package org.jnode.fs.xfs.attribute;

import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class XfsLeafAttributeBlock extends XfsObject {

    public static final int MAGIC = 0x3BEE;

    public XfsLeafAttributeBlock(byte[] data, int offset) throws IOException {
        super(data,offset);

        final int signature = getUInt16(8);

        if (signature != MAGIC) {
            throw new IOException("Wrong magic number for XFS Leaf Attribute Block: " + getAsciiSignature(signature));
        }
    }

    public int getEntryCount(){
        return getUInt16( 56);
    }

    public List<XfsLeafAttribute> getAttributes(){
        final int entryCount = getEntryCount();
        List<XfsLeafAttribute> attributes = new ArrayList<>(entryCount);
        for (int i=0;i<entryCount;i++){
            final XfsAttributeLeafEntry leafEntry = new XfsAttributeLeafEntry(getData(),getOffset() + 0x50 + XfsAttributeLeafEntry.PACKED_LENGTH * i);
            final int attributeBlockOffset = leafEntry.getBlockOffset() + getOffset();
            final XfsLeafAttribute attribute = new XfsLeafAttribute(getData(), attributeBlockOffset);
            attributes.add(attribute);
        }
        return attributes;
    }
}
