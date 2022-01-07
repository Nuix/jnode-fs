package org.jnode.fs.xfs.extent;

import org.jnode.fs.xfs.Superblock;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * A data extent ('xfs_bmbt_rec_t' packed, or 'xfs_bmbt_irec_t' unpacked).
 *
 * @author Luke Quinane
 */
public class DataExtent extends XfsObject {

    /**
     * The length of the structure when packed for storage on disk.
     */
    public static final int PACKED_LENGTH = 0x10;

    /**
     * The start offset.
     */
    private long startOffset;

    /**
     * The start block.
     */
    private long startBlock;

    /**
     * The block count.
     */
    private int blockCount;

    /**
     * The state.
     */
    private int state;

    /**
     * Creates a new extent from the packed on disk format.
     *
     * @param data the data to read from.
     * @param offset the offset to read from.
     */
    public DataExtent(byte[] data, int offset) {
        super(data, offset);

        long valueUpper128bit = getInt64(0);
        long valueLower128bit = getInt64(8);
        blockCount = (int) (valueLower128bit & 0x1fffffL);
        valueLower128bit = valueLower128bit >>> 21;
        startBlock = valueLower128bit | (valueUpper128bit & 0x1ffL);
        valueUpper128bit = valueUpper128bit >>> 9;
        startOffset = valueUpper128bit & 0x3fffffffffffffL;
    }

    /**
     * Gets the start offset.
     *
     * @return the start offset.
     */
    public long getStartOffset() {
        return startOffset;
    }

    /**
     * Gets the start block for the extent.
     *
     * @return the start block.
     */
    public long getStartBlock() {
        return startBlock;
    }

    /**
     * Gets the block count.
     *
     * @return the block count.
     */
    public long getBlockCount() {
        return blockCount;
    }

    /**
     * Checks if the given file offset is within the range covered by this extent.
     *
     * @param fileOffset the file offset to check.
     * @param blockSize the file system block size.
     * @return {@code true} if this extent covers the file offset.
     */
    public boolean isWithinExtent(long fileOffset, long blockSize) {
        return
            fileOffset >= startOffset * blockSize &&
            fileOffset < (startOffset + blockCount) * blockSize;
    }

    public long getExtentOffset(XfsFileSystem fs) throws IOException {
        final Superblock sb = fs.getSuperblock();
        final long agSizeLog2 = sb.getAGSizeLog2();
        long allocationGroupIndex = startBlock >> agSizeLog2;
        long relativeBlockNumber  = startBlock & ( ( (long) 1 << agSizeLog2 ) - 1 );
        long allocationGroupBlockNumber = allocationGroupIndex * sb.getAGSize();
        return (allocationGroupBlockNumber + relativeBlockNumber) * sb.getBlockSize();
    }

    public static long getFileSystemBlockOffset(long block,XfsFileSystem fs) throws IOException {
        final Superblock sb = fs.getSuperblock();
        final long agSizeLog2 = sb.getAGSizeLog2();
        long allocationGroupIndex = block >> agSizeLog2;
        long relativeBlockNumber  = block & ( ( (long) 1 << agSizeLog2 ) - 1 );
        long allocationGroupBlockNumber = allocationGroupIndex * sb.getAGSize();
        return (allocationGroupBlockNumber + relativeBlockNumber) * sb.getBlockSize();
    }

    protected List<Long> validSignatures() { return Arrays.asList(0L); }

    @Override
    public String toString() {
        return String.format("extent:[start: 0x%x start-block:0x%x block-count:%d]",
            startOffset, startBlock, blockCount);
    }
}
