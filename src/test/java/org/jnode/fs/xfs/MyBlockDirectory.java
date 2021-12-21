package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class MyBlockDirectory  extends MyXfsBaseAccessor {
    private static final long MAGIC_V4 =AsciiToHex("XD2B");
    private static final long MAGIC = AsciiToHex("XDB3");

    public final static int V4_LENGTH = 16;
    public final static int V5_LENGTH = 64;

    public MyBlockDirectory(FSBlockDeviceAPI devApi, long superBlockStart,MyXfsFileSystem fs) {
        super(devApi, superBlockStart,fs);
    }

    @Override
    protected List<Long> validSignatures() {
        return Arrays.asList(MAGIC,MAGIC_V4);
    }

    public long getChecksum() throws IOException {
        return read(4,4);
    }

    public long getBlockNumber() throws IOException {
        return read(8,8);
    }

    public long getLogSequenceNumber() throws IOException {
        return read(16,8);
    }

    public String getUuid() throws IOException {
        return readUuid(24,16);
    }

    public long getParentInode() throws IOException {
        return read(40,8);
    }

    public List<MyBlockDirectoryEntry> getEntries() throws IOException {
        long offset = getOffset() + V5_LENGTH;
        List<MyBlockDirectoryEntry> data = new ArrayList<>(10);
        while (true) {
            final MyBlockDirectoryEntry entry = new MyBlockDirectoryEntry(devApi, offset,fs);
            final String name = entry.getName();
            if (entry.getNameSize() == 0) break;
            data.add(entry);
            offset += entry.getOffsetSize();
        }


        return data;
    }
}
