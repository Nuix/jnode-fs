package org.jnode.fs.xfs.directory;

import lombok.Getter;

/**
 * An unused XFS block directory entry inode.
 * <pre>
 *     typedef struct xfs_dir2_data_unused {
 *         __uint16_t freetag; // Must be 0xffff
 *         xfs_dir2_data_off_t length;
 *         xfs_dir2_data_off_t tag;
 *     } xfs_dir2_data_unused_t;
 * </pre>
 */
@Getter
public class BlockDirectoryDataUnusedEntry extends BlockDirectoryEntry {
    /**
     * Length of this unused entry in bytes, if it is xfs_dir2_data_unu. Or 0, otherwise.
     */
    private final int unusedLength;

    /**
     * Creates an unused block directory entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data.
     */
    public BlockDirectoryDataUnusedEntry(byte[] data, long offset) {
        super(data, (int) offset);
        skipBytes(2); //the read for freeTag
        unusedLength = readUInt16();
        tag = readUInt16();
    }

    @Override
    public long getOffsetSize() {
        return unusedLength;
    }
}

