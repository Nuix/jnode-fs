package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MyINodeInformation extends MyXfsBaseAccessor {


    public static final long MAGIC_NUMBER = AsciiToHex("XAGI");


    public MyINodeInformation(FSBlockDeviceAPI devApi, long superBlockStart) {
        super(devApi, superBlockStart);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(MAGIC_NUMBER);
    }

    public String getXfsDbInspectionString() throws IOException {
        String str = "";
        str += "magicnum = 0x" + Long.toHexString(getSignature()) + "\n";
        str += "versionnum = " + getVersion() + "\n";
        str += "seqno = " + getSequenceNumber() + "\n";
        str += "length = " + getAGBlockSize() + "\n";
        str += "count = " + getINodeAGCount() + "\n"; // Check
        str += "root = 3" + getBtreeBlockNumber() + "\n"; // Check
        str += "level = 1" + getINodeBtreeDepth() + "\n"; //Check
        str += "freecount = 58" + getFreeINodeCount() + "\n"; // Check
        str += "newino = 96" + getLasAllocatedINode() + "\n"; // Check
        str += "dirino = null\n";
        str += "unlinked[0-63] =\n";
        str += "uuid = " + getUuid() + "\n";
        str += "crc = 0x" + Long.toHexString(getChecksum()) + "\n"; // Check
        str += "lsn = 0x100000011" + Long.toHexString(getLogSequenceNumber()) + "\n"; // Check
        str += "free_root = " + getFreeInodeRootNumber() + "\n"; // Check
        str += "free_level = 1" + getFreeInodeDepth() + "\n"; // Check
        return str;
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


    /**
     * Unknown (Allocation group size)
     *     Contains number of blocks
     */
    public long getAGBlockSize() throws IOException {
        return read(12,4);
    }


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
    public String getUuid() throws IOException {
        return readUuid(296, 16);
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
