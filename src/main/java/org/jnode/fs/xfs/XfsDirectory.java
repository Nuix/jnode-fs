package org.jnode.fs.xfs;

import org.jnode.driver.ApiNotFoundException;
import org.jnode.fs.FSDirectoryId;
import org.jnode.fs.FSEntry;
import org.jnode.fs.spi.AbstractFSDirectory;
import org.jnode.fs.spi.FSEntryTable;
import org.jnode.fs.xfs.directory.*;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A XFS directory.
 *
 * @author Luke Quinane.
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class XfsDirectory extends AbstractFSDirectory implements FSDirectoryId {

    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(XfsDirectory.class);

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
                int iNodeOffset = inode.getVersion() == INode.V3 ? INode.V3_DATA_OFFSET : INode.DATA_OFFSET;
                int fourByteEntries = inode.getUInt8(iNodeOffset);
                int eightByteEntries = inode.getUInt8(iNodeOffset + 1);
                int recordSize = fourByteEntries > 0 ? 4 : 8;
                int entryCount = fourByteEntries > 0 ? fourByteEntries : eightByteEntries;
                int offset = iNodeOffset + inode.getOffset() + 0x6;

                if (!inode.isSymLink()) {
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
                        ShortFormDirectoryEntry entry = new ShortFormDirectoryEntry(inode.getData(), offset, recordSize, fileSystem.isV5());

                        if (entry.getINumber() == 0) {
                            return null;
                        }

                        INode childInode = fileSystem.getINode(entry.getINumber());
                        entries.add(new XfsEntry(childInode, entry.getName(), entries.size(), fileSystem, this));

                        offset += entry.getNextEntryOffset();

                    }
                } else {

                    ShortFormDirectoryEntry entry = new ShortFormDirectoryEntry(inode.getData(), offset, recordSize, fileSystem.isV5());
                    if (entry.getINumber() == 0) {
                        break;
                    }
                    INode childInode = fileSystem.getINode(entry.getINumber());
                    entries.add(new XfsEntry(childInode, entry.getName(), entries.size(), fileSystem, this));

                }
                break;

            case XfsConstants.XFS_DINODE_FMT_EXTENTS:
                if (!inode.isDirectory()) {
                    throw new UnsupportedOperationException("Trying to get directories of a non directory inode");
                }
                final List<DataExtent> extents = inode.getExtentInfo();

                if (extents.size() == 1) {
                    // Block Directory
                    if (log.isDebugEnabled()) {
                        log.debug("Processing a Block directory, Inode Number: " + entry.getINode().getINodeNr());
                    }
                    final DataExtent extentInformation = extents.get(0);
                    final long extOffset = extentInformation.getExtentOffset(fileSystem);
                    ByteBuffer buffer = ByteBuffer.allocate((int) fileSystem.getSuperblock().getBlockSize() * (int) extentInformation.getBlockCount());
                    try {
                        fileSystem.getFSApi().read(extOffset, buffer);
                    } catch (ApiNotFoundException e) {
                        log.warn("Failed to read directory entries at offset: " + extOffset, e);
                    }
                    final BlockDirectory myBlockDirectory = new BlockDirectory(buffer.array(), 0, fileSystem);
                    entries = myBlockDirectory.getEntries(this);
                } else {

                    long leafExtentIndex = LeafDirectory.getLeafExtentIndex(extents, fileSystem);
                    long iNodeNumber = entry.getINode().getINodeNr();

                    if (leafExtentIndex == -1) {
                        throw new IOException("Cannot compute leaf extent for inode " + iNodeNumber);
                    }
                    final DataExtent extentInformation = extents.get((int) leafExtentIndex);
                    final long extOffset = extentInformation.getExtentOffset(fileSystem);
                    ByteBuffer buffer = ByteBuffer.allocate((int) fileSystem.getSuperblock().getBlockSize() * (int) extentInformation.getBlockCount());
                    try {
                        fileSystem.getFSApi().read(extOffset, buffer);
                    } catch (ApiNotFoundException e) {
                        log.warn("Failed to read directory entries at offset: " + extOffset, e);
                    }

                    if (leafExtentIndex == (extents.size() - 1)) {
                        // Leaf Directory
                        if (log.isDebugEnabled()) {
                            log.debug("Processing a Leaf directory, Inode Number: " + iNodeNumber);
                        }
                        final LeafDirectory myLeafDirectory = new LeafDirectory(buffer.array(), 0, fileSystem, iNodeNumber, extents);
                        entries = myLeafDirectory.getEntries(this);
                    } else {
                        // Node Directory
                        if (log.isDebugEnabled()) {
                            log.debug("Processing a Node directory, Inode Number: " + iNodeNumber);
                        }
                        final NodeDirectory myNodeDirectory = new NodeDirectory(buffer.array(), 0, fileSystem, iNodeNumber, extents, leafExtentIndex);
                        entries = myNodeDirectory.getEntries(this);
                    }
                }
                break;

            case XfsConstants.XFS_DINODE_FMT_BTREE:
                long iNodeNumber = entry.getINode().getINodeNr();
                if (log.isDebugEnabled()) {
                    log.debug("Processing a B+tree directory, Inode Number: {}", iNodeNumber);
                }
                BPlusTreeDirectory myBPlusTreeDirectory = new BPlusTreeDirectory(entry.getINode().getData(), 0, iNodeNumber, fileSystem);
                entries = myBPlusTreeDirectory.getEntries(this);
                break;
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
