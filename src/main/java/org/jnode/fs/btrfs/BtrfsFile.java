package org.jnode.fs.btrfs;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jnode.fs.ReadOnlyFileSystemException;
import org.jnode.fs.spi.AbstractFSFile;

/**
 * A read-only btrfs file. Reads go through its {@link BtrfsNode}, which resolves the extents once
 * and caches the last decompressed extent, so a paging reader that seeks doesn't re-scan the FS
 * tree each time.
 *
 * @author David Baird
 */
public class BtrfsFile extends AbstractFSFile {

    private final BtrfsNode node;

    public BtrfsFile(BtrfsEntry entry) {
        super((BtrfsFileSystem) entry.getFileSystem());
        this.node = entry.getNode();
    }

    @Override
    public long getLength() {
        return node.getSize();
    }

    @Override
    public void read(long fileOffset, ByteBuffer dest) throws IOException {
        int len = dest.remaining();
        if (dest.hasArray()) {
            // read straight into the caller's array: no second full-size buffer per read
            int read = node.read(fileOffset, dest.array(), dest.arrayOffset() + dest.position(), len);
            if (read > 0) {
                dest.position(dest.position() + read);
            }
            return;
        }
        byte[] buf = new byte[len];
        int read = node.read(fileOffset, buf, 0, len);
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
