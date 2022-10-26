package org.jnode.fs.xfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jnode.fs.FSDirectoryId;
import org.jnode.fs.FSEntry;
import org.jnode.fs.spi.AbstractFSDirectory;
import org.jnode.fs.spi.FSEntryTable;
import org.jnode.fs.xfs.directory.BPlusTreeDirectory;
import org.jnode.fs.xfs.directory.BlockDirectory;
import org.jnode.fs.xfs.directory.LeafDirectory;
import org.jnode.fs.xfs.directory.NodeDirectory;
import org.jnode.fs.xfs.directory.ShortFormDirectoryEntry;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;

/**
 * A XFS directory.
 *
 * @author Luke Quinane.
 * @author Ricardo Garza
 * @author Julio Parra
 */
@Slf4j
public class XfsDirectory extends AbstractFSDirectory implements FSDirectoryId {

    /**
     * The related entry.
     */
    @Nonnull
    @Getter
    private final XfsEntry entry;

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
    }

    @Override
    public String getDirectoryId() {
        return Long.toString(entry.getINode().getINodeNumber());
    }

    @Override
    public FSEntry getEntryById(String id) {
        checkEntriesLoaded();
        return getEntryTable().getById(id);
    }

    @Override
    protected FSEntryTable readEntries() throws IOException {
        List<FSEntry> entries;

        INode inode = entry.getINode();
        switch (inode.getFormat()) {
            case LOCAL:
                entries = getLocalEntries(inode);
                break;

            case EXTENTS:
                entries = getExtentsEntries(inode);
                break;

            case BTREE:
                entries = getBtreeEntries(inode);
                break;

            default:
                throw new IllegalStateException("Unexpected format: " + inode.getRawFormat());
        }

        return new FSEntryTable(fileSystem, entries);
    }

    private List<FSEntry> getLocalEntries(INode inode) throws IOException {
        //  typedef struct xfs_dir2_sf {
        //    xfs_dir2_sf_hdr_t hdr;
        //    xfs_dir2_sf_entry_t list[1];
        //  } xfs_dir2_sf_t;

        List<FSEntry> entries = new ArrayList<>();

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
                entries.add(new XfsEntry(parent.getEntry().getINode(), "..", 1, fileSystem, parent.entry.getParent()));
            }

            while (entryCount > 0) {
                ShortFormDirectoryEntry dirEntry = new ShortFormDirectoryEntry(inode.getData(), offset, recordSize, fileSystem.isV5());

                if (dirEntry.getINumber() == 0) {
                    break;
                }

                INode childInode = fileSystem.getINode(dirEntry.getINumber());
                entries.add(new XfsEntry(childInode, dirEntry.getName(), entries.size(), fileSystem, this));

                offset += dirEntry.getNextEntryOffset();
                entryCount--;
            }
        } else {
            ShortFormDirectoryEntry dirEntry = new ShortFormDirectoryEntry(inode.getData(), offset, recordSize, fileSystem.isV5());
            if (dirEntry.getINumber() != 0) {
                INode childInode = fileSystem.getINode(dirEntry.getINumber());
                entries.add(new XfsEntry(childInode, dirEntry.getName(), entries.size(), fileSystem, this));
            }
        }

        return entries;
    }

    private List<FSEntry> getExtentsEntries(INode inode) throws IOException {
        List<FSEntry> entries;
        if (!inode.isDirectory()) {
            throw new UnsupportedOperationException("Trying to get directories of a non directory inode (node mode " + inode.getMode() + ")");
        }
        INode.ExtentInfo extentInfo = inode.getExtentInfo();
        List<DataExtent> extents = extentInfo.getExtents();

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
            } catch (Exception e) {
                logger.warn("Failed to read directory entries at offset: {}", extOffset, e);
            }
            final BlockDirectory myBlockDirectory = new BlockDirectory(buffer.array(), 0, fileSystem);
            entries = myBlockDirectory.getEntries(this);
        } else {

            long leafExtentIndex = extentInfo.getLeafExtentIndex();
            long iNodeNumber = entry.getINode().getINodeNumber();

            if (leafExtentIndex == -1) {
                throw new IOException("Cannot compute leaf extent for inode " + iNodeNumber);
            }
            final DataExtent extentInformation = extents.get((int) leafExtentIndex);
            final long extOffset = extentInformation.getExtentOffset(fileSystem);
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSystem.getSuperblock().getBlockSize() * extentInformation.getBlockCount());
            try {
                fileSystem.getFSApi().read(extOffset, buffer);
            } catch (Exception e) {
                logger.warn("Failed to read directory entries at offset: {}", extOffset, e);
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
        return entries;
    }

    private List<FSEntry> getBtreeEntries(INode inode) throws IOException {
        List<FSEntry> entries;
        long iNodeNumber = inode.getINodeNumber();
        if (logger.isDebugEnabled()) {
            logger.debug("Processing a B+tree directory, Inode Number: {}", iNodeNumber);
        }
        BPlusTreeDirectory myBPlusTreeDirectory = new BPlusTreeDirectory(inode.getData(), 0, iNodeNumber, fileSystem);
        entries = myBPlusTreeDirectory.getEntries(this);
        return entries;
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
