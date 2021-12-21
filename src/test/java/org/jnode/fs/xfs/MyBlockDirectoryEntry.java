package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class MyBlockDirectoryEntry extends MyXfsBaseAccessor implements IMyDirectory{

    private final long nameSize;
    private final boolean isFreeTag;
    private final long iNodeNumber;
    private final String name;

    public MyBlockDirectoryEntry(FSBlockDeviceAPI devApi, long superBlockStart,MyXfsFileSystem fs) throws IOException {
        super(devApi, superBlockStart,fs);
        isFreeTag = read(0,2) == 0xFFFF;
        if (!isFreeTag) {
            nameSize = read(8, 1);
            iNodeNumber = read(0, 8);
            final ByteBuffer buffer = ByteBuffer.allocate((int) nameSize);
            devApi.read(getOffset() + 9, buffer);
            name = new String(buffer.array(), StandardCharsets.US_ASCII);
        }else {
            nameSize = 0;
            iNodeNumber=0;
            name = "";
        }


    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    public long getINodeNumber() throws IOException {
        return iNodeNumber;
    }

    public long getNameSize() throws IOException {
        return nameSize;
    }

    public String getName() throws IOException {
        return name;
    }

    public long getOffsetFromBlock() throws IOException {
        return read(getNameSize() + 9, 2);
    }

    public boolean isFreeTag() {
        return isFreeTag;
    }

    public long getOffsetSize() throws IOException {
        if (!isFreeTag) {
            final long l = 12 + nameSize;
            final double v = l / 8.0;
            return (long) Math.ceil(v) * 8;
        } else {
            return read(2,2);
        }
    }

    @Override
    public String toString() {
        return "MyBlockDirectoryEntry{" +
                "name='" + name + '\'' +
                ", iNodeNumber=" + iNodeNumber +
                ", isFreeTag=" + isFreeTag +
                '}';
    }
}
