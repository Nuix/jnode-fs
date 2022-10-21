package org.jnode.fs.xfs.attribute;

import java.util.List;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

/**
 * <pre>
 *     typedef struct xfs_attr_leaf_entry {
 *         __be32 hashval;
 *         __be16 nameidx;
 *         __u8 flags;
 *         __u8 pad2;
 *     } xfs_attr_leaf_entry_t;
 * </pre>
 */
@Getter
public class XfsAttributeLeafEntry extends XfsObject {

    public static final int PACKED_LENGTH = 8;

    /**
     * Hash value of the attribute name.
     */
    private final long hashValue;

    /**
     * Block offset of the name entry, in bytes.
     */
    private final int nameIndex;

    /**
     * Attribute flags.
     */
    private final long flags;

    public XfsAttributeLeafEntry(byte[] data, int offset) {
        super(data, offset);

        hashValue = readUInt32();
        nameIndex = readUInt16();
        flags = readUInt8();

        // Pads the structure to 64-bit boundaries.
        skipBytes(1);
    }

    /**
     * Gets flags of the attribute.
     *
     * @return the flags.
     */
    public List<AttributeFlags> getAttributeFlags() {
        return AttributeFlags.fromValue(flags);
    }
}
