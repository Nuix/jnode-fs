package org.jnode.fs.xfs;

import com.google.common.base.Splitter;
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
            case 3:
                return BigEndian.getUInt24(buffer.array(), 0);
            case 4:
                return BigEndian.getUInt32(buffer.array(), 0);
            case 6:
                return BigEndian.getUInt48(buffer.array(),0);
            case 8:
                return BigEndian.getInt64(buffer.array(), 0);
        }
        throw new RuntimeException("Invalid read size " + size);
    }

    protected String readAsHexString(long offset, int size) throws IOException {
        final long data = read(offset, size);
        return Long.toHexString(data);
    }

    protected String readUuid(long offset, int size) throws IOException {
        return readAsHexString(offset,4)
                + "-" + readAsHexString(offset+4,2)
                + "-" + readAsHexString(offset+6,2)
                + "-" + readAsHexString(offset+8,2)
                + "-" + readAsHexString(offset+10,6);
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

    public String getAsciiSignature() throws IOException {
        return HexToAscii(Long.toHexString(getSignature()));
    }

    abstract public boolean isValidSignature() throws IOException;

    public static long AsciiToHex(String asciiString) {
        StringBuilder hex = new StringBuilder();
        for (char c : asciiString.toCharArray()) {
            hex.append(Integer.toHexString(c).toUpperCase(Locale.ROOT));
        }
        return Long.parseLong(hex.toString(),16);
    }

    public static String HexToAscii(String hexString) {
        try {
            StringBuilder ascii = new StringBuilder();
            final Iterable<String> chars = Splitter.fixedLength(2).split(hexString);
            for (String c : chars) {
                ascii.append((char) Byte.parseByte(c, 16));
            }
            return ascii.toString();
        }catch (Throwable t){
            return "INVALID";
        }
    }
}
