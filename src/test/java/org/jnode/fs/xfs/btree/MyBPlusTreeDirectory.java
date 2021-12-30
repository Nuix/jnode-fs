package org.jnode.fs.xfs.btree;

import org.jnode.fs.xfs.*;
import org.jnode.util.BigEndian;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class MyBPlusTreeDirectory {

    private final int level;
    private final int numrecs;
    private MyInode inode;

    public MyBPlusTreeDirectory(MyInode inode) throws IOException {
        this.inode = inode;
        long btreeInfoOffset = inode.getOffset() + inode.getINodeSizeForOffset();
        ByteBuffer buffer = ByteBuffer.allocate(4);
        inode.getDevApi().read(btreeInfoOffset,buffer);
        this.level = BigEndian.getUInt16(buffer.array(),0);
        this.numrecs = BigEndian.getUInt16(buffer.array(),2);
        if (level > 1){
            System.out.println("## Inode " + inode.getINodeNumber() + " has (numrec,level) (" + numrecs + "," + level + ")" );
        }
    }

    // when level > 1 this wont work need an example with more than 1 level to introduce recursivity
    public List<? extends IMyDirectory> getEntries() throws IOException {
        final long forkOffset = inode.getAttributesForkOffset() * 8;
        long btreeBlockOffset = (inode.getOffset() + inode.getINodeSizeForOffset() + (forkOffset/2));
        List<MyBPlusTreeExtentList> extentListsList = new ArrayList<>(numrecs);
        final ByteBuffer buffer = ByteBuffer.allocate(4);
        for (int i = 0; i < numrecs; i++) {
            inode.getDevApi().read(btreeBlockOffset,buffer);
            final long fsBlockNo = BigEndian.getUInt32(buffer.array(), 0);
            btreeBlockOffset += 4;
            final long offset = MyExtentInformation.calcFsBlockOffset(fsBlockNo, inode.getFs());
            final MyBPlusTreeExtentList extentList = new MyBPlusTreeExtentList(inode.getDevApi(), offset, inode.getFs());
            extentListsList.add(extentList);
        }

        List<List<MyBlockDirectoryEntry>> entriesCollection = new ArrayList<>(1000);
        for (MyBPlusTreeExtentList bPlusTreeExtentList : extentListsList) {
            final List<MyExtentInformation> extents = bPlusTreeExtentList.getExtents();
            final long leafExtentIndex = MyLeafDirectory.getLeafExtentIndex(extents, inode.getFs());
            final MyNodeDirectory leafDirectory = new MyNodeDirectory(inode.getDevApi(), inode.getFs(), inode.getINodeNumber(), extents,leafExtentIndex);
            final List<MyBlockDirectoryEntry> entries = leafDirectory.getEntries();
            entriesCollection.add(entries);
        }


        return entriesCollection.stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

}
