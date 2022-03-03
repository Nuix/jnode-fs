package org.jnode.fs.xfs.attribute;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.xfs.XfsObject;

import java.util.ArrayList;
import java.util.List;

public class XfsShortFormAttributeReader extends XfsObject {
    private final XfsAttributeHeader header;

    public XfsShortFormAttributeReader(byte[] data, int offset) {
        super(data, offset);
        header = new XfsAttributeHeader(getData(), offset);
    }

    public int getCount() {
        return (int) header.getCount();
    }

    public List<FSAttribute> getAttributes() {
        int count = getCount();
        List<FSAttribute> attributes = new ArrayList<>(count);
        int off = getOffset() + XfsAttributeHeader.SIZE; // header size
        for (int i = 0; i < count; i++) {
            XfsAttribute attribute = new XfsAttribute(getData(), off);
            attributes.add(attribute);
            off += attribute.getAttributeSizeForOffset();
        }
        return attributes;
    }
}
