package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LeafEntry  extends XfsObject {

    /**
     * The Hash value of the name of the directory entry.
     */
    private final long hashval;

    /**
     * The Block offset of the entry.
     */
    private final long address;

    /**
     * The fileSystem.
     */
    private XfsFileSystem fileSystem;

    /**
     * Creates a Leaf entry.
     *
     * @param data of the inode.
     * @param offset of the inode's data
     * @param fileSystem of the image
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public LeafEntry(byte [] data , long offset, XfsFileSystem fileSystem) throws IOException {
        super(data, (int) offset);
        this.fileSystem = fileSystem;
        hashval = read(0,4);
        address = read(4,4);
    }

    /**
     * Gets the magic signature of the leaf.
     *
     * @return the magic value of the leaf block
     */
    @Override
    public long getMagicSignature() throws IOException {
        return 0L;
    }

    /**
     * Validate the magic key data
     *
     * @return a list of valid magic signatures
     */
    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
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
