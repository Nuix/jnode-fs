package org.jnode.fs.xfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.jnode.fs.FSDirectoryId;
import org.jnode.fs.FSEntry;
import org.jnode.fs.spi.AbstractFSDirectory;
import org.jnode.fs.spi.FSEntryTable;
import org.jnode.fs.xfs.directory.DirectoryDataEntry;
import org.jnode.fs.xfs.directory.DirectoryDataHeader;
import org.jnode.fs.xfs.inode.INode;
import org.jnode.fs.xfs.directory.ShortFormDirectoryEntry;

/**
 * A XFS directory.
 *
 * @author Luke Quinane.
 */
public class XfsDirectory extends AbstractFSDirectory implements FSDirectoryId {

    /**
     * The related entry.
     */
    private final XfsEntry entry;

    /**
     * The inode.
     */
    private final INode inode;

    /**
     * The file system.
     */
    private final XfsFileSystem fileSystem;

    /**
     * Creates a new directory.
     *
     * @param entry the entry.
     */
    public XfsDirectory(XfsEntry entry) {
        super((XfsFileSystem) entry.getFileSystem());

        this.entry = entry;
        fileSystem = (XfsFileSystem) entry.getFileSystem();
        inode = entry.getINode();
    }

    @Override
    public String getDirectoryId() {
        return Long.toString(inode.getINodeNr());
    }

    @Override
    public FSEntry getEntryById(String id) throws IOException {
        checkEntriesLoaded();
        return getEntryTable().getById(id);
    }

    @Override
    protected FSEntryTable readEntries() throws IOException {
        List<FSEntry> entries = new ArrayList<FSEntry>();

        switch (inode.getFormat()) {
            case XfsConstants.XFS_DINODE_FMT_LOCAL:
                // Entries are stored within the inode record itself
                int iNodeOffset = this.inode.getVersion() == 3 ? INode.V3_DATA_OFFSET: INode.DATA_OFFSET;
                int fourByteEntries = inode.getUInt8(iNodeOffset);
                int eightByteEntries = inode.getUInt8(iNodeOffset + 1);
                int recordSize = fourByteEntries > 0 ? 4 : 8;
                int entryCount = fourByteEntries > 0 ? fourByteEntries : eightByteEntries;
                int offset = iNodeOffset + inode.getOffset() + 0x6;

                // The local storage doesn't store the relative directory entries, so add these in manually
                XfsDirectory parent = (XfsDirectory) entry.getParent();
                entries.add(new XfsEntry(inode, ".", 0, fileSystem, parent));
                if (parent == null) {
                    // This is the root, just add it again
                    entries.add(new XfsEntry(inode, "..", 1, fileSystem, null));
                } else {
                    entries.add(new XfsEntry(parent.inode, "..", 1, fileSystem, parent.entry.getParent()));
                }
                entryCount += entries.size();

                while (entries.size() < entryCount) {
                    ShortFormDirectoryEntry entry = new ShortFormDirectoryEntry(inode.getData(), offset, recordSize);

                    if (entry.getINumber() == 0) {
                        break;
                    }

                    INode childInode = fileSystem.getINode(entry.getINumber());
                    entries.add(new XfsEntry(childInode, entry.getName(), entries.size(), fileSystem, this));

                    offset += entry.getLength();
                }

                break;

            case XfsConstants.XFS_DINODE_FMT_EXTENTS:
                ByteBuffer buffer = ByteBuffer.allocate((int) entry.getINode().getSize());
                entry.read(0, buffer);

                DirectoryDataHeader header = new DirectoryDataHeader(buffer.array(), 0);
                for (DirectoryDataEntry dataEntry : header.readEntries(fileSystem.getSuperblock().getBlockSize())) {
                    INode childInode = fileSystem.getINode(dataEntry.getINumber());
                    entries.add(new XfsEntry(childInode, dataEntry.getName(), entries.size(), fileSystem, this));
                }

                break;

            case XfsConstants.XFS_DINODE_FMT_BTREE:
                /*
                The directory entries are contained in the leaves of a B+tree. The
                inode contains the root node (xfs_bmdr_block_t*).
                 */
                throw new UnsupportedOperationException();

            default:
                throw new IllegalStateException("Unexpected format: " + inode.getFormat());
        }

        return new FSEntryTable(fileSystem, entries);
    }

    @Override
    protected void writeEntries(FSEntryTable entries) throws IOException {
        throw new UnsupportedOperationException("XFS is read-only");
    }

    @Override
    protected FSEntry createFileEntry(String name) throws IOException {
        throw new UnsupportedOperationException("XFS is read-only");
    }

    @Override
    protected FSEntry createDirectoryEntry(String name) throws IOException {
        throw new UnsupportedOperationException("XFS is read-only");
    }
}
