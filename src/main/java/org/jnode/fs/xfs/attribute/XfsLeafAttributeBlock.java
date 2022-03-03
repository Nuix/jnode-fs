package org.jnode.fs.xfs.attribute;

import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class XfsLeafAttributeBlock extends XfsObject {

    public static final int MAGIC_V5 = 0x3BEE;
    public static final int MAGIC = 0xFBEE;
    public static final int BASE_ATTRIBUTE_LEAF_OFFSET = 0x20;
    public static final int BASE_ATTRIBUTE_LEAF_OFFSET_V5 = 0x50;
    private final boolean isV5;
    private final int baseOffset;

    public XfsLeafAttributeBlock(byte[] data, int offset, boolean v5) throws IOException {
        super(data, offset);

        final int signature = getUInt16(8);

        if (signature != MAGIC && signature != MAGIC_V5) {
            throw new IOException("Wrong magic number for XFS Leaf Attribute Block: " + getAsciiSignature(signature));
        }
        this.isV5 = v5;
        baseOffset = v5 ? BASE_ATTRIBUTE_LEAF_OFFSET_V5 : BASE_ATTRIBUTE_LEAF_OFFSET;
    }

    public int getEntryCount() {
        if (isV5) {
            return getUInt16(56);
        } else {
            return getUInt16(12);
        }
    }

    public List<XfsLeafAttribute> getAttributes() {
        final int entryCount = getEntryCount();
        List<XfsLeafAttribute> attributes = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            XfsAttributeLeafEntry leafEntry = new XfsAttributeLeafEntry(getData(), getOffset() + baseOffset + XfsAttributeLeafEntry.PACKED_LENGTH * i);
            int attributeBlockOffset = leafEntry.getBlockOffset() + getOffset();
            XfsLeafAttribute attribute = new XfsLeafAttribute(getData(), attributeBlockOffset);
            attributes.add(attribute);
        }
        return attributes;
    }
}
