package org.jnode.fs.xfs.directory;

import org.jnode.driver.ApiNotFoundException;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsDirectory;
import org.jnode.fs.xfs.XfsEntry;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;
import org.jnode.util.BigEndian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Leaf directory.
 *
 * Leaf Directories
 * Once a Block Directory has filled the block, the directory data is changed into a new format called leaf.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class LeafDirectory extends XfsObject {

    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(LeafDirectory.class);

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
     * The leaf block offset (XFS_DIR2_LEAF_OFFSET).
     */
    private static final long BYTES_IN_32G = 34359738368L;

    /**
     * Creates a Leaf directory entry.
     *
     * @param data of the inode.
     * @param offset of the inode's data
     * @param fileSystem of the image
     * @param iNodeNumber of the inode
     * @param extents of the inode
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
        long leafOffset = BYTES_IN_32G / fs.getSuperblock().getBlockSize();
        int leafExtentIndex = -1;
        for (int i = 0; i < extents.size(); i++) {
            if (extents.get(i).getStartOffset() == leafOffset) {
                leafExtentIndex = i;
            }
        }
        return leafExtentIndex;
    }

    /**
     * Get the leaf block entries
     *
     * @return a list of inode entries
     */
    public List<FSEntry> getEntries(XfsDirectory parentDirectory) throws IOException {
        final Leaf leaf = new Leaf(getData(), getOffset(), fileSystem.isV5(), extents.size() - 1);
        final LeafInfo leafInfo = leaf.getLeafInfo();
        final int entryCount = leafInfo.getCount() - leafInfo.getStale();
        List<FSEntry> entries = new ArrayList<>(entryCount);
        for (DataExtent dataExtent : extents) {
            LeafDirectory.extractEntriesFromExtent(fileSystem,dataExtent,entries,parentDirectory);
        }
        return entries;
    }

    public static final long LEAF_DIR_DATA_MAGIC_V5 = 0x58444433;
    public static final long LEAF_DIR_DATA_MAGIC_V4 = 0x58443244;

    public static void extractEntriesFromExtent(XfsFileSystem fs,DataExtent extent,List<FSEntry> entries,FSDirectory parentDirectory) throws IOException {
        final int blockSize = (int) fs.getSuperblock().getBlockSize();
        final int blockCount = (int) extent.getBlockCount();
        final long dataExtentOffset = extent.getExtentOffset(fs);
        int x = 2;
        for (long i = 0; i < blockCount; i++) {
            ByteBuffer buffer = ByteBuffer.allocate(blockSize);
            try {
                fs.getFSApi().read(dataExtentOffset + (i * blockSize),buffer);
            } catch (ApiNotFoundException e) {
                e.printStackTrace();
            }
            final long extentSignature = BigEndian.getUInt32(buffer.array(), 0);
            if (extentSignature == LEAF_DIR_DATA_MAGIC_V5 || extentSignature == LEAF_DIR_DATA_MAGIC_V4) {
                int extentOffset = extentSignature == LEAF_DIR_DATA_MAGIC_V5 ? 64 : 16;
                while (extentOffset < blockSize) {
                    final BlockDirectoryEntry blockDirectoryEntry = new BlockDirectoryEntry(buffer.array(), extentOffset, fs.isV5());
                    if (!blockDirectoryEntry.isFreeTag()) {
                        final XfsEntry entry = new XfsEntry(fs.getINode(blockDirectoryEntry.getINodeNumber()), blockDirectoryEntry.getName(), x++, fs, parentDirectory);
                        entries.add(entry);
                    }
                    extentOffset += blockDirectoryEntry.getOffsetSize();
                }
            }
        }
    }

}
