package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Arrays;

/**
 * 32 Bit Implementation
 */
public class MyBPlusTree extends MyXfsBaseAccessor {


    enum BTreeSignatures {
        V5_free_space_block_offset_B_P_tree("AB3B"), V5_free_space_block_count_B_P_tree("AB3C"),
        Free_space_block_offset_B_P__tree("ABTB"),
        Free_space_block_count_B_P__tree("ABTC"),
        File_system_version_5_free_inode_B_P_tree("FIB3"),
        Free_inode_B_P_tree("FIBT"),
        File_system_version_5_allocated_inode_B_P_tree("IAB3"),
        Allocated_inode_B_P_tree("IABT"),
        File_system_version_5_reference_count_B_P__tree("R3FC");

        public String ascii;
        public long signature;

        BTreeSignatures(String val) {
            this.ascii = val;
            signature = AsciiToHex(val);
        }
    }

    public MyBPlusTree(FSBlockDeviceAPI devApi, long superBlockStart) {
        super(devApi, superBlockStart);
    }

    @Override
    public boolean isValidSignature() throws IOException {
        final long signature = getSignature();
        return Arrays.stream(BTreeSignatures.values()).anyMatch(s -> s.signature == signature);
    }

    /**
     * Level(or depth/height)
     * Contains 0 for a leaf block
     */
    public long getDepth() throws IOException {
        return read(4, 2);
    }

    /**
     * Number of records
     */
    public long getRecordNumber() throws IOException {
        return read(6, 2);
    }

    /**
     * Previous B+ tree block number
     * Contains a block number relative to the start of the allocation group or -1(0xffffffff)if not set
     */
    public long getPreviousBlockNumber() throws IOException {
        return read(8, 4);
    }

    /**
     * Next B+ tree block number
     * Contains a block number relative to the start of the allocation group or -1(0xffffffff) if not set
     */
    public long getNextBlockNumber() throws IOException {
        return read(12, 4);
    }


    // ------- If file system version >=5 ---------

    /**
     * Block number
     */
    public long getBlockNumber() throws IOException {
        return read(16, 8);
    }

    /**
     * Log sequence number
     */
    public long getLogSequenceNumber() throws IOException {
        return read(24, 8);
    }

    /**
     * Block type identifier
     * Contains an UUID that should correspond to sb_uuid or sb_meta_uuid
     */
    public String getUuid() throws IOException {
        return readUuid(32, 16);
    }

    /**
     * Owner allocation group
     * Contains the allocation group the block is part of
     */
    public long getOwnerAllocationGroup() throws IOException {
        return read(48, 4);
    }

    /**
     * Checksum
     */
    public long getChecksum() throws IOException {
        return read(52, 4);
    }

}
