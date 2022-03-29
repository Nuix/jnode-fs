package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;

/**
 * Leaf entry.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class LeafEntry extends XfsObject {

    /**
     * The Hash value of the name of the directory entry.
     */
    private final long hashval;

    /**
     * The Block offset of the entry.
     */
    private final long address;

    /**
     * Creates a Leaf entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public LeafEntry(byte[] data, long offset) {
        super(data, (int) offset);
        hashval = getUInt32(0);
        address = getUInt32(4);
    }

    /**
     * Gets the Hash value of the name of the directory entry.
     *
     * @return the Hash value of the name of the directory entry.
     */
    public long getHashval() {
        return hashval;
    }

    /**
     * Gets the Block offset of the entry
     *
     * @return the Block offset of the entry
     */
    public long getAddress() {
        return address;
    }

    /**
     * Gets the string information of the leaf entry.
     *
     * @return a string
     */
    @Override
    public String toString() {
        return "LeafEntry{hashval=" + Long.toHexString(hashval) +
                ", address=" + Long.toHexString(address) + '}';
    }
}
