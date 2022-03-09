package org.jnode.fs.xfs.attribute;

import org.jnode.fs.xfs.XfsObject;

public class XfsAttributeLeafEntry extends XfsObject {

    public static final int PACKED_LENGTH = 8;

    public XfsAttributeLeafEntry(byte[] data, int offset) {
        super(data, offset);
    }

    public long getHashValue() {
        return getUInt32(0);
    }

    public int getBlockOffset() {
        return getUInt16(4);
    }
}
