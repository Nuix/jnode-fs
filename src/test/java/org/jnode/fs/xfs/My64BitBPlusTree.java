package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;

/**
 * 32 Bit Implementation
 */
public class My64BitBPlusTree extends MyBPlusTree {
    public My64BitBPlusTree(FSBlockDeviceAPI devApi, long superBlockStart) {
        super(devApi, superBlockStart);
    }


    /**
     * Previous B+ tree block number
     * Contains a block number relative to the start of the allocation group or -1(0xffffffff)if not set
     */
    public long getPreviousBlockNumber() throws IOException {
        return read(8, 8);
    }

    /**
     * Next B+ tree block number
     * Contains a block number relative to the start of the allocation group or -1(0xffffffff) if not set
     */
    public long getNextBlockNumber() throws IOException {
        return read(16, 8);
    }


    // ------- If file system version >=5 ---------

    /**
     * Block number
     */
    public long getBlockNumber() throws IOException {
        return read(24, 8);
    }

    /**
     * Log sequence number
     */
    public long getLogSequenceNumber() throws IOException {
        return read(32, 8);
    }

    /**
     * Block type identifier
     * Contains an UUID that should correspond to sb_uuid or sb_meta_uuid
     */
    public String getUuid() throws IOException {
        return readUuid(40, 16);
    }

    /**
     * Owner allocation group
     * Contains the allocation group the block is part of
     */
    public long getOwnerAllocationGroup() throws IOException {
        return read(56, 8);
    }

    /**
     * Checksum
     */
    public long getChecksum() throws IOException {
        return read(52, 4);
    }

}
