package org.jnode.fs.xfs.superblock;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * Quota flags.
 * Flags from the sb_qflags.
 *
 * @see <a href="https://github.com/torvalds/linux/blob/master/fs/xfs/libxfs/xfs_log_format.h#L857">xfs_log_format.h</a>
 */
public enum QuotaFlags implements Flags {
    /**
     * User quota accounting is enabled.
     */
    XFS_UQUOTA_ACCT(0x0001),

    /**
     * User quota limits enforced.
     */
    XFS_UQUOTA_ENFD(0x0002),

    /**
     * User quotas have been checked.
     */
    XFS_UQUOTA_CHKD(0x0004),

    /**
     * Project quota accounting is enabled.
     */
    XFS_PQUOTA_ACCT(0x0008),

    /**
     * Other (group/project) quotas are enforced.
     */
    XFS_OQUOTA_ENFD(0x0010),

    /**
     * Other (group/project) quotas have been checked.
     */
    XFS_OQUOTA_CHKD(0x0020),

    /**
     * Group quota accounting is enabled.
     */
    XFS_GQUOTA_ACCT(0x0040),

    /**
     * Group quotas are enforced.
     */
    XFS_GQUOTA_ENFD(0x0080),

    /**
     * Group quotas have been checked.
     */
    XFS_GQUOTA_CHKD(0x0100),

    /**
     * Project quotas are enforced.
     */
    XFS_PQUOTA_ENFD(0x0200),

    /**
     * Project quotas have been checked.
     */
    XFS_PQUOTA_CHKD(0x0400);

    private final FlagUtil flagUtil;

    QuotaFlags(int flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<QuotaFlags> fromValue(int value) {
        return FlagUtil.fromValue(values(), value);
    }
}
