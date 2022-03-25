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
    private static final Logger logger = LoggerFactory.getLogger(XfsDirectory.class);

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
        return Long.toString(inode.getINodeNumber());
    }

    @Override
    public FSEntry getEntryById(String id) {
        checkEntriesLoaded();
        return getEntryTable().getById(id);
    }

    @Override
    protected FSEntryTable readEntries() throws IOException {
        List<FSEntry> entries = new ArrayList<>();

        switch (inode.getFormat()) {
            case LOCAL:
                // Entries are stored within the inode record itself
                int inodeDataOffset = inode.getDataOffset();
                int fourByteEntries = inode.getUInt8(inodeDataOffset);
                int eightByteEntries = inode.getUInt8(inodeDataOffset + 1);
                int recordSize = fourByteEntries > 0 ? 4 : 8;
                int entryCount = fourByteEntries > 0 ? fourByteEntries : eightByteEntries;
                int offset = inodeDataOffset + inode.getOffset() + 6;

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
                        ShortFormDirectoryEntry dirEntry = new ShortFormDirectoryEntry(inode.getData(), offset, recordSize, fileSystem.isV5());

                        if (dirEntry.getINumber() == 0) {
                            return null;
                        }

                        INode childInode = fileSystem.getINode(dirEntry.getINumber());
                        entries.add(new XfsEntry(childInode, dirEntry.getName(), entries.size(), fileSystem, this));

                        offset += dirEntry.getNextEntryOffset();

                    }
                } else {
                    ShortFormDirectoryEntry dirEntry = new ShortFormDirectoryEntry(inode.getData(), offset, recordSize, fileSystem.isV5());
                    if (dirEntry.getINumber() == 0) {
                        break;
                    }

                    INode childInode = fileSystem.getINode(dirEntry.getINumber());
                    entries.add(new XfsEntry(childInode, dirEntry.getName(), entries.size(), fileSystem, this));
                }
                break;

            case EXTENTS:
                if (!inode.isDirectory()) {
                    throw new UnsupportedOperationException("Trying to get directories of a non directory inode");
                }
                final List<DataExtent> extents = inode.getExtentInfo();

                if (extents.size() == 1) {
                    // Block Directory
                    if (logger.isDebugEnabled()) {
                        logger.debug("Processing a Block directory, Inode Number: {}", entry.getINode().getINodeNumber());
                    }
                    final DataExtent extentInformation = extents.get(0);
                    final long extOffset = extentInformation.getExtentOffset(fileSystem);
                    ByteBuffer buffer = ByteBuffer.allocate((int) fileSystem.getSuperblock().getBlockSize() * (int) extentInformation.getBlockCount());
                    try {
                        fileSystem.getFSApi().read(extOffset, buffer);
                    } catch (ApiNotFoundException e) {
                        logger.warn("Failed to read directory entries at offset: " + extOffset, e);
                    }
                    final BlockDirectory myBlockDirectory = new BlockDirectory(buffer.array(), 0, fileSystem);
                    entries = myBlockDirectory.getEntries(this);
                } else {

                    long leafExtentIndex = LeafDirectory.getLeafExtentIndex(extents, fileSystem);
                    long iNodeNumber = entry.getINode().getINodeNumber();

                    if (leafExtentIndex == -1) {
                        throw new IOException("Cannot compute leaf extent for inode " + iNodeNumber);
                    }
                    final DataExtent extentInformation = extents.get((int) leafExtentIndex);
                    final long extOffset = extentInformation.getExtentOffset(fileSystem);
                    ByteBuffer buffer = ByteBuffer.allocate((int) fileSystem.getSuperblock().getBlockSize() * (int) extentInformation.getBlockCount());
                    try {
                        fileSystem.getFSApi().read(extOffset, buffer);
                    } catch (ApiNotFoundException e) {
                        logger.warn("Failed to read directory entries at offset: " + extOffset, e);
                    }

                    if (leafExtentIndex == (extents.size() - 1)) {
                        // Leaf Directory
                        if (logger.isDebugEnabled()) {
                            logger.debug("Processing a Leaf directory, Inode Number: {}", iNodeNumber);
                        }
                        final LeafDirectory myLeafDirectory = new LeafDirectory(buffer.array(), 0, fileSystem, iNodeNumber, extents);
                        entries = myLeafDirectory.getEntries(this);
                    } else {
                        // Node Directory
                        if (logger.isDebugEnabled()) {
                            logger.debug("Processing a Node directory, Inode Number: {}", iNodeNumber);
                        }
                        final NodeDirectory myNodeDirectory = new NodeDirectory(buffer.array(), 0, fileSystem, iNodeNumber, extents, leafExtentIndex);
                        entries = myNodeDirectory.getEntries(this);
                    }
                }
                break;

            case BTREE:
                long iNodeNumber = entry.getINode().getINodeNumber();
                if (logger.isDebugEnabled()) {
                    logger.debug("Processing a B+tree directory, Inode Number: {}", iNodeNumber);
                }
                BPlusTreeDirectory myBPlusTreeDirectory = new BPlusTreeDirectory(entry.getINode().getData(), 0, iNodeNumber, fileSystem);
                entries = myBPlusTreeDirectory.getEntries(this);
                break;

            default:
                throw new IllegalStateException("Unexpected format: " + inode.getRawFormat());
        }

        return new FSEntryTable(fileSystem, entries);
    }

    @Override
    protected void writeEntries(FSEntryTable entries) {
        throw new UnsupportedOperationException(XfsConstants.XFS_IS_READ_ONLY);
    }

    @Override
    protected FSEntry createFileEntry(String name) {
        throw new UnsupportedOperationException(XfsConstants.XFS_IS_READ_ONLY);
    }

    @Override
    protected FSEntry createDirectoryEntry(String name) {
        throw new UnsupportedOperationException(XfsConstants.XFS_IS_READ_ONLY);
    }
}
