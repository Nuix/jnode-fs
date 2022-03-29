package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;

/**
 * A XFS leaf info.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class LeafInfo extends XfsObject {

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
    public LeafInfo(byte[] data, long offset, boolean v5) throws IOException {
        super(data, (int) offset);

        long signature = getMagicSignature();
        if ((signature != LEAF_DIR_MAGIC) && (signature != LEAF_DIR_MAGIC_V5) && (signature != NODE_DIR_MAGIC_V5) && (signature != NODE_DIR_MAGIC)) {
            throw new IOException("Wrong magic number for XFS Leaf Info: " + getAsciiSignature(signature));
        }

        forward = getUInt32(0);
        backward = getUInt32(4);
        if (v5) {
            // 4 byte padding at the end
            crc = getUInt32(12);
            blockNumber = getInt64(16);
            logSequenceNumber = getInt64(24);
            owner = getInt64(48);
            uuid = readUuid(32);
            count = getUInt16(56);
            stale = getUInt16(58);
        } else {
            // if v4
            crc = -1;
            blockNumber = -1;
            logSequenceNumber = -1;
            owner = -1;
            uuid = null;
            count = getUInt16(12);
            stale = getUInt16(14);
        }
    }

    /**
     * Gets the magic signature of the leaf.
     *
     * @return the magic value of the leaf block
     */
    public long getMagicSignature() {
        return getUInt16(8);
    }

    /**
     * Gets the Logical block offset of the previous block at this level.
     *
     * @return a block offset
     */
    public long getForward() {
        return forward;
    }

    /**
     * Gets the Logical block offset of the next block at this level.
     *
     * @return a block offset
     */
    public long getBackward() {
        return backward;
    }

    /**
     * Gets the Checksum of the directory/attribute block.
     *
     * @return a checksum value
     */
    public long getCrc() {
        return crc;
    }

    /**
     * Gets the Block number of this directory/attribute block..
     *
     * @return a block number
     */
    public long getBlockNumber() {
        return blockNumber;
    }

    /**
     * Gets the Log sequence number of the last write to this block.
     *
     * @return a sequence number
     */
    public long getLogSequenceNumber() {
        return logSequenceNumber;
    }

    /**
     * Gets the The UUID of this block.
     *
     * @return a UUID value
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Gets the The inode number that this directory/attribute block belongs to.
     *
     * @return the owner of the block
     */
    public long getOwner() {
        return owner;
    }

    /**
     * Gets the Number of leaf entries.
     *
     * @return a number of node entries.
     */
    public int getCount() {
        return count;
    }

    /**
     * Gets the Number of free leaf entries.
     *
     * @return a number of free entries.
     */
    public int getStale() {
        return stale;
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
