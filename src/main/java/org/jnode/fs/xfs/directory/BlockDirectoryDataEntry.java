package org.jnode.fs.xfs.directory;

import lombok.Getter;
import org.jnode.fs.util.FSUtils;

/**
 * A XFS block directory entry inode.
 * <pre>
 *     typedef struct xfs_dir2_data_entry {
 *         xfs_ino_t inumber;
 *         __uint8_t namelen;
 *         __uint8_t name[1];
 *         __uint8_t ftype;
 *         xfs_dir2_data_off_t tag;
 *     } xfs_dir2_data_entry_t;
 * </pre>
 */
@Getter
public class BlockDirectoryDataEntry extends BlockDirectoryEntry {
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
     * ftype.
     * The type of the inode. This is used to avoid reading the inode while iterating a directory. The XFS_SB_VERSION2_FTYPE
     * feature must be set, or this field will not be present.
     */
    private final int inodeType;

    /**
     * The offset size of the directory block.
     */
    private final long offsetSize;

    /**
     * Creates a block directory entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data.
     * @param v5     is filesystem v5.
     */
    public BlockDirectoryDataEntry(byte[] data, long offset, boolean v5) {
        super(data, (int) offset);

        iNodeNumber = readInt64();
        nameLength = readUInt8();
        byte[] buffer = new byte[nameLength];
        System.arraycopy(getData(), getOffset(), buffer, 0, nameLength);
        name = FSUtils.toNormalizedString(buffer);
        skipBytes(nameLength);
        inodeType = readUInt8();
        tag = readUInt16();

        //calculate the offset size of the directory block.
        long l = 12 + nameLength - (v5 ? 0 : 1);
        double v = l / 8.0;
        offsetSize = (long) Math.ceil(v) * 8;
    }

    @Override
    public long getOffsetSize() {
        return offsetSize;
    }
}
