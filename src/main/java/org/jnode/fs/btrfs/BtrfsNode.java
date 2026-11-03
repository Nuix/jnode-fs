package org.jnode.fs.btrfs;

import java.io.IOException;
import java.util.List;

/**
 * A handle to one inode in a {@link BtrfsVolume}: which subvolume it lives in, its object id, name,
 * kind, size and timestamps. Lightweight (navigation looks children up in the subvolume's scanned
 * maps), so a large tree isn't materialised eagerly. Content reads resolve the inode's extents once
 * and keep a one-slot decompressed-extent cache, so repeated or sequential reads through the same
 * handle neither re-descend the FS tree nor re-inflate the same extent.
 *
 * @author David Baird
 */
public class BtrfsNode {

    /** The {@link #getDirIndex() directory index} of a node that has no directory entry (the root). */
    static final long NO_DIR_INDEX = -1L;

    private final BtrfsVolume volume;
    private final long subvolId;
    private final long objectId;
    private final long dirIndex;
    private final String name;
    private final BtrfsVolume.InodeInfo info;
    /** The inode's extents, resolved on first read. */
    private List<BtrfsFileContent.Extent> extents;
    /** One-slot decompressed-extent cache: sequential paging re-reads each extent once, not per page. */
    private final BtrfsFileContent.ExtentCache cache = new BtrfsFileContent.ExtentCache();

    BtrfsNode(BtrfsVolume volume, long subvolId, long objectId, long dirIndex, String name,
            BtrfsVolume.InodeInfo info) {
        this.volume = volume;
        this.subvolId = subvolId;
        this.objectId = objectId;
        this.dirIndex = dirIndex;
        this.name = name;
        this.info = info;
    }

    /**
     * @return the entry name of this node within its parent directory.
     */
    public String getName() {
        return name;
    }

    /**
     * @return {@code true} for a directory (including a subvolume root).
     */
    public boolean isDirectory() {
        return info.directory;
    }

    /**
     * A symbolic link (its {@link #getSize() size} is the target path length; never descended).
     *
     * @return {@code true} for a symlink inode ({@code S_IFLNK}).
     */
    public boolean isSymlink() {
        return info.symlink;
    }

    /**
     * @return the inode's size in bytes (for a symlink, the target path length).
     */
    public long getSize() {
        return info.size;
    }

    /**
     * @return last content modification (mtime) in epoch milliseconds.
     */
    public long getLastModified() {
        return info.modifiedMs;
    }

    /**
     * @return last inode change (ctime) in epoch milliseconds.
     */
    public long getLastChanged() {
        return info.changedMs;
    }

    /**
     * @return last access (atime) in epoch milliseconds.
     */
    public long getLastAccessed() {
        return info.accessedMs;
    }

    /**
     * @return creation/birth time (otime) in epoch milliseconds; {@code 0} when never set
     *         (e.g. images produced by {@code mkfs.btrfs -r}).
     */
    public long getCreated() {
        return info.createdMs;
    }

    long getSubvolId() {
        return subvolId;
    }

    long getObjectId() {
        return objectId;
    }

    /**
     * The {@code DIR_INDEX} key offset of this node's directory entry: unique among the entries of
     * one directory, so it tells apart hard links to the same inode (which share an object id).
     * {@link #NO_DIR_INDEX} for the volume root, which has no entry.
     */
    long getDirIndex() {
        return dirIndex;
    }

    /**
     * The target path of a symlink (stored as the inode's inline content); empty if not a symlink.
     *
     * @return the symlink target path.
     * @throws IOException if the target cannot be read.
     */
    public String getSymlinkTarget() throws IOException {
        if (!info.symlink || info.size <= 0) {
            return "";
        }
        byte[] buf = new byte[(int) info.size];
        int pos = 0;
        while (pos < buf.length) {
            int n = read(pos, buf, pos, buf.length - pos);
            if (n <= 0) {
                break;
            }
            pos += n;
        }
        return new String(buf, 0, pos, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * The directory's children (empty for a file).
     *
     * @return the child nodes, in directory-index order.
     * @throws IOException if the subvolume's FS tree cannot be read.
     */
    public List<BtrfsNode> getChildren() throws IOException {
        return volume.listChildren(this);
    }

    /**
     * This inode's extended attributes (xattrs): SELinux context, {@code user.*} pairs, POSIX ACLs,
     * capabilities, btrfs per-file properties.
     *
     * @return the attributes in key order; empty if none.
     * @throws IOException if the FS tree cannot be read.
     */
    public List<org.jnode.fs.FSAttribute> getXattrs() throws IOException {
        return volume.listXattrs(subvolId, objectId);
    }

    /**
     * Reads up to {@code len} bytes of this file starting at {@code fileOffset}.
     *
     * @param fileOffset the byte offset within the file to start reading at.
     * @param dst        the destination buffer.
     * @param off        the offset within {@code dst} to write to.
     * @param len        the maximum number of bytes to read.
     * @return the number of bytes read, or {@code -1} at end of file.
     * @throws IOException if the extents cannot be read.
     */
    public int read(long fileOffset, byte[] dst, int off, int len) throws IOException {
        if (extents == null) {
            extents = BtrfsFileContent.collectExtents(volume, subvolId, objectId);
        }
        return BtrfsFileContent.readFromExtents(volume, extents, info.size, fileOffset, dst, off, len, cache);
    }
}
