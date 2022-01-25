package org.jnode.fs.xfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntryCreated;
import org.jnode.fs.FSEntryLastAccessed;
import org.jnode.fs.FSEntryLastChanged;
import org.jnode.fs.spi.AbstractFSEntry;
import org.jnode.fs.util.UnixFSConstants;
import org.jnode.fs.xfs.directory.BlockDirectoryEntry;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An entry in a XFS file system.
 *
 * @author Luke Quinane
 */
public class XfsEntry extends AbstractFSEntry implements FSEntryCreated, FSEntryLastAccessed, FSEntryLastChanged {


    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(BlockDirectoryEntry.class);

    /**
     * Constant used to convert nano seconds to milliseconds.
     */
    private static final long ONE_MILLISECOND = 1_000_000;
    /**
     * The inode.
     */
    private final INode inode;

    /**
     * The directory record ID.
     */
    private final long directoryRecordId;

    /**
     * The file system.
     */
    private final XfsFileSystem fileSystem;

    /**
     * The list of extents when the data format is 'XFS_DINODE_FMT_EXTENTS'.
     */
    private List<DataExtent> extentList;

    /**
     * Creates a new entry.
     *
     * @param inode the inode.
     * @param name the name.
     * @param directoryRecordId the directory record ID.
     * @param fileSystem the file system.
     * @param parent the parent.
     */
    public XfsEntry(INode inode, String name, long directoryRecordId, XfsFileSystem fileSystem, FSDirectory parent) {
        super(fileSystem, null, parent, name, getFSEntryType(name, inode));

        this.inode = inode;
        this.directoryRecordId = directoryRecordId;
        this.fileSystem = fileSystem;
    }

    @Override
    public String getId() {
        return Long.toString(inode.getINodeNr()) + '-' + directoryRecordId;
    }

    @Override
    public long getCreated() throws IOException {
        return (inode.getCreatedTimeSec() * 1000) + (inode.getCreatedTimeNsec() / ONE_MILLISECOND);
    }

    @Override
    public long getLastAccessed() throws IOException {
        return (inode.getAccessTimeSec() * 1000) + (inode.getAccessTimeNsec() / ONE_MILLISECOND);
    }

    @Override
    public long getLastChanged() throws IOException {
        return (inode.getChangedTimeSec() * 1000) + (inode.getChangedTimeNsec() / ONE_MILLISECOND);
    }

    /**
     * Gets the inode.
     *
     * @return the inode.
     */
    public INode getINode() {
        return inode;
    }

    /**
     * Reads from this entry's data.
     *
     * @param offset the offset to read from.
     * @param destBuf the destination buffer.
     * @throws IOException if an error occurs reading.
     */
    public void read(long offset, ByteBuffer destBuf) throws IOException {
        if (offset + destBuf.remaining() > inode.getSize()) {
            throw new IOException("Reading past the end of the entry. Offset: " + offset + " entry: " + this);
        }

        readUnchecked(offset, destBuf);
    }

    /**
     * A read implementation that doesn't check the file length.
     *
     * @param offset the offset to read from.
     * @param destBuf the destination buffer.
     * @throws IOException if an error occurs reading.
     */
    public void readUnchecked(long offset, ByteBuffer destBuf) throws IOException {
        switch (inode.getFormat()) {
            case XfsConstants.XFS_DINODE_FMT_LOCAL:
                if(getINode().isSymLink()) {
                    ByteBuffer buffer = StandardCharsets.UTF_8.encode(getINode().getSymLinkText());
                    destBuf.put(buffer);
                } else {
                    throw new UnsupportedOperationException();
                }
            case XfsConstants.XFS_DINODE_FMT_EXTENTS:
                if (extentList == null) {
                    extentList = new ArrayList<>((int)inode.getExtentCount());

                    for (int i = 0; i < inode.getExtentCount(); i++) {
                        int inodeOffset = inode.getVersion() >= INode.V3 ? INode.V3_DATA_OFFSET : INode.DATA_OFFSET;
                        int extentOffset = inodeOffset + i * DataExtent.PACKED_LENGTH;
                        DataExtent extent = new DataExtent(inode.getData(), extentOffset);
                        extentList.add(extent);
                    }
                }
                readFromExtentList(offset, destBuf);
                return;

            case XfsConstants.XFS_DINODE_FMT_BTREE:
                throw new UnsupportedOperationException();

            default:
                throw new IllegalStateException("Unexpected format: " + inode.getFormat());
        }
    }

    /**
     * Reads from the entry's extent list.
     *
     * @param offset the offset to read from.
     * @param destBuf the destination buffer.
     * @throws IOException if an error occurs reading.
     */
    private void readFromExtentList(long offset, ByteBuffer destBuf) throws IOException {
        long blockSize = fileSystem.getSuperblock().getBlockSize();
        long extentOffset = 0;

        for (DataExtent extent : extentList) {
            if (!destBuf.hasRemaining()) {
                return;
            }

            if (extent.isWithinExtent(offset, blockSize)) {
                ByteBuffer readBuffer = destBuf.duplicate();

                long offsetWithinBlock = offset - extentOffset;
                int bytesToRead = (int) Math.min(extent.getBlockCount() * blockSize - offsetWithinBlock, destBuf.remaining());
                readBuffer.limit(readBuffer.position() + bytesToRead);
                fileSystem.getApi().read(extent.getFileSystemBlockOffset(fileSystem) + offsetWithinBlock, readBuffer);

                offset += bytesToRead;
                destBuf.position(destBuf.position() + bytesToRead);
            }

            long extentLength = extent.getBlockCount() * blockSize;
            extentOffset += extentLength;
        }
    }

    @Override
    public String toString() {
        return "xfs-entry:[" + getName() + "] " + inode;
    }

    private static int getFSEntryType(String name, INode inode) {
        int mode = inode.getMode() & UnixFSConstants.S_IFMT;

        if ("/".equals(name))
            return AbstractFSEntry.ROOT_ENTRY;
        else if (mode == UnixFSConstants.S_IFDIR)
            return AbstractFSEntry.DIR_ENTRY;
        else if (mode == UnixFSConstants.S_IFREG || mode == UnixFSConstants.S_IFLNK ||
            mode == UnixFSConstants.S_IFIFO || mode == UnixFSConstants.S_IFCHR ||
            mode == UnixFSConstants.S_IFBLK)
            return AbstractFSEntry.FILE_ENTRY;
        else
            return AbstractFSEntry.OTHER_ENTRY;
    }
}
