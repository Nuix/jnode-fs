package org.jnode.fs.btrfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;

import org.jnode.fs.ReadOnlyFileSystemException;
import org.jnode.fs.spi.AbstractFSFile;

/**
 * A read-only btrfs file. Its extents are resolved once (on first read) and cached for the life of
 * this handle, so a paging reader that seeks doesn't re-scan the FS tree each time.
 *
 * @author David Baird
 */
public class BtrfsFile extends AbstractFSFile {

    private final BtrfsEntry entry;
    private final BtrfsNode node;
    private List<BtrfsFileContent.Extent> extents;
    /** One-slot decompressed-extent cache: sequential paging re-reads each extent once, not per page. */
    private final BtrfsFileContent.ExtentCache cache = new BtrfsFileContent.ExtentCache();

    public BtrfsFile(BtrfsEntry entry) {
        super((BtrfsFileSystem) entry.getFileSystem());
        this.entry = entry;
        this.node = entry.getNode();
    }

    @Override
    public long getLength() {
        return node.getSize();
    }

    @Override
    public void read(long fileOffset, ByteBuffer dest) throws IOException {
        BtrfsVolume volume = ((BtrfsFileSystem) getFileSystem()).getVolume();
        if (extents == null) {
            extents = BtrfsFileContent.collectExtents(volume, node.getSubvolId(), node.getObjectId());
        }
        int len = dest.remaining();
        byte[] buf = new byte[len];
        int read = BtrfsFileContent.readFromExtents(volume, extents, node.getSize(), fileOffset,
                buf, 0, len, cache);
        if (read > 0) {
            dest.put(buf, 0, read);
        }
    }

    @Override
    public void setLength(long length) throws IOException {
        throw new ReadOnlyFileSystemException("btrfs is read-only");
    }

    @Override
    public void write(long fileOffset, ByteBuffer src) throws IOException {
        throw new ReadOnlyFileSystemException("btrfs is read-only");
    }

    @Override
    public void flush() throws IOException {
        throw new ReadOnlyFileSystemException("btrfs is read-only");
    }
}
