package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.util.BigEndian;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MyXfsFileSystem {
    private final long blockSize;
    FSBlockDeviceAPI device;
    final MySuperblock mainSuperBlock;
    final long allocationGroupSize;
    final int sectorSize;
    final int aGCount;
    final long aGSizeLog2;
    final int xfsVersion;
    final int iNodeSize;
    final int rootINode;

    public MyXfsFileSystem(FSBlockDeviceAPI device) throws IOException {
        this.device = device;
        mainSuperBlock = new MySuperblock(device,0,this);
        sectorSize = (int) mainSuperBlock.getSectorSize();
        blockSize = mainSuperBlock.getBlockSize();
        aGCount = (int) mainSuperBlock.getAGCount();
        iNodeSize = (int) mainSuperBlock.getINodeSize();
        allocationGroupSize = blockSize * mainSuperBlock.getTotalBlocks() / aGCount;
        aGSizeLog2 = mainSuperBlock.getAGSizeLog2();
        xfsVersion = (int) mainSuperBlock.getVersion();
        rootINode = (int) mainSuperBlock.getRootINodeNumber();
    }


    public MySuperblock getMainSuperBlock(){
        return mainSuperBlock;
    }

    private long getAllocationGroupOffsetByIndex(int index) {
        return index * allocationGroupSize;
    }

    public MySuperblock getSuperBlockOnAllocationGroupIndex(int index) {
        final long offset = getAllocationGroupOffsetByIndex(index);
        return new MySuperblock(device,offset,this);
    }

    public MyAGFreeSpaceBlock getAGFreeSpaceBlockOnAllocationGroupIndex(int index) {
        final long offset = getAllocationGroupOffsetByIndex(index) + sectorSize;
        return new MyAGFreeSpaceBlock(device,offset,this);
    }

    public MyINodeInformation getINodeInformationOnAllocationGroupIndex(int index) {
        final long offset = getAllocationGroupOffsetByIndex(index) + sectorSize * 2L;
        return new MyINodeInformation(device,offset,this);
    }

    public MyAGFreeListHeader getAGFreeListHeaderOnAllocationGroupIndex(int index) {
        final long offset = getAllocationGroupOffsetByIndex(index) + sectorSize * 3L;
        return new MyAGFreeListHeader(device, offset,this);
    }

    public List<MyBPlusTree> getBTreesOnAllocationGroupIndex(int index) throws IOException {
        final List<Long> signatures =  Arrays.stream(MyBPlusTree.BTreeSignatures.values())
                .map(s -> s.signature).collect(Collectors.toList());
        long offset = getAllocationGroupOffsetByIndex(index) + blockSize;
        List<MyBPlusTree> data = new ArrayList<>();
        final ByteBuffer buffer = ByteBuffer.allocate(4);
        do {
            device.read(offset,buffer);
            final long signature = BigEndian.getUInt32(buffer.array(), 0);
            if (signatures.contains(signature)){
                data.add(new My64BitBPlusTree(device,offset,this));
                offset += blockSize;
            } else {
                break;
            }
        }while (true);
        return data;
    }

    public MyBPlusTree getBTreeOnAllocationGroupIndexAndType(int index, MyBPlusTree.BTreeSignatures type) throws IOException {
        final List<Long> signatures =  Arrays.stream(MyBPlusTree.BTreeSignatures.values())
                .map(s -> s.signature).collect(Collectors.toList());
        long offset = getAllocationGroupOffsetByIndex(index) + blockSize;
        MyBPlusTree data = null;
        final ByteBuffer buffer = ByteBuffer.allocate(4);
        do {
            device.read(offset,buffer);
            final long signature = BigEndian.getUInt32(buffer.array(), 0);
            if (signatures.contains(signature)){
                if (signature == type.signature) {
                    return new My64BitBPlusTree(device,offset,this);
                }
                offset += blockSize;
            } else {
                break;
            }
        }while (true);
        throw new IndexOutOfBoundsException("COULD not find btree of type " + type);
    }

    public MyBPlusTree getFreeSpaceByBlockBTreeOnAllocationGroupIndex(int index) throws IOException {
        final MyBPlusTree.BTreeSignatures treeSignature = getXfsVersion() == 5 ?
                MyBPlusTree.BTreeSignatures.FREE_SPACE_BY_BLOCK_V5 : MyBPlusTree.BTreeSignatures.FREE_SPACE_BY_BLOCK;
        return getBTreeOnAllocationGroupIndexAndType(index,treeSignature);
    }

    public MyBPlusTree getFreeSpaceBySizeBTreeOnAllocationGroupIndex(int index) throws IOException {
        final MyBPlusTree.BTreeSignatures treeSignature = getXfsVersion() == 5 ?
                MyBPlusTree.BTreeSignatures.FREE_SPACE_BY_SIZE_V5 : MyBPlusTree.BTreeSignatures.FREE_SPACE_BY_SIZE;
        return getBTreeOnAllocationGroupIndexAndType(index,treeSignature);
    }

    public MyBPlusTree getInodeBTreeOnAllocationGroupIndex(int index) throws IOException {
        final MyBPlusTree.BTreeSignatures treeSignature = getXfsVersion() == 5 ?
                MyBPlusTree.BTreeSignatures.INODE_V5 : MyBPlusTree.BTreeSignatures.INODE;
        return getBTreeOnAllocationGroupIndexAndType(index,treeSignature);
    }

    public MyInode getINode(long absoluteINodeNumber) throws IOException {
        // absolute inode number = ( allocation group number << number of relative inode number bits ) | relative inode number
        // number of relative inode number bits = allocation group size log2 + number of inodes per block log2
        final MySuperblock mainSuperblock = getSuperBlockOnAllocationGroupIndex(0);
        final long numberOfRelativeINodeBits = mainSuperblock.getAGSizeLog2() + mainSuperblock.getINodePerBlockLog2();
        int allocationGroupIndex = (int) ( absoluteINodeNumber >> numberOfRelativeINodeBits );
        long relativeINodeNumber  = absoluteINodeNumber & ( ( (long) 1 << numberOfRelativeINodeBits ) - 1 );
        long allocationGroupBlockNumber = (long) allocationGroupIndex * mainSuperblock.getAGSize();
        long offset = ( allocationGroupBlockNumber * mainSuperblock.getBlockSize() ) + ( relativeINodeNumber * mainSuperblock.getINodeSize() );
        return new MyInode(device, offset,absoluteINodeNumber,this);
    }

    public long getDataExtentOffset(MyExtentInformation extent) throws IOException {
//        file system block number = ( allocation group number << number of relative block number bits ) | relative block number
//        number of relative block number bits = allocation group size log2
//        file offset = ( allocation group block number + relative block number ) x block size
        final long startBlock = 2174664; // extent.getStartBlock();
        long allocationGroupIndex = startBlock >> aGSizeLog2;
        System.out.println(extent + " - allocationGroup");
        long offset = 0;
        return offset;
    }

    public int getXfsVersion() {
        return xfsVersion;
    }

    public long getAGCount() throws IOException {
        return aGCount;
    }

    public long getAllocationGroupSize() {
        return allocationGroupSize;
    }

    public long getBlockSize() {
        return blockSize;
    }

    public int getiNodeSize() {
        return iNodeSize;
    }

    public int getRootINode() {
        return rootINode;
    }
}
