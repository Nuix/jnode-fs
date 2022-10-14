package org.jnode.fs.xfs.attribute;

import java.util.Arrays;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.util.FSUtils;
import org.jnode.fs.xfs.XfsObject;

public class XfsLeafAttribute extends XfsObject implements FSAttribute {

    public XfsLeafAttribute(byte[] data, int offset) {
        super(data, offset);
    }

    public int getValueLength() {
        return getUInt16(0);
    }

    public int getNameLength() {
        return getUInt8(2);
    }

    @Override
    public String getName() {
        return FSUtils.toNormalizedString(getData(), getOffset() + 3, getNameLength());
    }

    @Override
    public byte[] getValue() {
        byte[] bytes = new byte[getValueLength()];
        System.arraycopy(getData(), getNameLength() + getOffset() + 3, bytes, 0, getValueLength());
        return bytes;
    }

    @Override
    public String toString() {
        return "{" + getName() + " : " + Arrays.toString(getValue()) + "}";
    }
}
