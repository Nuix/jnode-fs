package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.util.BigEndian;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MyXfsFileSystem {
    private final long blockSize;
    FSBlockDeviceAPI device;
    long allocationGroupSize;
    int sectorSize;
    int aGCount;
    int xfsVersion;
    int iNodeSize;
    int rootINode;

    public MyXfsFileSystem(FSBlockDeviceAPI device) throws IOException {
        this.device = device;
        final MySuperblock mainSuperBlock = new MySuperblock(device,0);
        sectorSize = (int) mainSuperBlock.getSectorSize();
        blockSize = mainSuperBlock.getBlockSize();
        aGCount = (int) mainSuperBlock.getAGCount();
        iNodeSize = (int) mainSuperBlock.getINodeSize();
        allocationGroupSize = blockSize * mainSuperBlock.getTotalBlocks() / aGCount;
        xfsVersion = (int) mainSuperBlock.getVersion();
        rootINode = (int) mainSuperBlock.getRootINodeNumber();
    }

    private long getAllocationGroupOffsetByIndex(int index) {
        return index * allocationGroupSize;
    }

    public MySuperblock getSuperBlockOnAllocationGroupIndex(int index) {
        final long offset = getAllocationGroupOffsetByIndex(index);
        return new MySuperblock(device,offset);
    }

    public MyAGFreeSpaceBlock getAGFreeSpaceBlockOnAllocationGroupIndex(int index) {
        final long offset = getAllocationGroupOffsetByIndex(index) + sectorSize;
        return new MyAGFreeSpaceBlock(device,offset);
    }

    public MyINodeInformation getINodeInformationOnAllocationGroupIndex(int index) {
        final long offset = getAllocationGroupOffsetByIndex(index) + sectorSize * 2L;
        return new MyINodeInformation(device,offset);
    }

    public MyAGFreeListHeader getAGFreeListHeaderOnAllocationGroupIndex(int index) {
        final long offset = getAllocationGroupOffsetByIndex(index) + sectorSize * 3L;
        return new MyAGFreeListHeader(device, offset);
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

    public MyInode getINode(long index){
        return new MyInode(device, iNodeSize * index,index);
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
