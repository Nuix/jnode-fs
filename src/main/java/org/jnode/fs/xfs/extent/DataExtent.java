package org.jnode.fs.xfs.extent;

import lombok.Getter;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.superblock.Superblock;

/**
 * A data extent ('xfs_bmbt_rec_t' packed, or 'xfs_bmbt_irec_t' unpacked).
 * It is the packed format in this class, and the unpacked version is below.
 * <pre>
 *   struct xfs_bmbt_irec {
 *     xfs_fileoff_t br_startoff;
 *     xfs_fsblock_t br_startblock;
 *     xfs_filblks_t br_blockcount;
 *     xfs_exntst_t br_state;
 *   };
 * </pre>
 * @author Luke Quinane
 */
@Getter
public class DataExtent extends XfsObject {

    /**
     * The length of the structure when packed for storage on disk.
     */
    public static final int PACKED_LENGTH = 0x10;

    /**
     * The start offset.
     */
    private final long startOffset;

    /**
     * The start block.
     */
    private final long startBlock;

    /**
     * The block count.
     */
    private final int blockCount;

    /**
     * Specify if the extent has been preallocated but has
     * not yet been written (unwritten extent).
     */
    private final boolean initialised;

    /**
     * The state.
     */
    private int state;

    /**
     * Creates a new extent from the packed on disk format.
     * <p>
     * See XFS Algorithms & Data Structures
     * Chapter 16 - page 115
     * An extent is 128 bits in size and uses the following packed layout:
     * -----------------------------------------------------------------------------------------------------------------------------------
     * |1|1     1         1         1                                                                                                    |
     * |2|2     2         1         0         9         8         7         6         5         4         3         2         1          |
     * |7|6543210987654321098765432109876543210987654321098765432109876543210987654321098765432109876543210987654321098765432109876543210|
     * ----------------------------------------------------------------------------------------------------------------------------------|
     * |f|      bits 73 to 126 (54)                             |     bit 21 to 72 (52)                            |   bit  0-20 (21)    |
     * |l|      logical file block offset                       |     absolute block number                        |   # blocks          |
     * |a|                                                      |                                                  |                     |
     * |g|                                                      |                                                  |                     |
     * -----------------------------------------------------------------------------------------------------------------------------------
     *
     * @param data   the data to read from.
     * @param offset the offset to read from.
     */
    public DataExtent(byte[] data, int offset) {
        super(data, offset);

        long valueUpper128bit = getInt64(0);
        long valueLower128bit = getInt64(8);
        blockCount = (int) (valueLower128bit & 0x1f_ff_ffL);
        valueLower128bit = valueLower128bit >>> 21;
        startBlock = valueLower128bit | ((valueUpper128bit & 0x1ffL) << 43);
        valueUpper128bit = valueUpper128bit >>> 9;
        startOffset = valueUpper128bit & 0x3f_ff_ff_ff_ff_ff_ffL;
        initialised = ((valueUpper128bit >>> 54) == 0x1L);
    }

    /**
     * Gets the FileSystem block offset.
     *
     * @param block      the file offset to check.
     * @param fileSystem the file system block size.
     * @return the fileSystem block offset.
     */
    public static long getFileSystemBlockOffset(long block, XfsFileSystem fileSystem) {
        Superblock sb = fileSystem.getSuperblock();
        long agSizeLog2 = sb.getAgSizeLog2();
        long allocationGroupIndex = block >> agSizeLog2;
        long relativeBlockNumber = block & (((long) 1 << agSizeLog2) - 1);
        long allocationGroupBlockNumber = allocationGroupIndex * sb.getAgBlockSize();
        return (allocationGroupBlockNumber + relativeBlockNumber) * sb.getBlockSize();
    }

    /**
     * Checks if the given file offset is within the range covered by this extent.
     *
     * @param fileOffset the file offset to check.
     * @param blockSize  the file system block size.
     * @return {@code true} if this extent covers the file offset.
     */
    public boolean isWithinExtent(long fileOffset, long blockSize) {
        return
                fileOffset >= startOffset * blockSize &&
                        fileOffset < (startOffset + blockCount) * blockSize;
    }

    /**
     * Gets the extent offset.
     *
     * @param fileSystem the file offset to check.
     * @return the extent offset.
     */
    public long getExtentOffset(XfsFileSystem fileSystem) {
        Superblock sb = fileSystem.getSuperblock();
        long agSizeLog2 = sb.getAgSizeLog2();
        long allocationGroupIndex = startBlock >> agSizeLog2;
        long relativeBlockNumber = startBlock & (((long) 1 << agSizeLog2) - 1);
        long allocationGroupBlockNumber = allocationGroupIndex * sb.getAgBlockSize();
        return (allocationGroupBlockNumber + relativeBlockNumber) * sb.getBlockSize();
    }

    public long getFileSystemBlockOffset(XfsFileSystem fileSystem) {
        return getFileSystemBlockOffset(startBlock, fileSystem);
    }

    @Override
    public String toString() {
        return String.format("extent:[start: 0x%x start-block:0x%x block-count:%d]",
                startOffset, startBlock, blockCount);
    }
}
