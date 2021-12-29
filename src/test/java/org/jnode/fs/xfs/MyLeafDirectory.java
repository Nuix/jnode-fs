package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.btree.Leaf;
import org.jnode.fs.xfs.btree.LeafEntry;
import org.jnode.fs.xfs.btree.MyXfsDir3DataHdr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MyLeafDirectory {

    private final List<MyExtentInformation> extents;
    private final MyXfsFileSystem fs;
    private final long iNodeNumber;
    private final FSBlockDeviceAPI devApi;
    private static final long BYTES_IN_32G = 34359738368L;

    public MyLeafDirectory(FSBlockDeviceAPI devApi, MyXfsFileSystem fs, long iNodeNumber, List<MyExtentInformation> extents) {
        this.extents = extents;
        this.fs = fs;
        this.iNodeNumber = iNodeNumber;
        this.devApi = devApi;
    }

    public static long getLeafExtentIndex(List<MyExtentInformation> extents, MyXfsFileSystem fs) throws IOException {
        long leafOffset = BYTES_IN_32G / fs.getBlockSize();
        int leafExtentIndex = -1;
        for (int i = 0; i < extents.size(); i++) {
            if (extents.get(i).getStartOffset() == leafOffset){
                leafExtentIndex = i;
            }
        }
        return leafExtentIndex;
    }

    public List<? extends IMyDirectory> getEntries() throws IOException {
        final MyExtentInformation leafExtent = extents.get(extents.size() - 1);
        final Leaf leaf = new Leaf(devApi, leafExtent.getExtentOffset(), fs, extents.size() - 1);
        List<MyBlockDirectoryEntry> entries = new ArrayList<>((int)leaf.getLeafInfo().getCount());
        final MyExtentOffsetManager extentOffsetManager = new MyExtentOffsetManager(extents.subList(0, extents.size() - 1), fs);
        for (LeafEntry leafEntry : leaf.getLeafEntries()) {
            final long address = leafEntry.getAddress();
            if (address == 0) { continue; }
            final long extentGroupOffset = address * 8;
            final MyExtentOffsetManager.ExtentOffsetLimitData data = extentOffsetManager.getExtentDataForOffset(extentGroupOffset);
            final long extentRelativeOffset = extentGroupOffset - data.getStart();
            final MyBlockDirectoryEntry entry = new MyBlockDirectoryEntry(devApi, data.getExtent().getExtentOffset() + extentRelativeOffset, fs);
            if (entry.isFreeTag()) { continue; }
            entries.add(entry);
        }

        return entries;
    }

}
