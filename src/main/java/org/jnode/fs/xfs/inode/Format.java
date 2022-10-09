package org.jnode.fs.xfs.inode;

import java.util.Arrays;

/**
 * <p>inode format values.</p>
 *
 * <pre>
 * typedef enum xfs_dinode_fmt {
 *     XFS_DINODE_FMT_DEV,
 *     XFS_DINODE_FMT_LOCAL,
 *     XFS_DINODE_FMT_EXTENTS,
 *     XFS_DINODE_FMT_BTREE,
 *     XFS_DINODE_FMT_UUID,
 *     XFS_DINODE_FMT_RMAP,
 * } xfs_dinode_fmt_t;
 * </pre>
 */
public enum Format {
    /**
     * Character and block devices.
     */
    DEV,

    /**
     * All metadata associated with the file is within the inode.
     */
    LOCAL,

    /**
     * The inode contains an array of extents to other filesystem blocks which contain
     * the associated metadata or data.
     */
    EXTENTS,

    /**
     * The inode contains a B+tree root node which points to filesystem blocks containing
     * the metadata or data.
     */
    BTREE,

    /**
     * Defined, but currently not used.
     */
    UUID,

    /**
     * A reverse-mapping B+tree is rooted in the fork.
     */
    RMAP,

    /**
     * Unknown
     */
    UNKNOWN;

    public static Format fromValue(int value) {
        return Arrays.stream(values())
                .filter(fmt -> fmt.ordinal() == value)
                .findFirst()
                .orElse(UNKNOWN);
    }
}
