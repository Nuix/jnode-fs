package org.jnode.fs.xfs.inode;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * Specifies flags associated with the inode.
 * Flags from the di_flags
 * <p>inode flags.</p>
 */
public enum InodeFlags implements Flags {
    /**
     * The inode’s data is located on the real-time device.
     */
    REALTIME(0x01),

    /**
     * The inode’s extents have been preallocated.
     */
    PREALLOC(0x02),

    /**
     * Specifies the sb_rbmino uses the new real-time bitmap
     * format.
     */
    NEWRTBM(0x04),

    /**
     * Specifies the inode cannot be modified.
     */
    IMMUTABLE(0x08),

    /**
     * The inode is in append only mode.
     */
    APPEND(0x10),

    /**
     * The inode is written synchronously.
     */
    SYNC(0x20),

    /**
     * The inode’s di_atime is not updated.
     */
    NOATIME(0x40),

    /**
     * Specifies the inode is to be ignored by xfsdump.
     */
    NODUMP(0x80),

    /**
     * For directory inodes, new inodes inherit the
     * XFS_DIFLAG_REALTIME bit.
     */
    RTINHERIT(0x100),

    /**
     * For directory inodes, new inodes inherit the di_projid
     * value.
     */
    PROJINHERIT(0x200),

    /**
     * For directory inodes, symlinks cannot be created.
     */
    NOSYMLINKS(0x400),

    /**
     * Specifies the extent size for real-time files or an extent size
     * hint for regular files.
     */
    EXTSIZE(0x800),

    /**
     * For directory inodes, new inodes inherit the di_extsize
     * value.
     */
    EXTSZINHERIT(0x1000),

    /**
     * Specifies the inode is to be ignored when defragmenting
     * the filesystem.
     */
    NODEFRAG(0x2000),

    /**
     * Use the filestream allocator. The filestreams allocator
     * allows a directory to reserve an entire allocation group for
     * exclusive use by files created in that directory. Files in
     * other directories cannot use AGs reserved by other
     * directories
     */
    FILESTREAMS(0x4000);

    private final FlagUtil flagUtil;

    InodeFlags(long flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<InodeFlags> fromValue(long value) {
        return FlagUtil.fromValue(values(), value);
    }
}
