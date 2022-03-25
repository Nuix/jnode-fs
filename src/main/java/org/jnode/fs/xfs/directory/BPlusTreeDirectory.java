package org.jnode.fs.xfs.directory;

import org.jnode.driver.ApiNotFoundException;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.extent.BPlusTreeDataExtent;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;
import org.jnode.util.BigEndian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>An XFS B+tree directory.</p>
 *
 * <p>When the extent map in an inode grows beyond the inode’s space,
 * the inode format is changed to a “B+tree”.</p>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class BPlusTreeDirectory extends XfsObject {

    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(BPlusTreeDirectory.class);

    /**
     * The magic number for a v5 block map B+tree block (bmbt) - "BMA3".
     */
    private static final long BTREE_EXTENT_LIST_MAGIC_V5 = asciiToHex("BMA3");

    /**
     * The magic number for a block map B+tree block (bmbt) - "BMAP".
     */
    private static final long BTREE_EXTENT_LIST_MAGIC = asciiToHex("BMAP");

    /**
     * The inode b+tree.
     */
    private final INode inode;

    /**
     * The filesystem.
     */
    private final XfsFileSystem fileSystem;

    /**
     * The inode number.
     */
    private final long iNodeNumber;

    /**
     * Creates a b+tree directory.
     *
     * @param data        of the inode.
     * @param offset      of the inode's data
     * @param iNodeNumber of the inode
     * @param fileSystem  of the image
     * @throws IOException if an error occurs reading in the super block.
     */
    public BPlusTreeDirectory(byte[] data, long offset, long iNodeNumber, XfsFileSystem fileSystem) throws IOException {
        super(data, (int) offset);
        this.fileSystem = fileSystem;
        this.iNodeNumber = iNodeNumber;
        this.inode = fileSystem.getINode(iNodeNumber);
    }

    /**
     * Gets all the entries of the current b+tree directory.
     * Note: When level &gt; 1 this won't work.
     * Need an example with more than 1 level to introduce recursively.
     * TODO: determine if this needs a test based on note.
     *
     * @param parentDirectory of the inode.
     * @return a {@link List} of {@link FSEntry}.
     * @throws IOException if an error occurs reading in the super block.
     */
    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        int btreeInfoOffset = inode.getOffset() + inode.getDataOffset();
        int level = getUInt16(btreeInfoOffset);
        int numrecs = getUInt16(btreeInfoOffset + 2);
        long forkOffset = inode.getAttributesForkOffset() * 8;
        if (forkOffset == 0) {
            forkOffset = fileSystem.getSuperblock().getInodeSize() - inode.getDataOffset();
        }
        int btreeBlockOffset = (int) (inode.getOffset() + inode.getDataOffset() + (forkOffset / 2));
        // 8 byte alignment. not sure if it should be a 16 byte alignment?
        btreeBlockOffset = btreeBlockOffset + (btreeBlockOffset % 8);
        List<DataExtent> extents = getFlattenedExtents(getData(), level, new ArrayList<>(200), btreeBlockOffset, numrecs);
        long leafExtentIndex = LeafDirectory.getLeafExtentIndex(extents, fileSystem);
        DataExtent extentInformation = extents.get((int) leafExtentIndex);
        long extOffset = extentInformation.getExtentOffset(fileSystem);
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSystem.getSuperblock().getBlockSize() * (int) extentInformation.getBlockCount());
        try {
            fileSystem.getFSApi().read(extOffset, buffer);
        } catch (ApiNotFoundException e) {
            log.warn("Failed to read node directory at offset: " + extOffset, e);
        }
        NodeDirectory leafDirectory = new NodeDirectory(buffer.array(), 0, fileSystem, iNodeNumber, extents, leafExtentIndex);
        return leafDirectory.getEntries(parentDirectory);
    }

    public List<DataExtent> getFlattenedExtents(byte[] data, int level, List<DataExtent> currentExtents, int btreeBlockOffset, int numrecs) throws IOException {
        boolean isBtreeExtentList = false;
        if (btreeBlockOffset == 0) {
            numrecs = BigEndian.getUInt16(data, 6);
            long signature = BigEndian.getUInt32(data, 0);
            isBtreeExtentList = signature == BTREE_EXTENT_LIST_MAGIC_V5 || signature == BTREE_EXTENT_LIST_MAGIC;
            int filesystemOffset = fileSystem.isV5() ? 64 : 16;
            btreeBlockOffset = (int) (fileSystem.getSuperblock().getBlockSize() + filesystemOffset) / 2;
        }
        for (int i = 0; i < numrecs; i++) {
            long fsBlockNo = isBtreeExtentList ? BigEndian.getInt64(data, btreeBlockOffset) : BigEndian.getUInt32(data, btreeBlockOffset);
            // 8 byte alignment
            btreeBlockOffset += 0x8;
            long offset = DataExtent.getFileSystemBlockOffset(fsBlockNo, fileSystem);
            ByteBuffer buffer = ByteBuffer.allocate((int) fileSystem.getSuperblock().getBlockSize());
            try {
                fileSystem.getFSApi().read(offset, buffer);
            } catch (ApiNotFoundException e) {
                log.warn("Failed to read FS entry list at offset: " + offset, e);
            }
            if (level > 1) {
                return getFlattenedExtents(buffer.array(), level - 1, currentExtents, 0, 0);
            } else {
                BPlusTreeDataExtent extentList = new BPlusTreeDataExtent(buffer.array(), 0, fileSystem.isV5());
                currentExtents.addAll(extentList.getExtents());
            }
        }
        return currentExtents;
    }
}
