package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class MyBlockDirectoryEntry extends MyXfsBaseAccessor implements IMyDirectory{

    private final long nameSize;

    public MyBlockDirectoryEntry(FSBlockDeviceAPI devApi, long superBlockStart,MyXfsFileSystem fs) throws IOException {
        super(devApi, superBlockStart,fs);
        nameSize = read(8,1);
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
        return read(0,8);
    }

    public long getNameSize() throws IOException {
        return nameSize;
    }

    public String getName() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate((int) getNameSize());
        devApi.read(getOffset() + 9,buffer);
        return new String(buffer.array(), StandardCharsets.US_ASCII);
    }

    public long getOffsetFromBlock() throws IOException {
        return read(getNameSize() + 9, 2);
    }


    public long getOffsetSize(){
        final long l = 12 + nameSize;
        final double v = l / 8.0;
        return (long) Math.ceil(v) * 8;
//        return/* Name Size and INodeNumber AND TAG */ 15 + nameSize;
    }
}
