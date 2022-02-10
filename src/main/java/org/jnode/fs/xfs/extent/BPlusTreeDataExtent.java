package org.jnode.fs.xfs.extent;

import java.io.IOException;
import java.util.ArrayList;

import java.util.List;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * B+tree data extent
 * Provides the infrastructure to read the b+tree data.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class BPlusTreeDataExtent extends XfsObject {

    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(BPlusTreeDataExtent.class);

    /**
     * The magic number for a BMBT block (V5).
     */
    private final static Long MAGIC_V5 = asciiToHex("BMA3");
    /**
     * The magic number for a BMBT block (V5).
     */
    private final static Long MAGIC = asciiToHex("BMAP");
    private final XfsFileSystem fs;

    /**
     * Creates a b+tree data extent.
     *
     * @param data of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the b+tree block.
     */
    public BPlusTreeDataExtent(byte[] data, long offset, XfsFileSystem fileSystem) throws IOException {
        super(data, (int) offset);

        final long signature = getMagicSignature();
        if (signature != MAGIC_V5 && signature != MAGIC) {
            throw new IOException("Wrong magic number for XFS: Required[" + getAsciiSignature(MAGIC_V5) + " or " + getAsciiSignature(MAGIC_V5) + "] found[" + getAsciiSignature(signature) + "]" ) ;
        }
        this.fs = fileSystem;
    }

    /**
     * Gets magic signature.
     *
     * @return the hex value.
     */
    public long getMagicSignature()  {
        return getUInt32(0);
    }

    /**
     * Gets all the entries of the current b+tree directory.
     *
     */
    private List<DataExtent> getExtentInfo() {
        long offset = getOffset() + (fs.getXfsVersion() == 5 ? 72 : 24) ;
        final List<DataExtent> list = new ArrayList<>((int) getNumrecs());
        for (int i=0; i < getNumrecs(); i++) {
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
        return getUInt16(4);
    }

    /**
     * Gets the number of records in this block.
     *
     * @return the numrecs
     */
    public long getNumrecs() {
        return getUInt16(6);
    }

    /**
     * Gets the block number of the right sibling of this B+tree node.
     *
     * @return block number of the right sibling node
     */
    public long getRight() {
        return getInt64(8);
    }

    /**
     * Gets the block number of the left sibling of this B+tree node.
     *
     * @return block number of the left sibling node
     */
    public long getLeft() {
        return getInt64(16);
    }

    /**
     * Gets the block number of this B+tree block.
     *
     * @return the block number
     */
    public long getBlockNo() {
        return getInt64(24);
    }

    /**
     * Gets the log sequence number of the last write to this block.
     *
     * @return the log sequence number
     */
    public long getLsn() {
        return getInt64(32);
    }

    /**
     * Gets the UUID of this block.
     *
     * @return  the UUID
     */
    public String getUuid() {
        return readUuid(40);
    }

    /**
     * Gets the AG number that this B+tree block ought to be in.
     *
     * @return the AG number owner of this block.
     */
    public long getOwner() {
        return getInt64(56);
    }

    /**
     * Gets the list of extents found.
     *
     * @return the list of extents.
     */
    public List<DataExtent> getExtents() {
        return getExtentInfo();
    }

    /**
     * Gets the Checksum of the B+tree block..
     *
     * @return the checksum
     */
    public long getCrc() {
        return getUInt32(64);
    }

}
