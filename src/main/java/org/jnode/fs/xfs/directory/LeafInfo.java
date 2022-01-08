package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.XfsValidSignature;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class LeafInfo extends XfsObject {

    /**
     * The magic signature of a leaf directory entry.
     */
    public static final long LEAF_DIR_MAGIC = 0x3df1;

    /**
     * The magic signature of the node directory entry.
     */
    public static final long NODE_DIR_MAGIC = 0x3dff;

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
    private final long count;

    /**
     * The Number of free leaf entries.
     */
    private final long stale;

    /**
     * The filesystem.
     */
    private final XfsFileSystem fileSystem;

    /**
     * Creates a Leaf block information entry.
     *
     * @param data of the inode.
     * @param offset of the inode's data
     * @param fileSystem of the image
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public LeafInfo(byte [] data, long offset, XfsFileSystem fileSystem) throws IOException {
        super(data, (int) offset);
        try {
            if (!isValidSignature()) {
                throw new XfsValidSignature(getAsciiSignature(), validSignatures(), (long) offset, this.getClass());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.fileSystem = fileSystem;
        forward = read(0,4);
        backward = read(4,4);
        crc = read(12,4);
        blockNumber = read(16,8);
        logSequenceNumber = read(24,8);
        owner = read(48,8);
        uuid = readUuid(32,16);
        count = read(56,2);
        stale = read(58,2);
        // 4 byte padding at the end
    }

    /**
     * Gets the magic signature of the leaf.
     *
     * @return the magic value of the leaf block
     */
    @Override
    public long getMagicSignature() throws IOException {
        return read(8,2);
    }

    /**
     * Validate the magic key data
     *
     * @return a list of valid magic signatures
     */
    protected List<Long> validSignatures() {
        return Arrays.asList(LEAF_DIR_MAGIC,NODE_DIR_MAGIC);
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
    public long getCount() {
        return count;
    }

    /**
     * Gets the Number of free leaf entries.
     *
     * @return a number of free entries.
     */
    public long getStale() {
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
