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

public class LeafDirectory extends XfsObject{

    private final List<DataExtent> extents;
    private final XfsFileSystem fileSystem;
    private final long iNodeNumber;

    private static final long BYTES_IN_32G = 34359738368L;

    public LeafDirectory(byte[] data, int offset, XfsFileSystem fileSystem, long iNodeNumber, List<DataExtent> extents) {
        super(data, offset);
        this.extents = extents;
        this.fileSystem = fileSystem;
        this.iNodeNumber = iNodeNumber;
    }

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

    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        final DataExtent leafExtent = extents.get(extents.size() - 1);
        final Leaf leaf = new Leaf(getData(), getOffset(), fileSystem, extents.size() - 1);
        List<FSEntry> entries = new ArrayList<>((int)leaf.getLeafInfo().getCount());
        final DataExtentOffsetManager extentOffsetManager = new DataExtentOffsetManager(extents.subList(0, extents.size() - 1), fileSystem);
        int i=0;
        for ( LeafEntry leafEntry : leaf.getLeafEntries() ) {
            final long address = leafEntry.getAddress();
            if ( address == 0 ) {
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
    protected List<Long> validSignatures() { return Arrays.asList(0L); }
}
