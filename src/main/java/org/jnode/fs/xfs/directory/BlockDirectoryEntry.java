package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * A XFS block directory entry inode.
 *
 * @author
 */
public class BlockDirectoryEntry extends XfsObject {

    /**
     * Length of the name, in bytes
     */
    private final long nameSize;

    /**
     * Magic number signifying that this is an unused entry. Must be 0xFFFF.
     */
    private final boolean isFreeTag;

    /**
     * The inode number that this entry points to.
     */
    private final long iNodeNumber;

    /**
     * The name associated with this entry.
     */
    private final String name;

    /**
     * The file system instance.
     */
    private XfsFileSystem fileSystem;

    /**
     * Creates a b+tree directory entry.
     *
     * @param data of the inode.
     * @param offset of the inode's data
     * @param fileSystem of the image
     * @throws IOException if an error occurs reading in the super block.
     */
    public BlockDirectoryEntry(byte [] data, long offset, XfsFileSystem fileSystem) throws IOException {
        super(data , (int) offset);
        this.fileSystem = fileSystem;
        isFreeTag = read(0,2) == 0xFFFF;
        if (!isFreeTag()) {
            nameSize = read(8, 1);
            iNodeNumber = read(0, 8);
            byte [] buffer = new byte[(int)nameSize];
            System.arraycopy(data, (int)offset + 9 , buffer, 0 , (int) nameSize);
            name = new String(buffer, StandardCharsets.US_ASCII);
        } else {
            nameSize = 0;
            iNodeNumber=0;
            name = "";
        }
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
     * Gets the magic signature.
     *
     * @return the magic signature.
     */
    @Override
    public long getMagicSignature() throws IOException {
        return 0L;
    }

    /**
     * Gets the inode number of this entry.
     *
     * @return the inode number
     */
    public long getINodeNumber() throws IOException {
        return iNodeNumber;
    }

    /**
     * Gets the name size of this entry.
     *
     * @return the name size.
     */
    public long getNameSize() throws IOException {
        return nameSize;
    }

    /**
     * Gets the name of this entry.
     *
     * @return the name entry.
     */
    public String getName() throws IOException {
        return name;
    }

    /**
     * Gets the offset of the directory block.
     *
     * @return the offset directory block
     */
    public long getOffsetFromBlock() throws IOException {
        return read(getNameSize() + 9, 2);
    }

    /**
     * Gets the free tag value of the directory block.
     *
     * @return the free tag.
     */
    public boolean isFreeTag() {
        return isFreeTag;
    }

    /**
     * Gets the offset size of the directory block.
     *
     * @return the offset size.
     */
    public long getOffsetSize() throws IOException {
        if (!isFreeTag) {
            final long l = 12 + nameSize;
            final double v = l / 8.0;
            return (long) Math.ceil(v) * 8;
        } else {
            return read(2,2);
        }
    }

    @Override
    public String toString() {
        return "BlockDirectoryEntry{" +
                "name='" + name + '\'' +
                ", iNodeNumber=" + iNodeNumber +
                ", isFreeTag=" + isFreeTag +
                '}';
    }
}

