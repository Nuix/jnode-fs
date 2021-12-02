package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MyAGFreeSpaceBlock extends MyXfsBaseAccessor {

    public static final long MAGIC_NUMBER = AsciiToHex("XAGF");

    public MyAGFreeSpaceBlock(FSBlockDeviceAPI devApi, long freeBlockStart) {
        super(devApi, freeBlockStart);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(MAGIC_NUMBER);
    }

    public String getXfsDbInspectionString() throws IOException {
        String str = "";

        str += "Magicnum = 0x" + Long.toHexString(getSignature()) + "\n";
        str += "Version = " + getVersion() + "\n";
        str += "Sequence number = " + getSequence_Number() + "\n";
        str += "Allocation group size = " + getAG_Size() + "\n";
        str += "Free space counts B+ tree root block number = " + getcountsBplustree() + "\n";
        str += "Free space sizes B+ tree root block number = " + getsizeBplustree() + "\n";
        str += "Free space counts B+ tree height/depth = " + getFScountsBplustree() + "\n";
        str += "Free space size B+ tree height/depth = " + getFSsizeBplustree() + "\n";
        str += "Index of the first free list block = " + getIndex_first_flb() + "\n";
        str += "Index of the last free list block = " + getIndex_last_flb() + "\n";
        str += "Free list size = " + getFLSize() + "\n";
        str += "Number of free blocks in the allocation group = " + get_fb_number() + "\n";
        str += "uuid = " + getUuid() + "\n";
//            str += "Longest contiguous free space in the allocation group = " + getAGFS() + "\n";

        return str;
    }

    public long getVersion() throws IOException {
        return read(4, 4);
    }

    public long getSequence_Number() throws IOException {
        return read(8, 4);
    }

    public long getAG_Size() throws IOException {
        return read(12, 4);
    }

    public long getcountsBplustree() throws IOException {
        return read(16, 4);
    }

    public long getsizeBplustree() throws IOException {
        return read(20, 4);
    }

//        public long getReserved() throws IOException {
//            return read(24, 4);
//        }

    public long getFScountsBplustree() throws IOException {
        return read(28, 4);
    }

    public long getFSsizeBplustree() throws IOException {
        return read(32, 4);
    }

//        public long getReserved2() throws IOException {
//            return read(36, 4);
//        }

    public long getIndex_first_flb() throws IOException {
        return read(60, 4);
    }

    public long getIndex_last_flb() throws IOException {
        return read(44, 4);
    }

    public long getFLSize() throws IOException {
        return read(48, 4);
    }

    public long get_fb_number() throws IOException {
        return read(52, 4);
    }

//        public long getAGFS() throws IOException {
//            return read(56, 4);
//        }

    // ------ Only used if the XFS_SB_VERSION2_LAZYSBCOUNTBIT feature flag is set -------

    /**
     * Number of blocks used for the free space B+ trees
     */
    public long getFreeSpaceBTreeBlockCount() throws IOException {
        return read(60, 4);
    }

    // ----- If file system version >= 5 -----------

    /**
     * Block type identifier
     * Contains an UUID that should correspond to sb_uuid or sb_meta_uuid
     */
    public String getUuid() throws IOException {
        return readUuid(64, 16);
    }
//80, 4
//    Unknown (Size of the reverse mapping B+ tree in blocks)
//
//84,4
//    Unknown (Size of the reference count B+ tree in blocks)

    /**
     * Reverse mapping B+ tree root block number
     * Contains a block number relative to the start of the allocation group
     */
    public long getReverseMappingBTreeRootBlockNum() throws IOException {
        return read(88, 4);
    }

    /**
     * Reference count B+ tree root block number
     * Contains a block number relative to the start of the allocation group
     */
    public long getReferenceCountBTreeRootBlockNum() throws IOException {
        return read(92, 4);
    }


// 96, 14 x 8 Unknown (reserved)

    /**
     * Log sequence number
     */
    public long getLogSequenceNumber() throws IOException {
        return read(208, 8);
    }


//216,4 Unknown (Checksum of the free sector)

//220,4,Unknown (reserved)
}
