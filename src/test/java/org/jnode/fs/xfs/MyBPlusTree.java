package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.inode.INode;
import org.jnode.fs.xfs.inode.INodeBTreeRecord;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

/**
 * 32 Bit Implementation
 */
public class MyBPlusTree extends MyXfsBaseAccessor {


    public static final int BTREE_HEADER_LENGTH = 0x10;
    public static final int INODE_CHUNK_COUNT = 64;
    /**
     * The block offset to the root inode.
     */
    private static final int ROOT_INODE_BLOCK = 8;
    private final MyAllocationGroup allocationGroup;

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

    public MyBPlusTree(FSBlockDeviceAPI devApi, long superBlockStart, MyAllocationGroup allocationGroup) {
        super(devApi, superBlockStart);
        this.allocationGroup = allocationGroup;
    }

    @Override
    protected List<Long> validSignatures() {
        return Arrays.stream(BTreeSignatures.values()).map(s -> s.signature).collect(Collectors.toList());
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


    protected int getFsOffset() throws IOException {
        // Offet changes according to filesystem version on fs5 it is 0x38 bytes long
        // TODO: should also check for 64bit or 32 bit as this also changes the initial offset
        return getSignature() == BTreeSignatures.File_system_version_5_allocated_inode_B_P_tree.signature
                ? 0x38 : BTREE_HEADER_LENGTH;
    }

    public List<INodeBTreeRecord> readRecords() throws IOException {
        List<INodeBTreeRecord> records = new ArrayList<>();
        long recordCount = getRecordNumber();
        System.out.println(getAsciiSignature());
        int offset = getFsOffset();


        final ByteBuffer buffer = ByteBuffer.allocate((int) allocationGroup.getSuperBlock().getBlockSize());
        getDevApi().read(this.getOffset() + 68,buffer);
        final byte[] data = buffer.array();

        for (; offset < data.length && records.size() < recordCount; offset += INodeBTreeRecord.LENGTH) {
            records.add(new INodeBTreeRecord(data, offset));
        }

        return records;
    }

    public INode getINode(long inode) throws IOException {
        List<INodeBTreeRecord> records = readRecords();
        int chunkNumber;
        boolean foundMatch = false;

        for (chunkNumber = 0; chunkNumber < records.size(); chunkNumber++) {
            INodeBTreeRecord record = records.get(chunkNumber);

            if (record.containsInode(inode)) {
                // Matching block...
                foundMatch = true;
                break;
            }
        }

        if (!foundMatch) {
            throw new IOException("Failed to find an inode for: " + inode);
        }

        long blockSize = allocationGroup.getSuperBlock().getBlockSize();
        long inodeSize = allocationGroup.getSuperBlock().getINodeSize();
        int chunkSize = (int) inodeSize * INODE_CHUNK_COUNT;
        int offset = (int) ((inode % INODE_CHUNK_COUNT) * inodeSize);


        byte[] data = new byte[chunkSize];

        ByteBuffer buffer = ByteBuffer.allocate(data.length);
        long rootInodeOffset = ROOT_INODE_BLOCK * blockSize;
        getDevApi().read(rootInodeOffset + chunkNumber * chunkSize, buffer);
        buffer.position(0);
        buffer.get(data);

        return new INode(inode, data, offset);
    }

}
