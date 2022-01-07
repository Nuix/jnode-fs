package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Leaf {

    private final LeafInfo leafInfo;
    private final List<LeafEntry> leafEntries;
    private final long bestCount;

    public Leaf(byte[] data, long offset, XfsFileSystem fs, int extentCount) throws IOException {
        leafInfo = new LeafInfo(data,offset,fs);
        final int infoCount = (int) leafInfo.getCount();
        bestCount = extentCount;
        leafEntries = new ArrayList<>(infoCount);
        long leafEntryOffset = offset + 64;
        for (int i=0;i<infoCount;i++) {
            final LeafEntry entry = new LeafEntry(data, leafEntryOffset, fs);
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

    public long getBestCount() {
        return bestCount;
    }
}
