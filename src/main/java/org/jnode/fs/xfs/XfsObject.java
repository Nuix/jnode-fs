package org.jnode.fs.xfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Locale;

import com.google.common.base.Splitter;
import org.jnode.util.BigEndian;

/**
 * An object in a XFS file system.
 */
public abstract class XfsObject {

    /**
     * The UTF-8 charset.
     */
    public static Charset UTF8 = Charset.forName("UTF-8");

    /**
     * The data for this record.
     */
    private byte[] data;

    /**
     * The offset into the data.
     */
    private int offset;

    /**
     * Creates a new object.
     */
    public XfsObject() {
    }

    /**
     * Creates a new object.
     *
     * @param data the data.
     * @param offset the offset into the data for this object.
     */
    public XfsObject(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
    }

    /**
     * Gets the data.
     *
     * @return the data.
     */
    public byte[] getData() {
        return data;
    }

    /**
     * Gets the offset for this object into the data.
     *
     * @return the offset.
     */
    public int getOffset() {
        return offset;
    }

    /**
     * Gets a uint-8.
     *
     * @param relativeOffset the offset to read from.
     * @return the value.
     */
    public int getUInt8(int relativeOffset) {
        return BigEndian.getUInt8(data, offset + relativeOffset);
    }

    /**
     * Gets a uint-16.
     *
     * @param relativeOffset the offset to read from.
     * @return the value.
     */
    public int getUInt16(int relativeOffset) {
        return BigEndian.getUInt16(data, offset + relativeOffset);
    }

    /**
     * Gets a uint-24.
     *
     * @param relativeOffset the offset to read from.
     * @return the value.
     */
    public int getUInt24(int relativeOffset) {
        return BigEndian.getUInt24(data, offset + relativeOffset);
    }

    /**
     * Gets a uint-32.
     *
     * @param relativeOffset the offset to read from.
     * @return the value.
     */
    public long getUInt32(int relativeOffset) {
        return BigEndian.getUInt32(data, offset + relativeOffset);
    }

    /**
     * Gets a uint-48.
     *
     * @param relativeOffset the offset to read from.
     * @return the value.
     */
    public long getUInt48(int relativeOffset) {
        return BigEndian.getUInt48(data, offset + relativeOffset);
    }

    /**
     * Gets an int-64.
     *
     * @param relativeOffset the offset to read from.
     * @return the value.
     */
    public long getInt64(int relativeOffset) {
        return BigEndian.getInt64(data, offset + relativeOffset);
    }

    public long read(long offset, int size) throws IOException {
        switch (size) {
            case 1:
                return getUInt8((int) offset);
            case 2:
                return getUInt16((int) offset);
            case 3:
                return getUInt24((int) offset);
            case 4:
                return getUInt32((int) offset);
            case 6:
                return getUInt48((int) offset);
            case 8:
                return getInt64((int) offset);
        }
        throw new RuntimeException("Invalid read size " + size);
    }

    protected String readAsHexString(long offset, int size) throws IOException {
        final long data = read(offset, size);
        return Long.toHexString(data);
    }

    protected String readUuid(long offset, int size) throws IOException {
                 return readAsHexString(offset, 4)
                + "-" + readAsHexString(offset + 4, 2)
                + "-" + readAsHexString(offset + 6, 2)
                + "-" + readAsHexString(offset + 8, 2)
                + "-" + readAsHexString(offset + 10, 6);
    }


    public static long asciiToHex(String asciiString) {
        StringBuilder hex = new StringBuilder();
        for (char c : asciiString.toCharArray()) {
            hex.append(Integer.toHexString(c).toUpperCase(Locale.ROOT));
        }
        return Long.parseLong(hex.toString(), 16);
    }

    public static String hexToAscii(String hexString) {
        try {
            StringBuilder ascii = new StringBuilder();
            final Iterable<String> chars = Splitter.fixedLength(2).split(hexString);
            for (String c : chars) {
                ascii.append((char) Byte.parseByte(c, 16));
            }
            return ascii.toString();
        } catch (Throwable t) {
            return "INVALID";
        }
    }

    abstract protected List<Long> validSignatures();

    public boolean isValidSignature() throws IOException{
        final long signature = getMagicSignature();
        return validSignatures().stream().anyMatch(s -> signature == s);
    }

    public long getMagicSignature() throws IOException {
        return read(0, 4);
    }

    public String getAsciiSignature() throws IOException {
        return hexToAscii(Long.toHexString(getMagicSignature()));
    }
}
