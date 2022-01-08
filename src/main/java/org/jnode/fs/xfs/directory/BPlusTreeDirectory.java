package org.jnode.fs.xfs.directory;

import org.jnode.driver.ApiNotFoundException;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.extent.BPlusTreeDataExtent;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A XFS B+tree directory.
 *
 * B+tree Directories
 * When the extent map in an inode grows beyond the inode’s space,
 * the inode format is changed to a “btree”.
 *
 * @author
 */
public class BPlusTreeDirectory extends XfsObject {

    /**
     * The level value determines if the node is an intermediate node or a leaf.
     */
    private final int level;

    /**
     *  The size of arrays (xfs_bmbt_key_t values and xfs_bmbt_ptr_t)
     */
    private final int numrecs;

    /**
     * The inode b+tree.
     */
    private INode inode;

    /**
     * The filesystem.
     */
    private XfsFileSystem fileSystem;

    /**
     * The inode number.
     */
    private long iNodeNumber;

    /**
     * Creates a b+tree directory.
     *
     * @param data of the inode.
     * @param offset of the inode's data
     * @param iNodeNumber of the inode
     * @param fileSystem of the image
     * @throws IOException if an error occurs reading in the super block.
     */
    public BPlusTreeDirectory(byte [] data, long offset, long iNodeNumber, XfsFileSystem fileSystem) throws IOException {
        super(data,(int) offset);
        this.fileSystem = fileSystem;
        this.iNodeNumber = iNodeNumber;
        this.inode =   fileSystem.getINode(iNodeNumber);
        long btreeInfoOffset = inode.getOffset() + fileSystem.getINode(iNodeNumber).getINodeSizeForOffset();
        this.level = (int) read(btreeInfoOffset, 2);
        this.numrecs = (int) read(btreeInfoOffset + 2, 2);
        if (level > 1) {
            System.out.println("## Inode " + inode.getINodeNr() + " has (numrec,level) (" + numrecs + "," + level + ")" );
        }
    }

    /**
     * Gets all the entries of the current b+tree directory.
     *
     * @param parentDirectory of the inode.
     * @throws IOException if an error occurs reading in the super block.
     *
     * Note :  When level > 1 this won't work need an example with more than 1 level to introduce recursively
     */
    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        final long forkOffset = inode.getAttributesForkOffset() * 8;
        long btreeBlockOffset = (inode.getOffset() + inode.getINodeSizeForOffset() + (forkOffset/2));
        List<BPlusTreeDataExtent> extentListsList = new ArrayList<>(numrecs);
        List<FSEntry> entries = new ArrayList<>();

        for (int i = 0; i < numrecs; i++) {
            final long fsBlockNo = read(btreeBlockOffset, 4);
            btreeBlockOffset += 4;
            final long offset = DataExtent.getFileSystemBlockOffset(fsBlockNo, fileSystem);
            ByteBuffer buffer = ByteBuffer.allocate(fileSystem.getSuperblock().getBlockSize());
            try {
                fileSystem.getFSApi().read(offset, buffer);
            } catch (ApiNotFoundException e) {
                e.printStackTrace();
            }
            final BPlusTreeDataExtent extentList = new BPlusTreeDataExtent(buffer.array(), 0);
            extentListsList.add(extentList);
        }

        for (BPlusTreeDataExtent bPlusTreeExtentList : extentListsList) {
            final List<DataExtent> extents = bPlusTreeExtentList.getExtents();
            final long leafExtentIndex = LeafDirectory.getLeafExtentIndex(extents, fileSystem);
            final DataExtent extentInformation = extents.get((int) leafExtentIndex);
            final long extOffset = extentInformation.getExtentOffset(fileSystem);
            ByteBuffer buffer = ByteBuffer.allocate(fileSystem.getSuperblock().getBlockSize() * (int) extentInformation.getBlockCount());
            try {
                fileSystem.getFSApi().read(extOffset, buffer);
            } catch (ApiNotFoundException e) {
                e.printStackTrace();
            }
            final NodeDirectory leafDirectory = new NodeDirectory(buffer.array(), 0, fileSystem, this.iNodeNumber, extents, leafExtentIndex);
            entries = leafDirectory.getEntries(parentDirectory);
        }
        return entries;
    }

    /**
     * Validate the magic key data
     *
     * @return a list of valid magic signatures
     */
    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }
}
