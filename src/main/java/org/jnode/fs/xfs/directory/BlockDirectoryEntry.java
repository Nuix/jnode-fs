package org.jnode.fs.xfs.directory;

import lombok.Getter;
import org.jnode.fs.util.FSUtils;
import org.jnode.fs.xfs.XfsObject;

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
public class BlockDirectoryEntry extends XfsObject {
    /**
     * Magic number signifying that this is an unused entry. Must be 0xFFFF.
     */
    private final boolean freeTag;

    /**
     * The inode number that this entry points to.
     */
    private final long iNodeNumber;

    /**
     * Length of the name, in bytes
     */
    private final int nameLength;

    /**
     * The name associated with this entry.
     */
    private final String name;

    /**
     * Length of this unused entry in bytes, if it is xfs_dir2_data_unu. Or 0, otherwise.
     */
    private final int unusedLength;

    /**
     * Starting offset of the entry, in bytes. This is used for directory iteration.
     */
    private final long tag;

    /**
     * The file system instance.
     */
    private final boolean isV5;

    /**
     * Creates a b+tree directory entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @param v5     is filesystem v5
     */
    public BlockDirectoryEntry(byte[] data, long offset, boolean v5) {
        super(data, (int) offset);
        this.isV5 = v5;
        freeTag = getUInt16(0) == 0xFFFF;

        if (!freeTag) { // it is xfs_dir2_data_entry
            iNodeNumber = readInt64();
            nameLength = readUInt8();
            byte[] buffer = new byte[nameLength];
            System.arraycopy(data, (int) offset + 9, buffer, 0, nameLength);
            name = FSUtils.toNormalizedString(buffer);
            unusedLength = 0;
            skipBytes(nameLength);
        } else { // it is xfs_dir2_data_unused
            nameLength = 0;
            iNodeNumber = 0;
            name = "";

            unusedLength = readUInt16();
        }

        tag = readUInt16();
    }

    /**
     * Gets the offset size of the directory block.
     *
     * @return the offset size.
     */
    public long getOffsetSize() {
        if (!freeTag) {
            long l = 12 + nameLength - (isV5 ? 0 : 1);
            double v = l / 8.0;
            return (long) Math.ceil(v) * 8;
        } else {
            return unusedLength;
        }
    }

    @Override
    public String toString() {
        return "BlockDirectoryEntry{" +
                "name='" + name + '\'' +
                ", iNodeNumber=" + iNodeNumber +
                ", isFreeTag=" + freeTag +
                '}';
    }
}

