package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;

public class MyInode extends MyXfsBaseAccessor {


    public static final long MAGIC_NUMBER = AsciiToHex("XAGI");


    public MyInode(FSBlockDeviceAPI devApi, long superBlockStart) {
        super(devApi, superBlockStart);
    }

    @Override
    public boolean isValidSignature() throws IOException {
        return getSignature() == MAGIC_NUMBER;
    }

    /**
     * Version
     */
    public long getVersion() throws IOException {
        return read(4, 4);
    }


    /**
     * Sequence number
     * Contains the allocation group number of the corresponding sector
     */
    public long getSequenceNumber() throws IOException {
        return read(8, 4);
    }


//    /**
//     * Unknown (Allocation group size)
//     *     Contains number of blocks
//     */
//    public long getVersion() throws IOException {
//        return read(12,4);
//    }


    /**
     * Number of inodes in the allocation group
     */
    public long getINodeAGCount() throws IOException {
        return read(16, 4);
    }

    /**
     * Inode B+ tree root block number
     * Contains a block number relative to the start of the allocation group
     */
    public long getBtreeBlockNumber() throws IOException {
        return read(20, 4);
    }

    /**
     * Inode B+ tree height/depth
     */
    public long getINodeBtreeDepth() throws IOException {
        return read(24, 4);
    }

    /**
     * Number of unused (free) inodes in the allocation group
     */
    public long getFreeINodeCount() throws IOException {
        return read(28, 4);
    }

    /**
     * First inode number of the last allocated inode chunk
     * Contains an inode number relative to the allocation group
     */
    public long getLasAllocatedINode() throws IOException {
        return read(32, 4);
    }

//40
//
//        64 x 4
//
//    Hash table of unlinked (deleted) inodes that are still being referenced
//    Contains -1 (0xffffffff) if not set

    // --------------- If file system version >= 5 ----------------


    /**
     * Block type identifier
     * Contains an UUID that should correspond to sb_uuid or sb_meta_uuid
     */
    public long getUuid() throws IOException {
        return read(296, 16);
    }

    public long getChecksum() throws IOException {
        return read(312, 4);
    }

    /**
     * Log sequence number
     */
    public long getLogSequenceNumber() throws IOException {
        return read(320, 8);
    }

    /**
     * Free inode B+ tree root block number
     * Contains a block number relative to the start of the allocation group
     */
    public long getFreeInodeRootNumber() throws IOException {
        return read(328, 4);
    }

    /**
     * Free inode B+ tree height/depth
     */
    public long getFreeInodeDepth() throws IOException {
        return read(332, 4);
    }

}
