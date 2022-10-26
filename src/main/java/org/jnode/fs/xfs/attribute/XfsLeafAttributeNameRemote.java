package org.jnode.fs.xfs.attribute;

import java.util.Arrays;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.xfs.XfsObject;

/**
 * TODO, we don't support it for now.
 *  And we don't have sample data covering it..
 *
 * <p>
 * Refer document:
 * 18.2 Leaf Attributes
 * 18.5 Remote Attribute Values
 * </p>
 *
 * <pre>
 *     typedef struct xfs_attr_leaf_name_remote {
 *         __be32 valueblk;
 *         __be32 valuelen;
 *         __u8 namelen;
 *         __u8 name[1];
 *     } xfs_attr_leaf_name_remote_t;
 * </pre>
 */
public class XfsLeafAttributeNameRemote extends XfsObject implements FSAttribute {

    public XfsLeafAttributeNameRemote(byte[] data, int offset) {
        super(data, offset);
    }

    @Override
    public String getName() {
        return null;
    }

    @Override
    public byte[] getValue() {
        return null;
    }

    @Override
    public String toString() {
        return "{" + getName() + " : " + Arrays.toString(getValue()) + "}";
    }
}
