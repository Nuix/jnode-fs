package org.jnode.fs.xfs.directory;

import org.jnode.driver.ApiNotFoundException;

import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;

import org.jnode.fs.xfs.XfsEntry;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.extent.DataExtentOffsetManager;
import org.jnode.fs.xfs.inode.INode;

import java.io.IOException;

import java.nio.ByteBuffer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Leaf directory.
 *
 * Leaf Directories
 * Once a Block Directory has filled the block, the directory data is changed into a new format called leaf.
 *
 * @author
 */
public class LeafDirectory extends XfsObject {

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
    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        final Leaf leaf = new Leaf(getData(), getOffset(), fileSystem, extents.size() - 1);
        List<FSEntry> entries = new ArrayList<>((int)leaf.getLeafInfo().getCount());
        final DataExtentOffsetManager extentOffsetManager = new DataExtentOffsetManager(extents.subList(0, extents.size() - 1), fileSystem);
        int i=0;
        for (LeafEntry leafEntry : leaf.getLeafEntries()) {
            final long address = leafEntry.getAddress();
            if (address == 0) {
                continue;
            }
            final long extentGroupOffset = address * 8;
            final DataExtentOffsetManager.ExtentOffsetLimitData data = extentOffsetManager.getExtentDataForOffset(extentGroupOffset);
            final long extentRelativeOffset = extentGroupOffset - data.getStart();

            // Read the memory block with the required information.
            final long extOffset = data.getExtent().getExtentOffset(fileSystem);
            ByteBuffer buffer = ByteBuffer.allocate(fileSystem.getSuperblock().getBlockSize() * (int) data.getExtent().getBlockCount());
            try {
                fileSystem.getFSApi().read(extOffset,buffer);
            } catch (ApiNotFoundException e) {
                e.printStackTrace();
            }

            final BlockDirectoryEntry entry = new BlockDirectoryEntry(buffer.array(), extentRelativeOffset, fileSystem);
            if ( entry.isFreeTag() ) {
                continue;
            }
            INode inode = fileSystem.getINode(entry.getINodeNumber());
            entries.add(new XfsEntry(inode, entry.getName(), i++, fileSystem, parentDirectory));
        }
        return entries;
    }

    /**
     * Validate the magic key data
     *
     * @return a list of valid magic signatures
     */
    protected List<Long> validSignatures() {
        return Arrays.asList(0L);
    }
}
