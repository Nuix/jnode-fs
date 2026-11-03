package org.jnode.fs.ntfs.attribute;

import java.io.IOException;
import org.jnode.fs.ntfs.NTFSStructure;

/**
 * Reads a reparse point value (tag, data length, reserved field, then the data) out of a structure, checking that
 * it lies within both the bytes the attribute holds and the buffer behind it.
 *
 * @author Luke Quinane
 */
final class ReparsePointValue {

    private ReparsePointValue() {
    }

    /**
     * Checks that the value header is present.
     *
     * @param structure   the structure holding the value.
     * @param valueOffset the offset of the value within the structure.
     * @param valueLength the number of bytes of value the attribute holds.
     * @throws IOException if the header does not fit.
     */
    static void checkHeader(NTFSStructure structure, int valueOffset, int valueLength) throws IOException {
        int available = available(structure, valueOffset, valueLength);
        if (available < ReparsePointAttribute.REPARSE_HEADER_SIZE) {
            throw new IOException("Reparse point value of " + available + " bytes at " + valueOffset +
                " is too short for its header");
        }
    }

    /**
     * Copies the data after the header: the declared data length, cut short to what the attribute holds.
     */
    static byte[] getData(NTFSStructure structure, int valueOffset, int valueLength) throws IOException {
        checkHeader(structure, valueOffset, valueLength);

        int available = available(structure, valueOffset, valueLength) - ReparsePointAttribute.REPARSE_HEADER_SIZE;
        int declared = structure.getUInt16(valueOffset + 4);
        byte[] data = new byte[Math.min(declared, available)];
        structure.getData(valueOffset + ReparsePointAttribute.REPARSE_HEADER_SIZE, data, 0, data.length);
        return data;
    }

    /**
     * Gets how many bytes of the value can be read: the length the attribute gives, cut short at the buffer's end.
     */
    private static int available(NTFSStructure structure, int valueOffset, int valueLength) throws IOException {
        long start = (long) structure.getOffset() + valueOffset;
        if (valueOffset < 0 || valueLength < 0 || start > structure.getBuffer().length) {
            throw new IOException("Reparse point value at " + valueOffset + " of " + valueLength +
                " bytes lies outside its buffer");
        }
        return (int) Math.min(valueLength, structure.getBuffer().length - start);
    }
}
