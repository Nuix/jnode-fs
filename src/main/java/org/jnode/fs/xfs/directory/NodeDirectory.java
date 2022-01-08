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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class NodeDirectory extends XfsObject {

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
     *  The extent index of the leaf.
     */
    private final int leafExtentIndex;

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
     * @param extents of the block directory
     * @param leafExtentIndex of the block directory
     */
    public NodeDirectory(byte[] data, long offset, XfsFileSystem fileSystem, long iNodeNumber, List<DataExtent> extents, long leafExtentIndex) {
        super(data,(int)offset);
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
        final List<DataExtent> leafExtents = extents.subList(leafExtentIndex+1, extents.size() - 1);
        final List<DataExtent> dataExtents = extents.subList(0, leafExtentIndex-1);
        final DataExtent freeExtent = extents.get(extents.size() - 1);
        final DataExtent directoryDataExtent = extents.get(leafExtentIndex);
        final long directoryBlockSizeLog2 = fs.getSuperblock().getDirectoryBlockSizeLog2();
        final long directoryBlockSize = (long) Math.pow(2, directoryBlockSizeLog2) * fs.getSuperblock().getBlockSize();
        final List<LeafEntry> leafEntries = leafExtentsToLeaves(leafExtents).stream().flatMap(leaf -> leaf.getLeafEntries().stream()).collect(Collectors.toList());
        final DataExtentOffsetManager extentOffsetManager = new DataExtentOffsetManager(dataExtents, fs);
        List<FSEntry> entries = new ArrayList<>(leafEntries.size());
        int i=0;
        for (LeafEntry leafEntry : leafEntries) {
            final long address = leafEntry.getAddress();
            if (address == 0) { continue; }
            final long extentGroupOffset = address * 8;
            final DataExtentOffsetManager.ExtentOffsetLimitData data = extentOffsetManager.getExtentDataForOffset(extentGroupOffset);
            if (data == null){
                System.out.println("# Error on Node Directory " + iNodeNumber + " No Relative address " + address + "(" +extentGroupOffset  + ") found");
                continue;
            }
            final long extOffset = data.getExtent().getExtentOffset(fs);
            ByteBuffer buffer = ByteBuffer.allocate(fs.getSuperblock().getBlockSize() * (int) data.getExtent().getBlockCount());
            try {
                fs.getFSApi().read(extOffset,buffer);
            } catch (ApiNotFoundException e) {
                e.printStackTrace();
            }
            final long extentRelativeOffset = extentGroupOffset - data.getStart();
            final BlockDirectoryEntry entry = new BlockDirectoryEntry(buffer.array(), extentRelativeOffset, fs);
            if (entry.isFreeTag()) { continue; }
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
    private List<Leaf> leafExtentsToLeaves(List<DataExtent> extent){
        return extent.stream().map(e -> {
            try {
                final long extOffset = e.getExtentOffset(fs);
                ByteBuffer buffer = ByteBuffer.allocate(fs.getSuperblock().getBlockSize() * (int) e.getBlockCount());
                try {
                    fs.getFSApi().read(extOffset,buffer);
                } catch (ApiNotFoundException exc) {
                    exc.printStackTrace();
                }
                return new Leaf(buffer.array(), 0, fs, extents.size() - 1);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).collect(Collectors.toList());
    }

    /**
     * Validate the magic key data
     *
     * @return a list of valid magic signatures
     */
    @Override
    protected List<Long> validSignatures() {
        return Arrays.asList(0L);
    }
}
