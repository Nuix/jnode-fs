package org.jnode.fs.xfs.directory;

import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsEntry;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.inode.INode;

import java.io.IOException;
import java.util.*;

/**
 * A XFS block directory inode.
 *
 * When the shortform directory space exceeds the space in an inode, the
 * directory data is moved into a new single directory block outside the inode.
 * The inode’s format is changed from “local” to “extent”
 *
 * @author
 */

public class BlockDirectory extends XfsObject  {

    /**
     * The magic number XD2B on < v5 filesystem
     */
    private static final long MAGIC_V4 =asciiToHex("XD2B");

    /**
     * The magic number XDB3 on a v5 filesystem
     */
    private static final long MAGIC_V5 = asciiToHex("XDB3");

    /**
     * The offset of the first entry version 4
     */
    public final static int V4_LENGTH = 16;

    /**
     * The offset of the first entry version 5
     */
    public final static int V5_LENGTH = 64;

    /**
     * The filesystem
     */
    XfsFileSystem fs;

    /**
     *  Creates a new block directory entry.
     *
     *  @param data the data.
     *  @param offset the offset.
     *  @param fs the filesystem instance.
     */
    public BlockDirectory(byte[] data, int offset, XfsFileSystem fs) {
        super(data, offset);
        this.fs = fs;
    }

    /**
     * Validate the magic key data
     *
     * @return a list of valid magic signatures
     */
    protected List<Long> validSignatures() { return Arrays.asList(MAGIC_V5,MAGIC_V4); }

    /**
     * Gets the Checksum of the directory block.
     *
     * @return the Checksum
     */
    public long getChecksum() throws IOException {
        return read(4,4);
    }

    /**
     * Gets the Block number of this directory block.
     *
     * @return the Block number
     */
    public long getBlockNumber() throws IOException {
        return read(8,8);
    }

    /**
     * Gets the log sequence number of the last write to this block.
     *
     * @return the log sequence number
     */
    public long getLogSequenceNumber() throws IOException {
        return read(16,8);
    }

    /**
     * Gets the UUID of this block
     *
     * @return the UUID
     */
    public String getUuid() throws IOException {
        return readUuid(24,16);
    }

    /**
     * Gets the inode number that this directory block belongs to
     *
     * @return the parent inode
     */
    public long getParentInode() throws IOException {
        return read(40,8);
    }

    /**
     * Get the inode's entries
     *
     * @return a list of inode entries
     */
    public List<FSEntry> getEntries( FSDirectory parentDirectory ) throws IOException {
        long offset = getOffset() + V5_LENGTH;
        List<FSEntry> data = new ArrayList<>(10);
        int i=0;
        while (true) {
            final BlockDirectoryEntry entry = new BlockDirectoryEntry(getData(), offset, fs);
            if (entry.getNameSize() == 0) {
                break;
            }
            INode iNode = fs.getINode(entry.getINodeNumber());
            data.add(new XfsEntry(iNode, entry.getName(), i++, fs, parentDirectory));
            offset += entry.getOffsetSize();
        }
        return data;
    }
}

