package org.jnode.fs.xfs.common;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.extent.DataExtent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>B+tree data extent</p>
 *
 * <p>Provides the infrastructure to read the b+tree data.</p>
 * <p>
 * Long format B+trees are similar to short format B+trees, except that their block pointers are 64-bit filesystem block
 * numbers instead of 32-bit AG block numbers. Because of this, long format b+trees can be (and usually are) rooted
 * in an inode’s data or attribute fork.
 * The nodes and leaves of B+tree use the xfs_btree_lblock.
 * </p>
 *
 * Gets the v5 properties in {@link LongFormBPlusTreeDataV3}.
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
public class LongFormBPlusTreeData extends XfsObject {
    /**
     * The size of this "header" structure.
     */
    private static final int SIZE = 24;

    /**
     * The magic number for a BMBT block (before V5 (exclusive)).
     */
    private static final long BMBT_MAGIC = asciiToHex("BMAP");

    /**
     * Specifies the magic number for the btree block.
     */
    protected final long magic;

    /**
     * The level of the tree in which this block is found. If this value is 0, this is a leaf block and contains records;
     * otherwise, it is a node block and contains keys and pointers.
     */
    private final long level;

    /**
     * Number of records in this block.
     */
    private final long numrecs;

    /**
     * FS block number of the left sibling of this B+tree node.
     */
    private final long leftSib;

    /**
     * FS block number of the right sibling of this B+tree node.
     */
    private final long rightSib;

    /**
     * Creates a b+tree data extent.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the b+tree block.
     */
    public LongFormBPlusTreeData(byte[] data, long offset) throws IOException {
        super(data, (int) offset);

        magic = readUInt32();
        checkSignature();

        level = readUInt16();
        numrecs = readUInt16();
        leftSib = readInt64();
        rightSib = readInt64();
    }

    protected void checkSignature() throws IOException {
        if ((magic != BMBT_MAGIC)) {
            throw new IOException("Wrong magic number for XFS: Required[" + getAsciiSignature(BMBT_MAGIC) + "] found[" + getAsciiSignature(magic) + "]");
        }
    }

    /**
     * Gets the list of extents found.
     *
     * @return the list of extents.
     */
    public List<DataExtent> getExtents() {
        // the beginning of the data extent list.
        long offset = getOffset();

        List<DataExtent> list = new ArrayList<>((int) numrecs);
        for (int i = 0; i < numrecs; i++) {
            DataExtent info = new DataExtent(getData(), (int) offset);
            list.add(info);
            offset += DataExtent.PACKED_LENGTH;
        }
        return list;
    }
}
