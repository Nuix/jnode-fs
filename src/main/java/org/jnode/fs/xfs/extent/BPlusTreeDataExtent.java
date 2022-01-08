package org.jnode.fs.xfs.extent;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsValidSignature;

/**
 *
 * B+tree data extent
 * Provides the infrastructure to read the b+tree data.
 *
 */
public class BPlusTreeDataExtent extends XfsObject {

    /**
     * The magic number for a BMBT block (V5).
     */
    private final static Long MAGIC = asciiToHex("BMA3");

    /**
     * The level of the tree in which this block is found.
     */
    private final long level;

    /**
     *  The number of records in this block.
     */
    private final long numrecs;

    /**
     *  block number of the right sibling of this B+tree node.
     */
    private final long right;

    /**
     *  block number of the left sibling of this B+tree node.
     */
    private final long left;

    /**
     *  block number of this B+tree block.
     */
    private final long blockNo;

    /**
     *  Log sequence number of the last write to this block.
     */
    private final long lsn;

    /**
     *  The UUID of this block.
     */
    private final String uuid;

    /**
     *  The AG number that this B+tree block ought to be in
     */
    private final long owner;

    /**
     *  List of data extent
     */
    private final List<DataExtent> extents;

    /**
     *  Checksum of the B+tree block.
     */
    private final long crc;

    /**
     * Creates a b+tree data extent.
     *
     * @param data of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the b+tree block.
     */
    public BPlusTreeDataExtent(byte[] data, long offset) throws IOException {
        super(data, (int) offset);
        try {
            if (!isValidSignature()) {
                throw new XfsValidSignature(getAsciiSignature(), validSignatures(), (long) offset, this.getClass());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.level = read(4, 2);
        this.numrecs = read(6, 2);
        this.right = read(8, 8);
        this.left = read(16, 8);
        this.blockNo = read(24, 8);
        this.lsn = read(32, 8);
        this.uuid = readUuid(40, 16);
        this.owner = read(56, 8);
        this.crc = read(64, 4);
        this.extents = getExtentInfo();
    }

    /**
     * Gets all the entries of the current b+tree directory.
     *
     */
    private List<DataExtent> getExtentInfo() {
        long offset = getOffset() + 72;
        final List<DataExtent> list = new ArrayList<>((int)numrecs);
        for (int i=0; i<numrecs; i++) {
            final DataExtent info = new DataExtent(getData(), (int) offset);
            list.add(info);
            offset += 0x10;
        }
        return list;
    }

    /**
     * Gets the level of the tree in which this block is found.
     *
     * @return the level
     */
    public long getLevel() {
        return level;
    }

    /**
     * Gets the number of records in this block.
     *
     * @return the numrecs
     */
    public long getNumrecs() {
        return numrecs;
    }

    /**
     * Gets the block number of the right sibling of this B+tree node.
     *
     * @return block number of the right sibling node
     */
    public long getRight() {
        return right;
    }

    /**
     * Gets the block number of the left sibling of this B+tree node.
     *
     * @return block number of the left sibling node
     */
    public long getLeft() {
        return left;
    }

    /**
     * Gets the block number of this B+tree block.
     *
     * @return the block number
     */
    public long getBlockNo() {
        return blockNo;
    }

    /**
     * Gets the log sequence number of the last write to this block.
     *
     * @return the log sequence number
     */
    public long getLsn() {
        return lsn;
    }

    /**
     * Gets the UUID of this block.
     *
     * @return  the UUID
     */
    public String getUuid() {
        return uuid;
    }

    /**
     * Gets the AG number that this B+tree block ought to be in.
     *
     * @return the AG number owner of this block.
     */
    public long getOwner() {
        return owner;
    }

    /**
     * Gets the list of extents found.
     *
     * @return the list of extents.
     */
    public List<DataExtent> getExtents() {
        return extents;
    }

    /**
     * Gets the Checksum of the B+tree block..
     *
     * @return the checksum
     */
    public long getCrc() {
        return crc;
    }

    /**
     * Validate the magic key data
     *
     * @return a list of valid magic signatures
     */
    protected List<Long> validSignatures() {
        return Arrays.asList(MAGIC);
    }

}
