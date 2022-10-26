package org.jnode.fs.xfs.common;

import java.io.IOException;
import java.util.UUID;

import lombok.Getter;

/**
 * <p>B+tree data extent V3</p>
 *
 * <p>Provides the infrastructure to read the b+tree data.</p>
 *
 * extends {@link LongFormBPlusTreeData} and add version 5 filesystem fields.
 *
 * <pre>
 *     struct xfs_btree_lblock {
 *         __be32 bb_magic;
 *         __be16 bb_level;
 *         __be16 bb_numrecs;
 *         __be64 bb_leftsib;
 *         __be64 bb_rightsib;
 *
 *         // version 5 filesystem fields start here
 *         __be64 bb_blkno;
 *         __be64 bb_lsn;
 *         uuid_t bb_uuid;
 *         __be64 bb_owner;
 *         __le32 bb_crc;
 *         __be32 bb_pad;
 *     };
 * </pre>
 */
@Getter
public class LongFormBPlusTreeDataV3 extends LongFormBPlusTreeData {
    /**
     * The size of this "header" structure.
     */
    private static final int SIZE = 72;

    /**
     * The magic number for a BMBT block (V5).
     */
    private static final long BMBT_MAGIC_V5 = asciiToHex("BMA3");

    /**
     * FS block number of this B+tree block.
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
     * The AG number that this B+tree block ought to be in.
     */
    private final long owner;

    /**
     * Checksum of the B+tree block.
     */
    private final long crc;

    /**
     * Creates a b+tree data extent.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the b+tree block.
     */
    public LongFormBPlusTreeDataV3(byte[] data, long offset) throws IOException {
        super(data, (int) offset);

        blockNumber = readInt64();
        logSequenceNumber = readInt64();
        uuid = readUuid();
        owner = readInt64();
        crc = readUInt32();

        // Pads the structure to 64 bytes.
        skipBytes(4);
    }

    @Override
    protected void checkSignature() throws IOException {
        if ((magic != BMBT_MAGIC_V5)) {
            throw new IOException("Wrong magic number for XFS: Required[" + getAsciiSignature(BMBT_MAGIC_V5) + "] found[" + getAsciiSignature(magic) + "]");
        }
    }
}
