package org.jnode.fs.xfs.superblock;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * The version flags in the version value sb_versionnum
 * Filesystem version number. This is a bitmask specifying the features enabled when creating the filesystem.
 * Any disk checking tools or drivers that do not recognize any set bits must not operate upon the filesystem.
 * Most of the flags indicate features introduced over time. If the value of the lower nibble is >= 4, the higher bits
 * indicate feature flags in this class.
 */
public enum VersionFlags implements Flags {
    /**
     * Set if any inode have extended attributes.
     */
    ATTRBIT(0x10),

    /**
     * Set if any inodes use 32-bit di_nlink values.
     */
    NLINKBIT(0x20),

    /**
     * Quotas are enabled on the filesystem. This also brings in the various quota fields in the superblock.
     */
    QUOTABIT(0x40),

    /**
     * Set if sb_inoalignmt is used.
     */
    ALIGNBIT(0x80),

    /**
     * Set if sb_unit and sb_width are used.
     */
    DALIGNBIT(0x100),

    /**
     * Set if sb_shared_vn is used.
     */
    SHAREDBIT(0x200),

    /**
     * Version 2 journaling logs are used.
     */
    LOGV2BIT(0x400),

    /**
     * Set if sb_sectsize is not 512.
     */
    SECTORBIT(0x800),

    /**
     * Unwritten extents are used. This is always set.
     */
    EXTFLGBIT(0x1000),

    /**
     * Version 2 directories are used. This is always set.
     */
    DIRV2BIT(0x2000),

    /**
     * Set if the sb_features2 field in the superblock contains more flags.
     */
    MOREBITSBIT(0x4000);

    private final FlagUtil flagUtil;

    VersionFlags(int flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<VersionFlags> fromValue(int value) {
        return FlagUtil.fromValue(values(), value);
    }
}
