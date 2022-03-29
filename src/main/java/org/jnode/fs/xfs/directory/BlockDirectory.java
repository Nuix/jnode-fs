package org.jnode.fs.xfs.directory;

import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsEntry;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.inode.INode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>A XFS block directory inode.</p>
 *
 * <p>When the shortform directory space exceeds the space in an inode, the
 * directory data is moved into a new single directory block outside the inode.
 * The inode’s format is changed from “local” to “extent”.</p>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class BlockDirectory extends XfsObject {

    /**
     * The offset of the first entry version 4
     */
    public static final int V4_LENGTH = 16;

    /**
     * The offset of the first entry version 5
     */
    public static final int V5_LENGTH = 64;

    /**
     * The magic number XD2B on < v5 filesystem
     */
    private static final long MAGIC_V4 = asciiToHex("XD2B");

    /**
     * The magic number XDB3 on a v5 filesystem
     */
    private static final long MAGIC_V5 = asciiToHex("XDB3");

    /**
     * The filesystem.
     */
    XfsFileSystem fs;

    /**
     * Creates a new block directory entry.
     *
     * @param data   the data.
     * @param offset the offset.
     * @param fs     the filesystem instance.
     */
    public BlockDirectory(byte[] data, int offset, XfsFileSystem fs) throws IOException {
        super(data, offset);

        if ((getMagicSignature() != MAGIC_V5) && (getMagicSignature() != MAGIC_V4)) {
            throw new IOException("Wrong magic number for XFS: " + getAsciiSignature(getMagicSignature()));
        }
        this.fs = fs;
    }

    /**
     * Gets magic signature.
     *
     * @return the hex value.
     */
    public long getMagicSignature() {
        return getUInt32(0);
    }

    /**
     * Gets the Checksum of the directory block.
     *
     * @return the Checksum
     */
    public long getChecksum() {
        return getUInt32(4);
    }

    /**
     * Gets the Block number of this directory block.
     *
     * @return the Block number
     */
    public long getBlockNumber() {
        return getInt64(8);
    }

    /**
     * Gets the log sequence number of the last write to this block.
     *
     * @return the log sequence number
     */
    public long getLogSequenceNumber() {
        return getInt64(16);
    }

    /**
     * Gets the UUID of this block
     *
     * @return the UUID
     */
    public String getUuid() {
        return readUuid(24);
    }

    /**
     * Gets the inode number that this directory block belongs to
     *
     * @return the parent inode
     */
    public long getParentInode() {
        return getInt64(40);
    }

    /**
     * Get the inode's entries
     *
     * @return a list of inode entries
     */
    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        int blockSize = getData().length;
        long stale = getUInt32(blockSize - 4);
        long count = getUInt32(blockSize - 8);
        int activeDirs = (int) (count - stale);


        List<FSEntry> data = new ArrayList<>(activeDirs);
        int leafOffset = blockSize - ((activeDirs + 1) * 8);
        for (int i = 0; i < activeDirs; i++) {
            LeafEntry leafEntry = new LeafEntry(getData(), leafOffset + (i * 8L));
            if (leafEntry.getAddress() == 0) {
                continue;
            }
            BlockDirectoryEntry entry = new BlockDirectoryEntry(getData(), leafEntry.getAddress() * 8, fs.isV5());

            INode iNode = fs.getINode(entry.getINodeNumber());
            data.add(new XfsEntry(iNode, entry.getName(), i, fs, parentDirectory));
        }
        return data;
    }
}

