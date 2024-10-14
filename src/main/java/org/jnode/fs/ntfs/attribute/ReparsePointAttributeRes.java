package org.jnode.fs.ntfs.attribute;

import org.jetbrains.annotations.TestOnly;
import org.jnode.fs.ntfs.FileRecord;
import org.jnode.fs.ntfs.NTFSStructure;

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

    @TestOnly
    public ReparsePointAttributeRes(NTFSStructure ntfsStructure, int offset) {
        super(ntfsStructure, offset);
    }

    @Override
    public int getReparseTag() {
        return getInt32(getAttributeOffset());
    }

    @Override
    public int getReparseDataLength() {
        return getUInt32AsInt(getAttributeOffset() + 0x4);
    }
}
