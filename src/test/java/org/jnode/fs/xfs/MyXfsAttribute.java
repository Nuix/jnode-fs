package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class MyXfsAttribute extends MyXfsBaseAccessor {

    private final int flags;
    private final int namelen;
    private final int valuelen;
    private final String name;
    private final String val;

    public MyXfsAttribute(FSBlockDeviceAPI devApi, long superBlockStart, MyXfsFileSystem fs) throws IOException {
        super(devApi, superBlockStart, fs);
        namelen = (int) read(0, 1);
        valuelen = (int) read(1, 1);
        flags = (int) read(2, 1);

        ByteBuffer buffer = ByteBuffer.allocate(namelen + valuelen);
        devApi.read(superBlockStart + 3, buffer);
        final String nameval = new String(buffer.array(), StandardCharsets.US_ASCII);
        name = nameval.substring(0, namelen);
        val = nameval.substring(namelen);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    public int getAttributeSizeForOffset(){
        return namelen + valuelen + 3;
    }

    public int getFlags() {
        return flags;
    }

    public int getNamelen() {
        return namelen;
    }

    public int getValuelen() {
        return valuelen;
    }

    public String getName() {
        return name;
    }

    public String getVal() {
        return val;
    }
}
