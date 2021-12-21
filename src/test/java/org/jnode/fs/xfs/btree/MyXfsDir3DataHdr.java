package org.jnode.fs.xfs.btree;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.MyBlockDirectory;
import org.jnode.fs.xfs.MyBlockDirectoryEntry;
import org.jnode.fs.xfs.MyXfsFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MyXfsDir3DataHdr {

    MyXfsDir3BlkHdr dir3BlkHdr;
    List<MyXfsDir2DataFree> bestFree;
    FSBlockDeviceAPI devApi;
    MyXfsFileSystem fs;
    long blockStart;

    public MyXfsDir3DataHdr(FSBlockDeviceAPI devApi, long blockStart, MyXfsFileSystem fs) {
        this.blockStart = blockStart;
        this.devApi = devApi;
        this.fs = fs;
        dir3BlkHdr = new MyXfsDir3BlkHdr(devApi,blockStart,fs);
        bestFree = new ArrayList<>(MyXfsDir2DataFree.XFS_DIR2_DATA_FD_COUNT);
        long dataStart = blockStart + 48;
        for (int i = 0; i < MyXfsDir2DataFree.XFS_DIR2_DATA_FD_COUNT; i++) {
            bestFree.add(new MyXfsDir2DataFree(devApi,dataStart,fs));
            dataStart += 4; // MyXfsDir2DataFree length
        }
    }

    public MyXfsDir3BlkHdr getDir3BlkHdr() {
        return dir3BlkHdr;
    }

    public List<MyXfsDir2DataFree> getBestFree() {
        return bestFree;
    }

    private long getOffset(){
        return 48 // dir3BlkHdr
                + (4 * MyXfsDir2DataFree.XFS_DIR2_DATA_FD_COUNT) // dir2DataFree
                + 4; // Padding to maintain a 64-bit alignment.
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(200);
        sb.append(dir3BlkHdr.toString()).append("\n");
        for (int i = 0; i < MyXfsDir2DataFree.XFS_DIR2_DATA_FD_COUNT; i++) {
            sb.append("    {").append(bestFree.get(i)).append("}\n");
        }
        return sb.toString();
    }

    public List<MyBlockDirectoryEntry> getEntries() throws IOException {
        long accumulatorOffset = getOffset();
        List<MyBlockDirectoryEntry> data = new ArrayList<>(50);
        while (true) {
            final MyBlockDirectoryEntry entry = new MyBlockDirectoryEntry(devApi, blockStart + accumulatorOffset,fs);
            final String name = entry.getName();
            if (!entry.isFreeTag()) {
                if (entry.getNameSize() == 0) break;
                data.add(entry);
            }
            accumulatorOffset += entry.getOffsetSize();
        }


        return data;
    }
}
