package org.jnode.fs.xfs.superblock;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * Miscellaneous flags.
 * Flags from the sb_flags.
 *
 * @see <a href="https://github.com/torvalds/linux/blob/b2a88c212e652e94f1e4b635910972ac57ba4e97/fs/xfs/libxfs/xfs_format.h#L273">xfs_format.h</a>
 */
public enum MiscFlags implements Flags {
    /**
     * only read-only mounts allowed.
     */
    READONLY(0x01);

    private final FlagUtil flagUtil;

    MiscFlags(int flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<MiscFlags> fromValue(int value) {
        return FlagUtil.fromValue(values(), value);
    }
}
