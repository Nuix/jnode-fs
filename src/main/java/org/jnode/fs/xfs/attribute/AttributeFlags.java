package org.jnode.fs.xfs.attribute;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * Flags used in the leaf_entry[i].flags field
 *
 * @see <a href="https://github.com/torvalds/linux/blob/babf0bb978e3c9fce6c4eba6b744c8754fd43d8e/fs/xfs/libxfs/xfs_da_format.h#L688-L696">xfs_da_format.h</a>
 */
public enum AttributeFlags implements Flags {
    // if there is no flag, it means the attribute’s namespace is “user”.
//    USER(0)

    /**
     * The attribute value is contained within this block.
     * limit access to trusted attrs.
     */
    LOCAL(1),

    /**
     * The attribute’s namespace is “user”.
     * limit access to trusted attrs
     */
    ROOT(1 << 1),

    /**
     * The attribute’s namespace is “secure”.
     * limit access to secure attrs
     */
    SECURE(1 << 2),

    /**
     * This attribute is being modified.
     * limit access to trusted attrs.
     */
    INCOMPLETE(1 << 7);

    private final FlagUtil flagUtil;

    AttributeFlags(int flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<AttributeFlags> fromValue(long value) {
        return FlagUtil.fromValue(values(), value);
    }
}
