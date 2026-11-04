package org.jnode.fs.ntfs.attribute;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.jnode.fs.ntfs.FileRecord;

/**
 * A resident NTFS reparse point (symbolic link).
 *
 * @author Luke Quinane
 */
public class ReparsePointAttributeRes extends NTFSResidentAttribute implements ReparsePointAttribute {

    /**
     * Constructs the attribute.
     *
     * @param fileRecord the containing file record.
     * @param offset     offset of the attribute within the file record.
     */
    public ReparsePointAttributeRes(FileRecord fileRecord, int offset) {
        super(fileRecord, offset);
    }

    @Override
    public int getReparseTag() {
        checkHeader();
        return getInt32(getAttributeOffset());
    }

    @Override
    public int getReparseDataLength() {
        checkHeader();

        // 16-bit, followed by a 2 byte reserved field
        return getUInt16(getAttributeOffset() + 0x4);
    }

    @Override
    public byte[] getReparseData() throws IOException {
        return ReparsePointValue.getData(this, getAttributeOffset(), getValueLength());
    }

    private void checkHeader() {
        try {
            ReparsePointValue.checkHeader(this, getAttributeOffset(), getValueLength());
        } catch (IOException e) {
            throw new UncheckedIOException(e.getMessage() + ", file record = " + getFileRecord(), e);
        }
    }

    /**
     * Gets the length of the value, cut short where it would run past the end of the attribute.
     */
    private int getValueLength() {
        return Math.max(Math.min(getAttributeLength(), getSize() - getAttributeOffset()), 0);
    }
}
