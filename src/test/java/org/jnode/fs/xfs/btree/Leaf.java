package org.jnode.fs.xfs.btree;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.MyXfsFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Leaf {
    private final LeafInfo leafInfo;
    private final List<LeafEntry> leafEntries;
    private final long bestcount;
    public Leaf(FSBlockDeviceAPI devApi, long superBlockStart, MyXfsFileSystem fs,int extentCount) throws IOException {
        leafInfo = new LeafInfo(devApi,superBlockStart,fs);
        final int infoCount = (int) leafInfo.getCount();
        bestcount = extentCount;
        leafEntries = new ArrayList<>(infoCount);
        long leafEntryOffset = superBlockStart + 64;
        for (int i=0;i<infoCount;i++){
            final LeafEntry entry = new LeafEntry(devApi, leafEntryOffset, fs);
            leafEntries.add(entry);
            leafEntryOffset += 8; // Add LeafEntry Size
        }
    }

    public LeafInfo getLeafInfo() {
        return leafInfo;
    }

    public List<LeafEntry> getLeafEntries() {
        return leafEntries;
    }

    public long getBestcount() {
        return bestcount;
    }
}
