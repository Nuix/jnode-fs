package org.jnode.fs.xfs.attribute;

import java.util.Arrays;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.util.FSUtils;
import org.jnode.fs.xfs.XfsObject;

/**
 * <pre>
 *     typedef struct xfs_attr_leaf_name_local {
 *         __be16 valuelen;
 *         __u8 namelen;
 *         __u8 nameval[1];
 *     } xfs_attr_leaf_name_local_t;
 * </pre>
 */
public class XfsLeafAttributeNameLocal extends XfsObject implements FSAttribute {
    /**
     * Length of the value, in bytes.
     */
    private final int valueLength;

    /**
     * Length of the name, in bytes.
     */
    private final int nameLength;

    public XfsLeafAttributeNameLocal(byte[] data, int offset) {
        super(data, offset);
        valueLength = readUInt16();
        nameLength = readUInt8();
    }

    @Override
    public String getName() {
        return FSUtils.toNormalizedString(getData(), getOffset(), nameLength);
    }

    @Override
    public byte[] getValue() {
        byte[] bytes = new byte[valueLength];
        System.arraycopy(getData(), getOffset() + nameLength, bytes, 0, valueLength);
        return bytes;
    }

    @Override
    public String toString() {
        return "{" + getName() + " : " + Arrays.toString(getValue()) + "}";
    }
}
