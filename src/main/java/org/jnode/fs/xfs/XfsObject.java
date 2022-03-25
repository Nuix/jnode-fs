package org.jnode.fs.xfs;

import org.jnode.util.BigEndian;

import java.util.Locale;

/**
 * An object in a XFS file system.
 *
 * @author Luke Quinane
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class XfsObject {

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
     * @param data   the data.
     * @param offset the offset into the data for this object.
     */
    public XfsObject(byte[] data, int offset) {
        this.data = data;
        this.offset = offset;
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
    private static String hexToAscii(String hexString) {
        StringBuilder asciiString = new StringBuilder();
        for (int i = 0; i < hexString.length(); i += 2) {
            String string = hexString.substring(i, i + 2);
            asciiString.append((char) Integer.parseInt(string, 16));
        }
        return asciiString.toString();
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
     * Gets signature as ascii.
     *
     * @param signature Xfs magic number
     * @return the ascii value.
     */
    public String getAsciiSignature(long signature) {
        final String hexString = Long.toHexString(signature);
        try {
            return hexToAscii(hexString);
        }catch (NumberFormatException e){
            return hexString;
        }
    }
}
