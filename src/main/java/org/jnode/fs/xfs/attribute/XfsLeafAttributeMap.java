package org.jnode.fs.xfs.attribute;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

/**
 * <pre>
 *     typedef struct xfs_attr_leaf_map {
 *         __be16 base;
 *         __be16 size;
 *     } xfs_attr_leaf_map_t;
 * </pre>
 */
@Getter
public class XfsLeafAttributeMap extends XfsObject {

    /**
     * The size of this structure.
     */
    public static final int SIZE = 4;

    /**
     * Block offset of the free area, in bytes.
     */
    private final int base;

    /**
     * Size of the free area, in bytes.
     */
    private final int size;

    public XfsLeafAttributeMap(byte[] data, int offset) {
        super(data, offset);

        base = readUInt16();
        size = readUInt16();
    }
}
