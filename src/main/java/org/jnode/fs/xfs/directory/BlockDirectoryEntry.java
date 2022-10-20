package org.jnode.fs.xfs.directory;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.util.BigEndian;

/**
 * A XFS block directory entry inode.
 * <p>
 * Space inside the directory block can be used for directory entries or unused entries. This is signified via a union of
 * the two types:
 * <pre>
 *     typedef union {
 *         xfs_dir2_data_entry_t entry;
 *         xfs_dir2_data_unused_t unused;
 *     } xfs_dir2_data_union_t;
 * </pre>
 *
 * <pre>
 *     typedef struct xfs_dir2_data_entry {
 *         xfs_ino_t inumber;
 *         __uint8_t namelen;
 *         __uint8_t name[1];
 *         __uint8_t ftype;
 *         xfs_dir2_data_off_t tag;
 *     } xfs_dir2_data_entry_t;
 * </pre>
 *
 * <pre>
 *     typedef struct xfs_dir2_data_unused {
 *         __uint16_t freetag; // Must be 0xffff
 *         xfs_dir2_data_off_t length;
 *         xfs_dir2_data_off_t tag;
 *     } xfs_dir2_data_unused_t;
 * </pre>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
@Getter
public abstract class BlockDirectoryEntry extends XfsObject {
    /**
     * Starting offset of the entry, in bytes. This is used for directory iteration.
     */
    long tag;

    /**
     * Creates a directory entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     */
    public BlockDirectoryEntry(byte[] data, long offset) {
        super(data, (int) offset);
    }

    /**
     * Checks if the entry is data or unused one.
     *
     * @param data   of the inode.
     * @param offset of the inode's data.
     * @return {@code true} if it is unused data, or {@code false} if it is data entry.
     */
    public static boolean isFreeTag(byte[] data, long offset) {
        return BigEndian.getUInt16(data, (int) offset) == 0xFFFF;
    }

    /**
     * Gets the offset size of the directory block.
     *
     * @return the offset size.
     */
    public abstract long getOffsetSize();
}

