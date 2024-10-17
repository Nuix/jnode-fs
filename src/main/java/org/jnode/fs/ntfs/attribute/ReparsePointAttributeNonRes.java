package org.jnode.fs.ntfs.attribute;

import org.jnode.fs.ntfs.FileRecord;

/**
 * A non-resident NTFS reparse point (symbolic link).
 *
 * @author Luke Quinane
 */
public class ReparsePointAttributeNonRes extends NTFSNonResidentAttribute implements ReparsePointAttribute {

    /**
     * Constructs the attribute.
     *
     * @param fileRecord the containing file record.
     * @param offset     offset of the attribute within the file record.
     */
    public ReparsePointAttributeNonRes(FileRecord fileRecord, int offset) {
        super(fileRecord, offset);
    }

    @Override
    public int getReparseTag() {
        return getInt32(0);
    }

    @Override
    public int getReparseDataLength() {
        return getUInt32AsInt(0x4);
    }
}
