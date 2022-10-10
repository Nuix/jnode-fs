package org.jnode.fs.xfs.superblock;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * Read-write incompatible feature flags.
 * Flags from the sb_features_incompat.
 *
 * @see <a href="https://github.com/torvalds/linux/blob/b2a88c212e652e94f1e4b635910972ac57ba4e97/fs/xfs/libxfs/xfs_format.h#L370-L376">xfs_format.h</a>
 */
public enum ReadWriteIncompatibilityFlags implements Flags {
    /**
     * Directory file type. Each directory entry tracks the
     * type of the inode to which the entry points. This is a
     * performance optimization to remove the need to
     * load every inode into memory to iterate a directory.
     */
    FTYPE(0x01),

    /**
     * Sparse inodes. This feature relaxes the requirement
     * to allocate inodes in chunks of 64. When the free
     * space is heavily fragmented, there might exist plenty
     * of free space but not enough contiguous free space to
     * allocate a new inode chunk. With this feature, the
     * user can continue to create files until all free space is
     * exhausted.
     * Unused space in the inode B+tree records are used to
     * track which parts of the inode chunk are not inodes.
     * See the chapter on Sparse Inodes for more
     * information.
     */
    SPINODES(0x02),

    /**
     * Metadata UUID. The UUID stamped into each
     * metadata block must match the value in
     * sb_meta_uuid. This enables the administrator to
     * change sb_uuid at will without having to rewrite
     * the entire filesystem.
     */
    META_UUID(0x04),

    /**
     * large timestamps (Only appears in the xfs_format.h, not in the document pdf.)
     */
    BIGTIME(0x08),

    /**
     * needs xfs_repair (Only appears in the xfs_format.h, not in the document pdf.)
     */
    NEEDSREPAIR(0x10),

    /**
     * large extent counter (Only appears in the xfs_format.h, not in the document pdf.)
     */
    NREXT64(0x20);

    private final FlagUtil flagUtil;

    ReadWriteIncompatibilityFlags(int flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<ReadWriteIncompatibilityFlags> fromValue(long value) {
        return FlagUtil.fromValue(values(), value);
    }
}
