package org.jnode.fs.xfs.directory;

import org.jnode.driver.ApiNotFoundException;

import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsEntry;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.extent.DataExtentOffsetManager;
import org.jnode.fs.xfs.inode.INode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A XFS Node Directory.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class NodeDirectory extends XfsObject {

    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(NodeDirectory.class);

    /**
     * The list of extents of this block directory.
     */
    private final List<DataExtent> extents;

    /**
     * The filesystem.
     */
    private final XfsFileSystem fs;

    /**
     * The number of the inode.
     */
    private final long iNodeNumber;

    /**
     * The extent index of the leaf.
     */
    private final int leafExtentIndex;

    /**
     * The leaf block offset (XFS_DIR2_LEAF_OFFSET).
     */
    private static final long BYTES_IN_32G = 34359738368L;

    /**
     * Creates a Leaf directory entry.
     *
     * @param data            of the inode.
     * @param offset          of the inode's data
     * @param fileSystem      of the image
     * @param iNodeNumber     of the inode
     * @param extents         of the block directory
     * @param leafExtentIndex of the block directory
     */
    public NodeDirectory(byte[] data, long offset, XfsFileSystem fileSystem, long iNodeNumber, List<DataExtent> extents, long leafExtentIndex) {
        super(data, (int) offset);
        this.extents = extents;
        this.fs = fileSystem;
        this.iNodeNumber = iNodeNumber;
        this.leafExtentIndex = (int) leafExtentIndex;
    }

    /**
     * Get the node block entries
     *
     * @return a list of inode entries
     */
    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        final List<DataExtent> leafExtents = extents.subList(leafExtentIndex + 1, extents.size() - 1);
        final List<DataExtent> dataExtents = extents.subList(0, leafExtentIndex);
        final DataExtent freeExtent = extents.get(extents.size() - 1);
        final DataExtent directoryDataExtent = extents.get(leafExtentIndex);
        final long directoryBlockSizeLog2 = fs.getSuperblock().getDirectoryBlockSizeLog2();
        final long directoryBlockSize = (long) Math.pow(2, directoryBlockSizeLog2) * fs.getSuperblock().getBlockSize();
        final List<Leaf> leaves = leafExtentsToLeaves(leafExtents);
        final List<LeafEntry> leafEntries = new ArrayList<>();
        int dirCount = 0;
        for (Leaf leaf : leaves) {
            dirCount += (leaf.getLeafInfo().getCount() - leaf.getLeafInfo().getStale());
            leafEntries.addAll(leaf.getLeafEntries());
        }
        final DataExtentOffsetManager extentOffsetManager = new DataExtentOffsetManager(dataExtents, fs);
        List<FSEntry> entries = new ArrayList<>(dirCount);
        int i = 0;
        Map<DataExtent,ByteBuffer> bufferMap = new HashMap<>();
        for (LeafEntry leafEntry : leafEntries) {
            final long address = leafEntry.getAddress();
            if (address == 0) {
                continue;
            }
            final long extentGroupOffset = address * 8;
            final DataExtentOffsetManager.ExtentOffsetLimitData data = extentOffsetManager.getExtentDataForOffset(extentGroupOffset);
            if (data == null) {
                log.warn("Error on Node Directory " + iNodeNumber + " No Relative address " + address + "(" + extentGroupOffset + ") found");
                continue;
            }
            ByteBuffer buffer;
            if (!bufferMap.containsKey(data.getExtent())){
                final long extOffset = data.getExtent().getExtentOffset(fs);
                buffer = ByteBuffer.allocate((int) fs.getSuperblock().getBlockSize() * (int) data.getExtent().getBlockCount());
                try {
                    fs.getFSApi().read(extOffset, buffer);
                } catch (ApiNotFoundException e) {
                    throw new IOException("Error reading entry data at offset:" + extOffset, e);
                }
                bufferMap.put(data.getExtent(),buffer);
            } else {
                buffer = bufferMap.get(data.getExtent());
            }
            final long extentRelativeOffset = extentGroupOffset - data.getStart();
            final BlockDirectoryEntry entry = new BlockDirectoryEntry(buffer.array(), extentRelativeOffset, fs);
            if (entry.isFreeTag()) {
                continue;
            }
            INode inode = fs.getINode(entry.getINodeNumber());
            entries.add(new XfsEntry(inode, entry.getName(), i++, fs, parentDirectory));
        }
        return entries;
    }

    /**
     * Gets the list of leaves of the node directory.
     *
     * @return a list of leases
     */
    private List<Leaf> leafExtentsToLeaves(List<DataExtent> extent) throws IOException {

        List<Leaf> entries = new ArrayList<>(extent.size());
        for (DataExtent dataExtent : extent) {
            final long extOffset = dataExtent.getExtentOffset(fs);
            ByteBuffer buffer = ByteBuffer.allocate((int) fs.getSuperblock().getBlockSize() * (int) dataExtent.getBlockCount());
            try {
                fs.getFSApi().read(extOffset, buffer);
            } catch (ApiNotFoundException exc) {
                throw new IOException("Error reading leaf extent data at offset:" + extOffset, exc);
            }
            entries.add(new Leaf(buffer.array(), 0, fs, extents.size() - 1));
        }
        return entries;
    }
}


