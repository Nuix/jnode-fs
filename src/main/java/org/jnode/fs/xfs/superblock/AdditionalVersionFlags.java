package org.jnode.fs.xfs.superblock;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * Flags from the sb_features2.
 * Additional version flags if XFS_SB_VERSION_MOREBITSBIT is set in sb_versionnum. The currently
 * defined additional features include flags in this class.
 * AKA, Extended Version 4 Superblock flags.
 */
public enum AdditionalVersionFlags implements Flags {
    /**
     * Lazy global counters. Making a filesystem with this bit set can improve
     * performance. The global free space and inode counts are only updated in
     * the primary superblock when the filesystem is cleanly unmounted.
     */
    LAZYSBCOUNTBIT(0x01),

    /**
     * Extended attributes version 2. Making a filesystem with this optimises
     * the inode layout of extended attributes. If this bit is set and the noattr2
     * mount flag is not specified, the di_forkoff inode field will be dynamically
     * adjusted.
     */
    ATTR2BIT(0x02),

    /**
     * Parent pointers. All inodes must have an extended attribute that points
     * back to its parent node. The primary purpose for this information is in
     * backup systems.
     */
    PARENTBIT(0x04),

    /**
     * 32-bit Project ID. Inodes can be associated with a project ID number,
     * which can be used to enforce disk space usage quotas for a particular
     * group of directories.This flag indicates that project IDs can be 32 bits
     * in size.
     */
    PROJID32BIT(0x08),

    /**
     * Metadata checksumming. All metadata blocks have an extended header containing
     * the block checksum, a copy of the metadata UUID, the log sequence number of the
     * last update to prevent stale replays, and a back pointer to the owner of the
     * block. This feature must be and can only be set of the lowest nibble of
     * sb_versionnum is set to 5.
     */
    CRCBIT(0x10),

    /**
     * Directory file type. Each directory entry records the type of the inode to which
     * the entry points. This speeds up directory iteration by removing the need to load
     * every inode into memory.
     */
    FTYPE(0x20);

    private final FlagUtil flagUtil;

    AdditionalVersionFlags(int flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<AdditionalVersionFlags> fromValue(long value) {
        return FlagUtil.fromValue(values(), value);
    }
}
