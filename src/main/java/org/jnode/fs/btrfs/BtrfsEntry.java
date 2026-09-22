package org.jnode.fs.btrfs;

import java.io.IOException;

import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntryCreated;
import org.jnode.fs.FSEntryLastAccessed;
import org.jnode.fs.FSEntryLastChanged;
import org.jnode.fs.spi.AbstractFSEntry;

/**
 * A btrfs directory entry, wrapping a {@link BtrfsNode}.
 *
 * @author David Baird
 */
public class BtrfsEntry extends AbstractFSEntry
        implements FSEntryCreated, FSEntryLastAccessed, FSEntryLastChanged {

    private final BtrfsNode node;

    public BtrfsEntry(BtrfsNode node, String name, BtrfsFileSystem fs, FSDirectory parent) {
        super(fs, null, parent, name, entryType(name, node));
        this.node = node;
    }

    public BtrfsNode getNode() {
        return node;
    }

    private static int entryType(String name, BtrfsNode node) {
        if ("/".equals(name)) {
            return AbstractFSEntry.ROOT_ENTRY;
        }
        return node.isDirectory() ? AbstractFSEntry.DIR_ENTRY : AbstractFSEntry.FILE_ENTRY;
    }

    /**
     * {@inheritDoc}
     *
     * <p>btrfs object ids restart at 256 within <em>every</em> subvolume, so the id must include the
     * subvolume to be unique across the volume.</p>
     */
    @Override
    public String getId() {
        return node.getSubvolId() + "-" + node.getObjectId();
    }

    @Override
    public long getLastModified() {
        return node.getLastModified();
    }

    @Override
    public long getLastChanged() {
        return node.getLastChanged();
    }

    @Override
    public long getLastAccessed() {
        return node.getLastAccessed();
    }

    @Override
    public long getCreated() {
        return node.getCreated();
    }

    /**
     * The entry's extended attributes, mirroring {@code XfsEntry.getAttributes()}.
     *
     * @return the inode's xattrs in key order; empty if none.
     * @throws IOException if the FS tree cannot be read.
     */
    public java.util.List<org.jnode.fs.FSAttribute> getAttributes() throws IOException {
        return node.getXattrs();
    }
}
