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

public class BPlusTreeDirectory extends XfsObject {

    private final int level;
    private final int numrecs;
    private INode inode;
    private XfsFileSystem fileSystem;
    private long iNodeNumber;

    public BPlusTreeDirectory(byte [] data, long offset, long iNodeNumber, XfsFileSystem fileSystem) throws IOException {
        super(data,(int)offset);
        this.fileSystem = fileSystem;
        this.iNodeNumber = iNodeNumber;
        this.inode =   fileSystem.getINode(iNodeNumber);
        long btreeInfoOffset = inode.getOffset() + fileSystem.getINode(iNodeNumber).getINodeSizeForOffset();
        this.level = (int)read(btreeInfoOffset, 2);
        this.numrecs = (int)read(btreeInfoOffset + 2, 2);
        if (level > 1){
            System.out.println("## Inode " + inode.getINodeNr() + " has (numrec,level) (" + numrecs + "," + level + ")" );
        }
    }

    // when level > 1 this wont work need an example with more than 1 level to introduce recursivity
    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        final long forkOffset = inode.getAttributesForkOffset() * 8;
        long btreeBlockOffset = (inode.getOffset() + inode.getINodeSizeForOffset() + (forkOffset/2));
        List<BPlusTreeDataExtent> extentListsList = new ArrayList<>(numrecs);
        List<FSEntry> entries = new ArrayList<>();

        for (int i = 0; i < numrecs; i++) {
            final long fsBlockNo = read(btreeBlockOffset,4);
            btreeBlockOffset += 4;
            final long offset = DataExtent.getFileSystemBlockOffset(fsBlockNo, fileSystem);
            ByteBuffer buffer = ByteBuffer.allocate(fileSystem.getSuperblock().getBlockSize());
            try {
                fileSystem.getFSApi().read(offset,buffer);
            } catch (ApiNotFoundException e) {
                e.printStackTrace();
            }
            final BPlusTreeDataExtent extentList = new BPlusTreeDataExtent(buffer.array(), 0);
            extentListsList.add(extentList);
        }

        for (BPlusTreeDataExtent bPlusTreeExtentList : extentListsList) {
            final List<DataExtent> extents = bPlusTreeExtentList.getExtents();
            final long leafExtentIndex = LeafDirectory.getLeafExtentIndex(extents, fileSystem);
            final DataExtent extentInformation = extents.get((int)leafExtentIndex);
            final long extOffset = extentInformation.getExtentOffset(fileSystem);
            ByteBuffer buffer = ByteBuffer.allocate(fileSystem.getSuperblock().getBlockSize() * (int) extentInformation.getBlockCount());
            try {
                fileSystem.getFSApi().read(extOffset,buffer);
            } catch (ApiNotFoundException e) {
                e.printStackTrace();
            }
            final NodeDirectory leafDirectory = new NodeDirectory(buffer.array(), 0, fileSystem, this.iNodeNumber, extents, leafExtentIndex);
            entries = leafDirectory.getEntries(parentDirectory);
        }
        return entries;
    }

    @Override
    protected List<Long> validSignatures() { return Collections.singletonList(0L); }
}
