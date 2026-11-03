package org.jnode.fs.btrfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jnode.fs.FSEntry;
import org.jnode.fs.ReadOnlyFileSystemException;
import org.jnode.fs.spi.AbstractFSDirectory;
import org.jnode.fs.spi.FSEntryTable;

/**
 * A btrfs directory: its entries come from the {@link BtrfsNode}'s children (which cross into
 * subvolumes where applicable). Read-only.
 *
 * @author David Baird
 */
public class BtrfsDirectory extends AbstractFSDirectory {

    private final BtrfsEntry entry;
    private final BtrfsFileSystem fileSystem;

    public BtrfsDirectory(BtrfsEntry entry) {
        super((BtrfsFileSystem) entry.getFileSystem());
        this.entry = entry;
        this.fileSystem = (BtrfsFileSystem) entry.getFileSystem();
    }

    @Override
    public FSEntry getEntryById(String id) {
        checkEntriesLoaded();
        return getEntryTable().getById(id);
    }

    @Override
    protected FSEntryTable readEntries() throws IOException {
        List<FSEntry> entries = new ArrayList<FSEntry>();
        for (BtrfsNode child : entry.getNode().getChildren()) {
            entries.add(new BtrfsEntry(child, child.getName(), fileSystem, this));
        }
        return new FSEntryTable(fileSystem, entries);
    }

    @Override
    protected void writeEntries(FSEntryTable entries) throws IOException {
        throw new ReadOnlyFileSystemException("btrfs is read-only");
    }

    @Override
    protected FSEntry createFileEntry(String name) throws IOException {
        throw new ReadOnlyFileSystemException("btrfs is read-only");
    }

    @Override
    protected FSEntry createDirectoryEntry(String name) throws IOException {
        throw new ReadOnlyFileSystemException("btrfs is read-only");
    }
}
