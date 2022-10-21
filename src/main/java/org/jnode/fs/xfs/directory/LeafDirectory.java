package org.jnode.fs.xfs.directory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jnode.driver.ApiNotFoundException;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsDirectory;
import org.jnode.fs.xfs.XfsEntry;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.util.BigEndian;

/**
 * <p>Leaf directory.</p>
 *
 * <p>Once a Block Directory has filled the block, the directory data is changed into a new format called leaf.</p>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
@Slf4j
public class LeafDirectory extends XfsObject {

    /**
     * The magic number "XD2D".
     */
    private static final long XFS_DIR2_DATA_MAGIC = asciiToHex("XD2D");

    /**
     * The magic number "XDD3".
     */
    private static final long XFS_DIR3_DATA_MAGIC = asciiToHex("XDD3");

    /**
     * The list of extents of this block directory.
     */
    private final List<DataExtent> extents;

    /**
     * The filesystem.
     */
    private final XfsFileSystem fileSystem;

    /**
     * The number of the inode.
     */
    @Getter
    private final long iNodeNumber;

    /**
     * Creates a Leaf directory entry.
     *
     * @param data        of the inode.
     * @param offset      of the inode's data
     * @param fileSystem  of the image
     * @param iNodeNumber of the inode
     * @param extents     of the inode
     */
    public LeafDirectory(byte[] data, int offset, XfsFileSystem fileSystem, long iNodeNumber, List<DataExtent> extents) {
        super(data, offset);
        this.extents = extents;
        this.fileSystem = fileSystem;
        this.iNodeNumber = iNodeNumber;
    }

    public static void extractEntriesFromExtent(XfsFileSystem fs, DataExtent extent, List<FSEntry> entries, FSDirectory parentDirectory) throws IOException {
        int blockSize = (int) fs.getSuperblock().getBlockSize();
        int blockCount = extent.getBlockCount();
        long dataExtentOffset = extent.getExtentOffset(fs);
        int directoryRecordId = 2; // skip "." and "..".
        for (long i = 0; i < blockCount; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(blockSize);
            try {
                fs.getFSApi().read(dataExtentOffset + (i * blockSize), buffer);
            } catch (ApiNotFoundException e) {
                logger.error("Failed to read data extent from offset: {}", dataExtentOffset + i * blockSize, e);
            }
            long extentSignature = BigEndian.getUInt32(buffer.array(), 0);
            if (extentSignature == XFS_DIR3_DATA_MAGIC || extentSignature == XFS_DIR2_DATA_MAGIC) {
                int extentOffset = extentSignature == XFS_DIR3_DATA_MAGIC ? 64 : 16;
                try {
                    while (extentOffset < blockSize) {
                        BlockDirectoryEntry blockDirectoryEntry;
                        if (BlockDirectoryEntry.isFreeTag(buffer.array(), extentOffset)) {
                            blockDirectoryEntry = new BlockDirectoryDataUnusedEntry(buffer.array(), extentOffset);
                        } else {
                            BlockDirectoryDataEntry dataEntry = new BlockDirectoryDataEntry(buffer.array(), extentOffset, fs.isV5());
                            XfsEntry entry = new XfsEntry(fs.getINode(dataEntry.getINodeNumber()), dataEntry.getName(), directoryRecordId++, fs, parentDirectory);
                            entries.add(entry);
                            blockDirectoryEntry = dataEntry;
                        }
                        extentOffset += blockDirectoryEntry.getOffsetSize();
                    }
                } catch (Exception e) {
                    logger.error("Failed to get extent entries for directory record id {}", directoryRecordId, e);
                }
            }
        }
    }

    /**
     * Get the leaf block entries.
     *
     * @return a list of inode entries.
     */
    public List<FSEntry> getEntries(XfsDirectory parentDirectory) throws IOException {
        LeafHeader leafHeader = fileSystem.isV5() ?
                new LeafHeaderV3(getData(), getOffset()) : new LeafHeaderV2(getData(), getOffset());
        int entryCount = leafHeader.getCount() - leafHeader.getStale();
        List<FSEntry> entries = new ArrayList<>(entryCount);
        for (DataExtent dataExtent : extents) {
            LeafDirectory.extractEntriesFromExtent(fileSystem, dataExtent, entries, parentDirectory);
        }
        return entries;
    }
}
