package org.jnode.fs.xfs.directory;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;

/**
 * A XFS leaf info.
 * It is either
 * <pre>
 *     typedef struct xfs_dir2_leaf_hdr {
 *         xfs_da_blkinfo_t info;
 *         __uint16_t count;
 *         __uint16_t stale;
 *     } xfs_dir2_leaf_hdr_t;
 * </pre>
 *
 * or
 * <pre>
 *     struct xfs_dir3_leaf_hdr {
 *         struct xfs_da3_blkinfo info;
 *         __uint16_t count;
 *         __uint16_t stale;
 *         __be32 pad;
 *     };
 * </pre>
 *
 * There are two versions of the blkinfo
 *
 * Version before v5:
 * <pre>
 *     typedef struct xfs_da_blkinfo {
 *         __be32 forw;
 *         __be32 back;
 *         __be16 magic;
 *         __be16 pad;
 *     } xfs_da_blkinfo_t;
 * </pre>
 *
 * Version v5:
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
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
@Getter
public class LeafHeader extends XfsObject {

    /**
     * The magic signature of a leaf directory entry v5.
     */
    private static final long LEAF_DIR_MAGIC_V5 = 0x3DF1;

    /**
     * The magic signature of a leaf directory entry v4.
     */
    private static final long LEAF_DIR_MAGIC = 0xD2F1;

    /**
     * The magic signature of the node directory entry v5.
     */
    private static final long NODE_DIR_MAGIC_V5 = 0x3dff;

    /**
     * The magic signature of the node directory entry.
     */
    private static final long NODE_DIR_MAGIC = 0xd2ff;

    /**
     * Logical block offset of the previous block at this level.
     */
    private final long forward;

    /**
     * The Logical block offset of the next block at this level.
     */
    private final long backward;

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
    private final String uuid;

    /**
     * The inode number that this directory block belongs to.
     */
    private final long owner;

    /**
     * The Number of leaf entries.
     */
    private final int count;

    /**
     * The Number of free leaf entries.
     */
    private final int stale;

    /**
     * Creates a Leaf block information entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @param v5     is filesystem on v5
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public LeafHeader(byte[] data, long offset, boolean v5) throws IOException {
        super(data, (int) offset);

        forward = readUInt32();
        backward = readUInt32();
        long signature = readUInt16();
        if ((signature != LEAF_DIR_MAGIC) && (signature != LEAF_DIR_MAGIC_V5) && (signature != NODE_DIR_MAGIC_V5) && (signature != NODE_DIR_MAGIC)) {
            throw new IOException("Wrong magic number for XFS Leaf Info: " + getAsciiSignature(signature));
        }

        //Padding to maintain alignment.
        skipBytes(2);

        if (v5) {
            crc = readUInt32();
            blockNumber = readInt64();
            logSequenceNumber = readInt64();
            uuid = readUuid();
            owner = readInt64();

            //skip the __be32 pad, "Padding to maintain alignment rules."
            skipBytes(4);
        } else {
            // if v4
            crc = -1;
            blockNumber = -1;
            logSequenceNumber = -1;
            owner = -1;
            uuid = null;
        }
        count = readUInt16();
        stale = readUInt16();
    }

    /**
     * Gets the string information of the leaf entry.
     *
     * @return a string
     */
    @Override
    public String toString() {
        return "LeafInfo{forward=" + forward +
                ", backward=" + backward +
                ", crc=0x" + Long.toHexString(crc) +
                ", blockNumber=" + blockNumber +
                ", logSequenceNumber=0x" + Long.toHexString(logSequenceNumber) +
                ", uuid='" + uuid + '\'' +
                ", owner=" + owner +
                ", count=" + count +
                ", stale=" + stale +
                '}';
    }
}
