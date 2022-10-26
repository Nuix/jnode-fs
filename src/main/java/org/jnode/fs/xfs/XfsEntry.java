package org.jnode.fs.xfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntryLastAccessed;
import org.jnode.fs.FSEntryLastChanged;
import org.jnode.fs.spi.AbstractFSEntry;
import org.jnode.fs.util.UnixFSConstants;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;

/**
 * <p>An entry in a XFS file system.</p>
 *
 * @author Luke Quinane
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class XfsEntry extends AbstractFSEntry implements FSEntryLastAccessed, FSEntryLastChanged {

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
     * @param inode             the inode.
     * @param name              the name.
     * @param directoryRecordId the directory record ID.
     * @param fileSystem        the file system.
     * @param parent            the parent.
     */
    public XfsEntry(INode inode, String name, long directoryRecordId, XfsFileSystem fileSystem, FSDirectory parent) {
        super(fileSystem, null, parent, name, getFSEntryType(name, inode));

        this.inode = inode;
        this.directoryRecordId = directoryRecordId;
        this.fileSystem = fileSystem;
    }

    private static int getFSEntryType(String name, INode inode) {
        int mode = inode.getMode() & UnixFSConstants.S_IFMT;

        if ("/".equals(name))
            return AbstractFSEntry.ROOT_ENTRY;
        else if (mode == UnixFSConstants.S_IFDIR)
            return AbstractFSEntry.DIR_ENTRY;
        else if (mode == UnixFSConstants.S_IFREG || mode == UnixFSConstants.S_IFLNK ||
                mode == UnixFSConstants.S_IFIFO || mode == UnixFSConstants.S_IFCHR ||
                mode == UnixFSConstants.S_IFBLK || mode == UnixFSConstants.S_IFSOCK)
            return AbstractFSEntry.FILE_ENTRY;
        else
            return AbstractFSEntry.OTHER_ENTRY;
    }

    @Override
    public String getId() {
        return Long.toString(inode.getINodeNumber()) + '-' + directoryRecordId;
    }

    /**
     * Converts an entry's time value from the seconds/nanoseconds values to a millisecond
     * value, usable by {@link #getLastAccessed()}, and {@link #getLastChanged()}.
     *
     * @param seconds     the seconds value from the entry.
     * @param nanoseconds the nanoseconds value from the entry.
     * @return the milliseconds value.
     * @see #getLastAccessed()
     * @see #getLastChanged()
     */
    public static long getMilliseconds(long seconds, long nanoseconds) {
        return (seconds * 1_000) + (nanoseconds / 1_000_000);
    }

    @Override
    public long getLastAccessed() {
        return getMilliseconds(inode.getAccessTimeSec(), inode.getAccessTimeNsec());
    }

    @Override
    public long getLastChanged() {
        return getMilliseconds(inode.getModifiedTimeSec(), inode.getModifiedTimeNsec());
    }

    /**
     * Gets the last inode change time.
     *
     * @return the last inode change time.
     */
    public long getInodeLastChanged() {
        return getMilliseconds(inode.getInodeChangeTimeSec(), inode.getInodeChangeTimeNsec());
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
     * @param offset  the offset to read from.
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
     * @param offset  the offset to read from.
     * @param destBuf the destination buffer.
     * @throws IOException if an error occurs reading.
     */
    public void readUnchecked(long offset, ByteBuffer destBuf) throws IOException {
        switch (inode.getFormat()) {
            case LOCAL:
                if (getINode().isSymLink()) {
                    ByteBuffer buffer = StandardCharsets.UTF_8.encode(getINode().getSymLinkText());
                    destBuf.put(buffer);
                } else {
                    throw new UnsupportedOperationException();
                }
                break;

            case EXTENTS:
                if (extentList == null) {
                    extentList = new ArrayList<>((int) inode.getExtentCount());

                    for (int i = 0; i < inode.getExtentCount(); i++) {
                        int inodeDataOffset = inode.getDataOffset();
                        int extentOffset = inodeDataOffset + i * DataExtent.PACKED_LENGTH;
                        DataExtent extent = new DataExtent(inode.getData(), extentOffset);
                        extentList.add(extent);
                    }
                }
                readFromExtentList(offset, destBuf);
                break;

            case BTREE:
                throw new UnsupportedOperationException("Unsupported B+tree format for inode " + inode.getINodeNumber());

            default:
                throw new IllegalStateException("Unexpected format: " + inode.getRawFormat());
        }
    }

    /**
     * Reads from the entry's extent list.
     *
     * @param offset  the offset to read from.
     * @param destBuf the destination buffer.
     * @throws IOException if an error occurs reading.
     */
    private void readFromExtentList(long offset, ByteBuffer destBuf) throws IOException {
        long blockSize = fileSystem.getSuperblock().getBlockSize();
        int bytesToRead;

        for (DataExtent extent : extentList) {
            if (!destBuf.hasRemaining()) {
                return;
            }

            if (extent.isWithinExtent(offset, blockSize)) {
                ByteBuffer readBuffer = destBuf.duplicate();

                long extentOffset = extent.getStartOffset() * blockSize;
                long offsetWithinBlock = offset - extentOffset;
                if ((extent.getBlockCount() * blockSize - offsetWithinBlock) > 0) {
                    bytesToRead = (int) Math.min(extent.getBlockCount() * blockSize - offsetWithinBlock, destBuf.remaining());
                } else {
                    bytesToRead = destBuf.remaining();
                }

                readBuffer.limit(readBuffer.position() + bytesToRead);
                fileSystem.getApi().read(extent.getFileSystemBlockOffset(fileSystem) + offsetWithinBlock, readBuffer);

                offset += bytesToRead;
                destBuf.position(destBuf.position() + bytesToRead);
            }
        }
    }

    @Override
    public String toString() {
        return "xfs-entry:[" + getName() + "] " + inode;
    }

    @Override
    public List<FSAttribute> getAttributes() throws IOException {
        return inode.getAttributes();
    }
}
