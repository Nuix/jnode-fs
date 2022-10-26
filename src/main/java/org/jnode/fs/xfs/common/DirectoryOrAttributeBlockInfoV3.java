package org.jnode.fs.xfs.common;

import java.io.IOException;
import java.util.UUID;

import lombok.Getter;

/**
 * On a v5 filesystem, the leaves use the struct xfs_da3_blkinfo_t filesystem block header.
 * This header is used in the same place as xfs_da_blkinfo_t.
 * <pre>
 *     struct xfs_da3_blkinfo {
 *         __be32 forw;
 *         __be32 back;
 *         __be16 magic;
 *         __be16 pad;
 *         __be32 crc;
 *         __be64 blkno;
 *         __be64 lsn;
 *         uuid_t uuid;
 *         __be64 owner;
 *     };
 * </pre>
 */
@Getter
public class DirectoryOrAttributeBlockInfoV3 extends DirectoryOrAttributeBlockInfo {
    /**
     * The size of this class, it is sizeof(xfs_da_blkinfo_t) + sizeof(__be32 + __be64 + __be64 + uuid_t + __be64)
     * = 12 + 44 = 56.
     */
    public static final int SIZE = DirectoryOrAttributeBlockInfo.SIZE + 44;

    /**
     * The magic signature of a leaf directory entry v5.
     */
    private static final long LEAF_DIR_MAGIC_V5 = 0x3DF1;

    /**
     * The magic signature of the node directory entry v5.
     */
    private static final long NODE_DIR_MAGIC_V5 = 0x3dff;

    /**
     * The magic signature of the leaf attribute header v5.
     */
    public static final int ATTR3_LEAF_MAGIC = 0x3BEE; // XFS_ATTR3_LEAF_MAGIC

    /**
     * Checksum of the directory block.
     */
    private final long crc;

    /**
     * Block number of this directory block.
     */
    private final long blockNumber;

    /**
     * Log sequence number of the last write to this block.
     */
    private final long logSequenceNumber;

    /**
     * The UUID of this block.
     */
    private final UUID uuid;

    /**
     * The inode number that this directory block belongs to.
     */
    private final long owner;

    /**
     * Creates a Leaf block information entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public DirectoryOrAttributeBlockInfoV3(byte[] data, long offset) throws IOException {
        super(data, (int) offset);

        crc = readUInt32();
        blockNumber = readInt64();
        logSequenceNumber = readInt64();
        uuid = readUuid();
        owner = readInt64();
    }

    @Override
    protected void checkSignature() throws IOException {
        if ((magic != LEAF_DIR_MAGIC_V5) && (magic != NODE_DIR_MAGIC_V5) && (magic != ATTR3_LEAF_MAGIC)) {
            throw new IOException("Wrong magic number for V3 XFS Leaf Dir or Node Dir Info: " + getAsciiSignature(magic));
        }
    }

    @Override
    public int getSize() {
        return DirectoryOrAttributeBlockInfoV3.SIZE;
    }
}
