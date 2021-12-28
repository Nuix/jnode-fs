package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.btree.Leaf;
import org.jnode.fs.xfs.btree.LeafEntry;
import org.jnode.fs.xfs.btree.MyXfsDir3DataHdr;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MyNodeDirectory {

    private final List<MyExtentInformation> extents;
    private final MyXfsFileSystem fs;
    private final long iNodeNumber;
    private final FSBlockDeviceAPI devApi;
    private final int leafExtentIndex;
    private static final long BYTES_IN_32G = 34359738368L;

    public MyNodeDirectory(FSBlockDeviceAPI devApi, MyXfsFileSystem fs, long iNodeNumber, List<MyExtentInformation> extents, long leafExtentIndex) {
        this.extents = extents;
        this.fs = fs;
        this.iNodeNumber = iNodeNumber;
        this.devApi = devApi;
        this.leafExtentIndex = (int) leafExtentIndex;
    }

    public List<? extends IMyDirectory> getEntries() throws IOException {
        final List<MyExtentInformation> leafExtents = extents.subList(leafExtentIndex+1, extents.size() - 1);
        final List<MyExtentInformation> dataExtents = extents.subList(0, leafExtentIndex-1);
        final MyExtentInformation freeExtent = extents.get(extents.size() - 1);
        final MyExtentInformation directoryDataExtent = extents.get(leafExtentIndex);
        final long directoryBlockSizeLog2 = fs.getMainSuperBlock().getDirectoryBlockSizeLog2();
        final long directoryBlockSize = (long) Math.pow(2, directoryBlockSizeLog2) * fs.getBlockSize();
        final List<LeafEntry> leafEntries = leafExtentsToLeafs(leafExtents).stream().flatMap(leaf -> leaf.getLeafEntries().stream()).collect(Collectors.toList());
        List<MyBlockDirectoryEntry> entries = new ArrayList<>(leafEntries.size());
        final MyExtentOffsetManager extentOffsetManager = new MyExtentOffsetManager(dataExtents, fs);
        for (LeafEntry leafEntry : leafEntries) {

        }


        return entries;
    }

    private List<Leaf> leafExtentsToLeafs(List<MyExtentInformation> extent){
        return extent.stream().map(e -> {
            try {
                return new Leaf(devApi, e.getExtentOffset(), fs, extents.size() - 1);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }).collect(Collectors.toList());
    }

}
