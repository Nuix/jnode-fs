package org.jnode.fs.xfs.superblock;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * Read-only compatible feature flags.
 * Flags from the sb_features_ro_compat.
 *
 * @see <a href="https://github.com/torvalds/linux/blob/b2a88c212e652e94f1e4b635910972ac57ba4e97/fs/xfs/libxfs/xfs_format.h#L352-L356">xfs_format.h</a>
 */
public enum ReadOnlyCompatibilityFlags implements Flags {
    /**
     * Free inode B+tree. Each allocation group contains a
     * B+tree to track inode chunks containing free inodes.
     * This is a performance optimization to reduce the
     * time required to allocate inodes.
     */
    FINOBT(0x01),

    /**
     * Reverse mapping B+tree. Each allocation group
     * contains a B+tree containing records mapping AG
     * blocks to their owners. See the section about
     * reconstruction for more details.
     */
    RMAPBT(0x02),

    /**
     * Reference count B+tree. Each allocation group
     * contains a B+tree to track the reference counts of AG
     * blocks. This enables files to share data blocks safely.
     * See the section about reflink and deduplication for
     * more details.
     */
    REFLINK(0x04),

    /**
     * inobt block counts (Only appears in the xfs_format.h, not in the document pdf.)
     */
    INOBTCNT(0x08);

    private final FlagUtil flagUtil;

    ReadOnlyCompatibilityFlags(int flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<ReadOnlyCompatibilityFlags> fromValue(long value) {
        return FlagUtil.fromValue(values(), value);
    }
}
