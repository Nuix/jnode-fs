package org.jnode.fs.ntfs.attribute;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.jnode.fs.ntfs.FileRecord;

/**
 * A non-resident NTFS reparse point (symbolic link).
 *
 * @author Luke Quinane
 */
public class ReparsePointAttributeNonRes extends NTFSNonResidentAttribute implements ReparsePointAttribute {

    /**
     * The largest value the 16-bit data length allows, past which the attribute size cannot be right.
     */
    private static final int MAX_VALUE_LENGTH = REPARSE_HEADER_SIZE + 0xffff;

    /**
     * Whether the value has been read out of the attribute's clusters into this structure's buffer.
     */
    private boolean valueRead;

    /**
     * Why the value could not be read, if it could not.
     */
    private IOException readFailure;

    /**
     * Constructs the attribute.
     *
     * @param fileRecord the containing file record.
     * @param offset     offset of the attribute within the file record.
     */
    public ReparsePointAttributeNonRes(FileRecord fileRecord, int offset) {
        super(fileRecord, offset);
    }

    /**
     * Reads the value out of the attribute's clusters and points this structure at it, so the value's fields read
     * from offset zero. A failure is kept and raised again, as the structure would otherwise still point at the
     * attribute header and the reparse getters would read that as the value.
     */
    private synchronized void readValue() throws IOException {
        if (readFailure != null) {
            throw new IOException(readFailure.getMessage(), readFailure);
        }
        if (valueRead) {
            return;
        }

        try {
            long length = getFileRecord().getAttributeTotalSize(NTFSAttribute.Types.REPARSE_POINT, null);
            if (length < REPARSE_HEADER_SIZE || length > MAX_VALUE_LENGTH) {
                throw new IOException("Non-resident reparse point has an invalid size: " + length);
            }

            byte[] value = new byte[(int) length];
            getFileRecord().readData(NTFSAttribute.Types.REPARSE_POINT, null, 0, value, 0, value.length, true);
            reset(value, 0);
            valueRead = true;
        } catch (IOException | RuntimeException e) {
            readFailure = new IOException("Failed to read the non-resident reparse point of " + getFileRecord() +
                ": " + e.getMessage(), e);
            throw readFailure;
        }
    }

    private void readValueUnchecked() {
        try {
            readValue();
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage(), e);
        }
    }

    @Override
    public int getReparseTag() {
        readValueUnchecked();
        return getInt32(0);
    }

    @Override
    public int getReparseDataLength() {
        readValueUnchecked();

        // 16-bit, followed by a 2 byte reserved field
        return getUInt16(0x4);
    }

    @Override
    public byte[] getReparseData() throws IOException {
        readValue();
        return ReparsePointValue.getData(this, 0, getBuffer().length);
    }
}
