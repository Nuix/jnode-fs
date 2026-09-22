package org.jnode.fs.btrfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jnode.util.LittleEndian;

/**
 * Translates btrfs <em>logical</em> addresses to <em>physical</em> device offsets. btrfs addresses
 * everything logically; the mapping lives in the chunk tree. The superblock carries a {@code
 * sys_chunk_array} with just enough chunk mappings to read the chunk tree itself, which is then
 * walked to collect the full mapping (single-device only — multi-device stripes are rejected).
 *
 * @author David Baird
 */
public class BtrfsChunkMap {

    /** One contiguous chunk: a logical range mapped linearly to a single device's physical offset. */
    private static final class Chunk {
        final long logicalStart;
        final long length;
        final long physicalStart;

        Chunk(long logicalStart, long length, long physicalStart) {
            this.logicalStart = logicalStart;
            this.length = length;
            this.physicalStart = physicalStart;
        }

        boolean contains(long logical) {
            return logical >= logicalStart && logical < logicalStart + length;
        }
    }

    private final List<Chunk> chunks = new ArrayList<Chunk>();

    /** Builds the bootstrap map from the superblock's sys_chunk_array, then the full chunk tree. */
    public BtrfsChunkMap(BtrfsSuperblock sb, BtrfsBlockReader reader) throws IOException {
        parseSysChunkArray(sb.getSysChunkArray());
        // With the bootstrap map we can now read the chunk tree and collect every chunk.
        BtrfsTree chunkTree = new BtrfsTree(reader, this, sb.getNodeSize());
        chunkTree.scanLeaves(sb.getChunkRootLogical(), (key, itemData) -> {
            if (key.getType() == BtrfsConstants.TYPE_CHUNK_ITEM) {
                addChunk(key.getOffset(), itemData);
            }
        });
    }

    /** sys_chunk_array is a sequence of (disk_key[17], btrfs_chunk) pairs. */
    private void parseSysChunkArray(byte[] array) throws IOException {
        int pos = 0;
        while (pos + BtrfsConstants.KEY_SIZE < array.length) {
            long logical = LittleEndian.getInt64(array, pos + 9); // key.offset = chunk logical start
            int chunkOff = pos + BtrfsConstants.KEY_SIZE;
            int numStripes = LittleEndian.getUInt16(array, chunkOff + BtrfsConstants.CHUNK_NUM_STRIPES);
            if (numStripes <= 0) {
                break;
            }
            addChunkAt(logical, array, chunkOff);
            pos = chunkOff + BtrfsConstants.CHUNK_STRIPES + numStripes * BtrfsConstants.STRIPE_SIZE;
        }
    }

    private void addChunk(long logical, byte[] chunkItem) throws IOException {
        addChunkAt(logical, chunkItem, 0);
    }

    private void addChunkAt(long logical, byte[] buf, int chunkOff) throws IOException {
        long length = LittleEndian.getInt64(buf, chunkOff + BtrfsConstants.CHUNK_LENGTH);
        long type = LittleEndian.getInt64(buf, chunkOff + BtrfsConstants.CHUNK_TYPE);
        // Reject only STRIPED profiles (RAID0/10/5/6), where a logical range is split across stripes
        // and can't be mapped linearly. single (1 stripe) and DUP (2 identical copies on one device)
        // both map linearly through stripe 0 — DUP is the default metadata profile on single disks,
        // so it must work. (Multi-device is already rejected by the caller via num_devices.)
        if ((type & BtrfsConstants.BLOCK_GROUP_STRIPED_MASK) != 0) {
            throw new IOException("Striped/RAID btrfs is not supported (single-device linear only)");
        }
        // use stripe 0: for single it's the only stripe, for DUP it's a full copy of the range
        long physical = LittleEndian.getInt64(buf,
                chunkOff + BtrfsConstants.CHUNK_STRIPES + BtrfsConstants.STRIPE_OFFSET);
        // avoid duplicate entries (the chunk also appears in the sys array)
        for (Chunk c : chunks) {
            if (c.logicalStart == logical) {
                return;
            }
        }
        chunks.add(new Chunk(logical, length, physical));
    }

    /**
     * Physical device offset for a logical address.
     *
     * @param logical a btrfs logical address (as stored in tree pointers and extents).
     * @return the corresponding physical offset on the (single) device.
     * @throws IOException if no chunk maps the address.
     */
    public long toPhysical(long logical) throws IOException {
        for (Chunk c : chunks) {
            if (c.contains(logical)) {
                return c.physicalStart + (logical - c.logicalStart);
            }
        }
        throw new IOException("Unmapped btrfs logical address: " + logical);
    }
}
