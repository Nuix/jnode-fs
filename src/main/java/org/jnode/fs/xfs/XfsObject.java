package org.jnode.fs.xfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.google.common.base.Splitter;
import org.jnode.util.BigEndian;

/**
 * An object in a XFS file system.
 */
public class XfsObject {

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

    /**
     * Gets the UUID value.
     *
     * @param offset the offset to read from.
     * @return the uuid value.
     */
    protected String readUuid(int offset) {
                 return Long.toHexString(getUInt32(offset))
                + "-" + Long.toHexString(getUInt16(offset + 4))
                + "-" + Long.toHexString(getUInt16(offset + 6))
                + "-" + Long.toHexString(getUInt16(offset + 8))
                + "-" + Long.toHexString(getUInt16(offset + 10));
    }

    /**
     * Converts ascii to hex.
     *
     * @param asciiString value to convert to hex.
     * @return the hex value.
     */
    public static long asciiToHex(String asciiString) {
        StringBuilder hex = new StringBuilder();
        for (char c : asciiString.toCharArray()) {
            hex.append(Integer.toHexString(c).toUpperCase(Locale.ROOT));
        }
        return Long.parseLong(hex.toString(), 16);
    }

    /**
     * Converts hex to ascii.
     *
     * @param hexString value to convert to ascii.
     * @return the ascii value.
     */
    public static String hexToAscii(String hexString) {
        try {
            StringBuilder ascii = new StringBuilder();
            final Iterable<String> chars = Splitter.fixedLength(2).split(hexString);
            for (String c : chars) {
                ascii.append((char) Byte.parseByte(c, 16));
            }
            return ascii.toString();
        } catch (Exception e) {
            return "Invalid";
        }
    }

    /**
     * Gets magic signature.
     *
     * @return the hex value.
     */
    public long getMagicSignature()  {
        return getUInt32(0);
    }

    /**
     * Gets signature as ascii.
     *
     * @return the ascii value.
     */
    public String getAsciiSignature() {
        return hexToAscii(Long.toHexString(getMagicSignature()));
    }
}
