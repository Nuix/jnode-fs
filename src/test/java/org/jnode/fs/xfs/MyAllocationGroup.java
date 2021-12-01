package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;

public class MyAllocationGroup {
    private final FSBlockDeviceAPI device;
    private final long offset;
    private final long allocationGroupSize;
    private final MySuperblock superBlock;
    private final MySuperblock mainSuperBlock;
    private final int xfsVersion;
    private final long byteBlockSize;

    private MyAllocationGroup(FSBlockDeviceAPI device, long offset, MySuperblock mainSuperBlock) throws IOException {
        this.device = device;
        this.offset = offset;
        this.superBlock = new MySuperblock(device, offset);
        if (mainSuperBlock == null) {
            this.mainSuperBlock = superBlock;
        } else {
            this.mainSuperBlock = mainSuperBlock;
        }
        this.byteBlockSize = this.mainSuperBlock.getBlockSize() / 2;
        allocationGroupSize = byteBlockSize * this.mainSuperBlock.getTotalBlocks()
                / this.mainSuperBlock.getAGCount();
        this.xfsVersion = (int) superBlock.getVersion();
    }

    public MyAllocationGroup(FSBlockDeviceAPI device) throws IOException {
        this(device, 0, null);
    }

    public MyAllocationGroup getNextAllocationGroup() throws IOException {
        return new MyAllocationGroup(device, allocationGroupSize + offset, mainSuperBlock);
    }
    public MyAllocationGroup getAllocationGroupOnIndex(int index) throws IOException {
        return new MyAllocationGroup(device, allocationGroupSize * index, mainSuperBlock);
    }

    public MySuperblock getSuperBlock() {
        return superBlock;
    }

    public MyAGFreeSpaceBlock getAGFreeSpaceBlock() {
        return new MyAGFreeSpaceBlock(device, offset + 256);
    }

    public MyINodeInformation getINodeInformation() {
        return new MyINodeInformation(device, offset + 256 * 2);
    }

    public MyAGFreeListHeader getAGFreeListHeader() {
        return new MyAGFreeListHeader(device, offset + 256 * 3);
    }

    public MyBPlusTree getBlockOffsetBTree() {
        return new My64BitBPlusTree(device, offset + 256 * 8,this);
    }

    public MyBPlusTree getBlockCountBTree() {
        return new My64BitBPlusTree(device, offset + 256 * 16,this);
    }

    public MyBPlusTree getV5FreeINodeBTree() {
        return new My64BitBPlusTree(device, offset + 256 * 24,this);
    }

    public MyBPlusTree getV5AllocatedINodeBTree() {
        return new My64BitBPlusTree(device, offset + 256 * 32,this);
    }

    public int getXfsVersion() {
        return xfsVersion;
    }

    public long getAGCount() throws IOException {
        return mainSuperBlock.getAGCount();
    }

    public long getAllocationGroupSize() {
        return allocationGroupSize;
    }
}
