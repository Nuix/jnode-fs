package org.jnode.fs.xfs.directory;

import java.io.IOException;
import java.util.UUID;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

/**
 * <p>On a v5 filesystem, directory and attribute blocks are formatted with v3 headers.</p>
 *
 * <pre>
 *     struct xfs_dir3_blk_hdr {
 *         __be32 magic;
 *         __be32 crc;
 *         __be64 blkno;
 *         __be64 lsn;
 *         uuid_t uuid;
 *         __be64 owner;
 *     };
 * </pre>
 */
@Getter
public class BlockDirectoryHeader extends XfsObject {
    /**
     * The magic number XD2B on < v5 filesystem
     */
    private static final long MAGIC_V4 = asciiToHex("XD2B");

    /**
     * The magic number XDB3 on a v5 filesystem
     */
    private static final long MAGIC_V5 = asciiToHex("XDB3");

    /**
     * Magic number for this directory block.
     */
    private final long magic;

    /**
     * Checksum of the directory block.
     */
    private final long checkSum;

    /**
     * Block number of this directory block.
     */
    private final long blockNumber;

    /**
     * Log sequence number of the last write to this block.
     */
    private final long logSequenceNumber;

    /**
     * The UUID of this block, which must match either sb_uuid or sb_meta_uuid depending on which features
     * are set.
     */
    private final UUID uuid;

    /**
     * The inode number that this directory block belongs to.
     */
    private final long parentInodeNumber;

    /**
     * Creates a new block directory entry.
     *
     * @param data   the data.
     * @param offset the offset.
     */
    public BlockDirectoryHeader(byte[] data, int offset) throws IOException {
        super(data, offset);

        magic = readUInt32();
        if ((magic != MAGIC_V5) && (magic != MAGIC_V4)) {
            throw new IOException("Wrong magic number for XFS: " + getAsciiSignature(magic));
        }
        checkSum = readUInt32();
        blockNumber = readInt64();
        logSequenceNumber = readInt64();
        uuid = readUuid();
        parentInodeNumber = readInt64();
    }
}
