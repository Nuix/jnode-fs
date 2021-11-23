package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.util.BigEndian;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;

public abstract class MyXfsBaseAccessor {

    private final FSBlockDeviceAPI devApi;
    private final long offset;

    public MyXfsBaseAccessor(FSBlockDeviceAPI devApi, long superBlockStart) {
        this.devApi = devApi;
        this.offset = superBlockStart;
    }


    protected long read(long offset, int size) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(size);
        devApi.read(this.offset + offset, buffer);
        switch (size) {
            case 1:
                return BigEndian.getUInt8(buffer.array(), 0);
            case 2:
                return BigEndian.getUInt16(buffer.array(), 0);
            case 4:
                return BigEndian.getUInt32(buffer.array(), 0);
            case 8:
                return BigEndian.getInt64(buffer.array(), 0);
        }
        throw new RuntimeException("Invalid read size " + size);
    }

    public FSBlockDeviceAPI getDevApi() {
        return devApi;
    }

    public long getOffset() {
        return offset;
    }

    public long getSignature() throws IOException {
        return read(offset, 4);
    }

    abstract public boolean isValidSignature() throws IOException;

    public static long AsciiToHex(String asciiString) {
        StringBuilder hex = new StringBuilder();
        for (char c : asciiString.toCharArray()) {
            hex.append(Integer.toHexString(c).toUpperCase(Locale.ROOT));
        }
        return Long.parseLong(hex.toString(),16);
    }
}
