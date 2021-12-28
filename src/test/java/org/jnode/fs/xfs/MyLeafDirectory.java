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
        final long directoryBlockSizeLog2 = fs.getMainSuperBlock().getDirectoryBlockSizeLog2();
        final long directoryBlockSize = (long) Math.pow(2, directoryBlockSizeLog2) * fs.getBlockSize();
        final Leaf leaf = new Leaf(devApi, leafExtent.getExtentOffset(), fs, extents.size() - 1);
        List<MyBlockDirectoryEntry> entries = new ArrayList<>((int)leaf.getLeafInfo().getCount());
        List<MyXfsDir3DataHdr> leafDirectories = new ArrayList<>(extents.size()-1);
        for (int i = 0,l = extents.size()-1; i <l; i++) {
            final MyExtentInformation leafDirExtent = extents.get(i);
            final MyXfsDir3DataHdr leafDir = new MyXfsDir3DataHdr(devApi, leafDirExtent.getExtentOffset(), fs);
            leafDirectories.add(leafDir);
        }

        for (LeafEntry leafEntry : leaf.getLeafEntries()) {
            final long address = leafEntry.getAddress();
            if (address == 0) { continue; }
            final long relativeOffset = address * 8;
            final long blockNum = Math.floorDiv(relativeOffset, directoryBlockSize);
            if (blockNum >= leafDirectories.size()){
                // TODO: Check logic for extents larger than 1 block
                System.out.println("edge case found getting directories for inode " + iNodeNumber + " unavailable block number " + blockNum);
                continue;
            }
            final long blockOffset = leafDirectories.get((int) blockNum).getDir3BlkHdr().getOffset();
            final long blockRelativeOffset = relativeOffset - (blockNum * directoryBlockSize);
            final MyBlockDirectoryEntry entry = new MyBlockDirectoryEntry(devApi, blockOffset + blockRelativeOffset, fs);
            if (entry.isFreeTag()) { continue; }
            entries.add(entry);
        }

        return entries;
    }

}
