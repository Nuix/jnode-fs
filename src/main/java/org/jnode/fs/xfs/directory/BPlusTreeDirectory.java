package org.jnode.fs.xfs.directory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.jnode.driver.ApiNotFoundException;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsConstants;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.common.LongFormBPlusTreeData;
import org.jnode.fs.xfs.common.LongFormBPlusTreeDataV3;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;
import org.jnode.util.BigEndian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>An XFS B+tree directory.</p>
 *
 * <p>When the extent map in an inode grows beyond the inode’s space,
 * the inode format is changed to a “B+tree”.</p>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
@Slf4j
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
        long leafExtentIndex = getLeafExtentIndex(extents);
        DataExtent extentInformation = extents.get((int) leafExtentIndex);
        long extOffset = extentInformation.getExtentOffset(fileSystem);
        ByteBuffer buffer = ByteBuffer.allocate((int) fileSystem.getSuperblock().getBlockSize() * (int) extentInformation.getBlockCount());
        try {
            fileSystem.getFSApi().read(extOffset, buffer);
        } catch (ApiNotFoundException e) {
            log.warn("Failed to read node directory at offset: " + extOffset, e);
        }

        // TODO, it seems that, theoretically, we need to use LeafDirectory here,
        //  but there is no difference between LeafDirectory and NodeDirectory regarding the getEntries() for now.
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
            // The doc says:
            // "The bb_level value determines if the node is an intermediate node or a leaf. Leaves have a bb_level of zero,
            // nodes are one or greater"
            //
            // So, if level > 1, it is a node that has extents in the sub node, so we flatten the sub nodes.
            // if level == 1, it is a node that doesn't have sub node, but has extent leaf, so we add the leaves.
            // if level == 0, it is a leaf with no extents.
            if (level > 1) {
                return getFlattenedExtents(buffer.array(), level - 1, currentExtents, 0, 0);
            } else if (level == 1) {
                LongFormBPlusTreeData extentList = fileSystem.isV5() ?
                        new LongFormBPlusTreeDataV3(buffer.array(), 0) :
                        new LongFormBPlusTreeData(buffer.array(), 0);
                currentExtents.addAll(extentList.getExtents());
            } else {
                logger.error("Do not extent leaf as it has been in the lowest level {}.", level);
            }
        }
        return currentExtents;
    }

    /**
     * Gets the extent index of the leaf.
     *
     * @return the index of the leaf block, or {@code -1} if not fount.
     */
    private int getLeafExtentIndex(List<DataExtent> extents) {
        //The “leaf” block has a special offset defined by XFS_DIR2_LEAF_OFFSET. Currently, this is 32GB and in the
        //extent view, a block offset of 32GB / sb_blocksize. On a 4KB block filesystem, this is 0x800000 (8388608
        //decimal).
        long leafOffset = XfsConstants.BYTES_IN_32G / fileSystem.getSuperblock().getBlockSize();

        for (int i = 0; i < extents.size(); i++) {
            if (extents.get(i).getStartOffset() == leafOffset) {
                return i;
            }
        }
        return -1;
    }
}
