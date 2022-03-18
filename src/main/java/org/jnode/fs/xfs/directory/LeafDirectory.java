package org.jnode.fs.xfs.directory;

import org.jnode.driver.ApiNotFoundException;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.*;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.util.BigEndian;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>Leaf directory.</p>
 *
 * <p>Once a Block Directory has filled the block, the directory data is changed into a new format called leaf.</p>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
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

    /**
     * Gets the extent index of the leaf.
     *
     * @return the index of the leaf block
     */
    public static long getLeafExtentIndex(List<DataExtent> extents, XfsFileSystem fs) {
        long leafOffset = XfsConstants.BYTES_IN_32G / fs.getSuperblock().getBlockSize();
        int leafExtentIndex = -1;
        for (int i = 0; i < extents.size(); i++) {
            if (extents.get(i).getStartOffset() == leafOffset) {
                leafExtentIndex = i;
            }
        }
        return leafExtentIndex;
    }

    public long getINodeNumber() {
        return iNodeNumber;
    }

    public static void extractEntriesFromExtent(XfsFileSystem fs, DataExtent extent, List<FSEntry> entries, FSDirectory parentDirectory) throws IOException {
        int blockSize = (int) fs.getSuperblock().getBlockSize();
        int blockCount = (int) extent.getBlockCount();
        long dataExtentOffset = extent.getExtentOffset(fs);
        int x = 2;
        for (long i = 0; i < blockCount; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(blockSize);
            try {
                fs.getFSApi().read(dataExtentOffset + (i * blockSize), buffer);
            } catch (ApiNotFoundException e) {
                e.printStackTrace();
            }
            long extentSignature = BigEndian.getUInt32(buffer.array(), 0);
            if (extentSignature == XFS_DIR3_DATA_MAGIC || extentSignature == XFS_DIR2_DATA_MAGIC) {
                int extentOffset = extentSignature == XFS_DIR3_DATA_MAGIC ? 64 : 16;
                while (extentOffset < blockSize) {
                    BlockDirectoryEntry blockDirectoryEntry = new BlockDirectoryEntry(buffer.array(), extentOffset, fs.isV5());
                    if (!blockDirectoryEntry.isFreeTag()) {
                        XfsEntry entry = new XfsEntry(fs.getINode(blockDirectoryEntry.getINodeNumber()), blockDirectoryEntry.getName(), x++, fs, parentDirectory);
                        entries.add(entry);
                    }
                    extentOffset += blockDirectoryEntry.getOffsetSize();
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
        Leaf leaf = new Leaf(getData(), getOffset(), fileSystem.isV5(), extents.size() - 1);
        LeafInfo leafInfo = leaf.getLeafInfo();
        int entryCount = leafInfo.getCount() - leafInfo.getStale();
        List<FSEntry> entries = new ArrayList<>(entryCount);
        for (DataExtent dataExtent : extents) {
            LeafDirectory.extractEntriesFromExtent(fileSystem, dataExtent, entries, parentDirectory);
        }
        return entries;
    }
}
