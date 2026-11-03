package org.jnode.fs.ntfs.attribute;

import org.jnode.fs.ntfs.FileRecord;

/**
 * A resident NTFS reparse point (symbolic link).
 *
 * @author Luke Quinane
 */
public class ReparsePointAttributeRes extends NTFSResidentAttribute implements ReparsePointAttribute {

    /**
     * The tag, the data length and a reserved field.
     */
    private static final int REPARSE_HEADER_SIZE = 8;

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
        return getInt32(getAttributeOffset());
    }

    @Override
    public int getReparseDataLength() {
        // 16-bit, followed by a 2 byte reserved field
        return getUInt16(getAttributeOffset() + 0x4);
    }

    @Override
    public byte[] getReparseData() {
        int available = Math.max(getAttributeLength() - REPARSE_HEADER_SIZE, 0);
        byte[] data = new byte[Math.min(getReparseDataLength(), available)];
        getData(getAttributeOffset() + REPARSE_HEADER_SIZE, data, 0, data.length);
        return data;
    }
}
