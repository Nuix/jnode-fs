package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * A short form directory entry ('xfs_dir2_sf_entry_t').
 *
 * @author Luke Quinane
 */
public class ShortFormDirectoryEntry extends XfsObject {

    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(ShortFormDirectoryEntry.class);
    /**
     * The number of bytes to get the next offset.
     */
    static int BYTES_FOR_NEXT_OFFSET = 0x8;
    private final boolean isV5;
    /**
     * The size of inode entries in this directory (4 or 8 bytes).
     */
    private final int inodeSize;

    /**
     * Creates a new short-form directory entry.
     *
     * @param data      the data.
     * @param offset    the offset.
     * @param inodeSize the size of inode entries in this directory (4 or 8 bytes).
     * @param v5        is filesystem v5
     */
    public ShortFormDirectoryEntry(byte[] data, int offset, int inodeSize, boolean v5) {
        super(data, offset);
        this.isV5 = v5;
        this.inodeSize = inodeSize;
    }

    /**
     * Gets the length of the name.
     *
     * @return the length.
     */
    public int getNameLength() {
        return getUInt8(0);
    }

    /**
     * Gets the directory entry offset.
     *
     * @return the offset.
     */
    public int getDirectoryEntryOffset() {
        return getUInt16(1);
    }

    /**
     * Gets the name of the entry.
     *
     * @return the entry name.
     */
    public String getName() {
        return new String(getData(), getOffset() + 0x3, getNameLength(), StandardCharsets.UTF_8);
    }

    /**
     * Gets the inode number.
     *
     * @return the inode number.
     */
    public long getINumber() {
        int baseOffset = isV5 ? 0x4 : 0x3;
        int numberOffset = getNameLength() + baseOffset;
        return inodeSize == 4 ? getUInt32(numberOffset) : getInt64(numberOffset);
    }

    /**
     * Get the next offset.
     *
     * @return the offset of the next entry.
     */
    public int getNextEntryOffset() {
        int baseOffset = isV5 ? BYTES_FOR_NEXT_OFFSET : 7;
        return getNameLength() + baseOffset;
    }

    @Override
    public String toString() {
        return String.format("short-dir-entry:[inum: %d name:%s]", getINumber(), getName());
    }
}
