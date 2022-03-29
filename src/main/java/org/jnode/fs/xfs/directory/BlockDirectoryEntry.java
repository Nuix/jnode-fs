package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsObject;

import java.nio.charset.StandardCharsets;

/**
 * A XFS block directory entry inode.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class BlockDirectoryEntry extends XfsObject {

    /**
     * Length of the name, in bytes
     */
    private final int nameSize;

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
    private final boolean isV5;

    /**
     * Creates a b+tree directory entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @param v5     is filesystem v5
     */
    public BlockDirectoryEntry(byte[] data, long offset, boolean v5) {
        super(data, (int) offset);
        this.isV5 = v5;
        isFreeTag = getUInt16(0) == 0xFFFF;
        if (!isFreeTag()) {
            nameSize = getUInt8(8);
            iNodeNumber = getInt64(0);
            byte[] buffer = new byte[nameSize];
            System.arraycopy(data, (int) offset + 9, buffer, 0, nameSize);
            name = new String(buffer, StandardCharsets.UTF_8);
        } else {
            nameSize = 0;
            iNodeNumber = 0;
            name = "";
        }
    }

    /**
     * Gets the inode number of this entry.
     *
     * @return the inode number
     */
    public long getINodeNumber() {
        return iNodeNumber;
    }

    /**
     * Gets the name size of this entry.
     *
     * @return the name size.
     */
    public int getNameSize() {
        return nameSize;
    }

    /**
     * Gets the name of this entry.
     *
     * @return the name entry.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the offset of the directory block.
     *
     * @return the offset directory block
     */
    public long getOffsetFromBlock() {
        return getUInt16(getNameSize() + 9);
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
    public long getOffsetSize() {
        if (!isFreeTag) {
            long l = 12 + nameSize - (isV5 ? 0 : 1);
            double v = l / 8.0;
            return (long) Math.ceil(v) * 8;
        } else {
            return getUInt16(2);
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

