package org.jnode.fs.xfs;

import org.jnode.fs.spi.AbstractFSFile;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A XFS file.
 *
 * @author Luke Quinane.
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class XfsFile extends AbstractFSFile {

    /**
     * The entry.
     */
    private XfsEntry entry;

    /**
     * Creates a new file.
     *
     * @param entry the entry.
     */
    public XfsFile(XfsEntry entry) {
        super((XfsFileSystem) entry.getFileSystem());
        this.entry = entry;
    }

    @Override
    public long getLength() {
        return entry.getINode().getSize();
    }

    @Override
    public void read(long fileOffset, ByteBuffer dest) throws IOException {
        entry.read(fileOffset, dest);
    }

    @Override
    public void setLength(long length) throws IOException {
        throw new UnsupportedOperationException("XFS is read only");
    }

    @Override
    public void write(long fileOffset, ByteBuffer src) throws IOException {
        throw new UnsupportedOperationException("XFS is read only");
    }

    @Override
    public void flush() throws IOException {
        throw new UnsupportedOperationException("XFS is read only");
    }
}
