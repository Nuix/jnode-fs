package org.jnode.fs.btrfs;

import java.io.IOException;
import java.util.Arrays;

import org.jnode.util.LittleEndian;

/**
 * The btrfs superblock (parsed from the primary copy at {@link BtrfsConstants#SUPERBLOCK_OFFSET}).
 * Everything the reader needs to start: the logical addresses of the root and chunk trees, the node
 * size, and the {@code sys_chunk_array} that bootstraps logical→physical translation.
 *
 * @author David Baird
 */
public class BtrfsSuperblock {

    private final byte[] data;

    public BtrfsSuperblock(BtrfsBlockReader reader) throws IOException {
        // read through the sys_chunk_array
        int len = BtrfsConstants.SB_SYS_CHUNK_ARRAY + BtrfsConstants.SYS_CHUNK_ARRAY_MAX;
        data = reader.read(BtrfsConstants.SUPERBLOCK_OFFSET, len);
        if (!isBtrfs()) {
            throw new IOException("Not a btrfs volume (magic mismatch)");
        }
        validate();
    }

    /**
     * Rejects superblock fields that would otherwise size buffers or arrays later on. The ranges are
     * the ones the kernel's {@code btrfs_validate_super} enforces: a power-of-two sector size from
     * 4&nbsp;KiB to 64&nbsp;KiB, a power-of-two node size from the sector size to 64&nbsp;KiB, and a
     * {@code sys_chunk_array} no larger than its fixed 2&nbsp;KiB slot.
     */
    private void validate() throws IOException {
        long sectorSize = LittleEndian.getUInt32(data, BtrfsConstants.SB_SECTORSIZE);
        long nodeSize = LittleEndian.getUInt32(data, BtrfsConstants.SB_NODESIZE);
        long sysChunkArraySize = LittleEndian.getUInt32(data, BtrfsConstants.SB_SYS_CHUNK_ARRAY_SIZE);
        if (!isPowerOfTwoIn(sectorSize, MIN_BLOCK, MAX_BLOCK)) {
            throw new IOException("btrfs superblock: invalid sector size " + sectorSize);
        }
        if (!isPowerOfTwoIn(nodeSize, sectorSize, MAX_BLOCK)) {
            throw new IOException("btrfs superblock: invalid node size " + nodeSize
                    + " (sector size " + sectorSize + ")");
        }
        if (sysChunkArraySize > BtrfsConstants.SYS_CHUNK_ARRAY_MAX) {
            throw new IOException("btrfs superblock: sys_chunk_array_size " + sysChunkArraySize
                    + " exceeds " + BtrfsConstants.SYS_CHUNK_ARRAY_MAX);
        }
    }

    private static final long MIN_BLOCK = 4096;
    private static final long MAX_BLOCK = 65536;

    private static boolean isPowerOfTwoIn(long v, long min, long max) {
        return v >= min && v <= max && (v & (v - 1)) == 0;
    }

    public boolean isBtrfs() {
        for (int i = 0; i < BtrfsConstants.MAGIC.length; i++) {
            if (data[BtrfsConstants.SB_MAGIC + i] != BtrfsConstants.MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    /** Cheap static probe of a 512-byte-plus head sample (offset within the sample = 0x40+sb base). */
    public static boolean hasMagicAt(byte[] superblockSample, int base) {
        if (base + BtrfsConstants.SB_MAGIC + BtrfsConstants.MAGIC.length > superblockSample.length) {
            return false;
        }
        for (int i = 0; i < BtrfsConstants.MAGIC.length; i++) {
            if (superblockSample[base + BtrfsConstants.SB_MAGIC + i] != BtrfsConstants.MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public long getRootTreeLogical() {
        return LittleEndian.getInt64(data, BtrfsConstants.SB_ROOT);
    }

    public long getChunkRootLogical() {
        return LittleEndian.getInt64(data, BtrfsConstants.SB_CHUNK_ROOT);
    }

    public int getNodeSize() {
        return (int) LittleEndian.getUInt32(data, BtrfsConstants.SB_NODESIZE);
    }

    public int getSectorSize() {
        return (int) LittleEndian.getUInt32(data, BtrfsConstants.SB_SECTORSIZE);
    }

    /** Checksum algorithm: 0 = crc32c (default), 1 = xxhash64, 2 = sha256, 3 = blake2. */
    public int getCsumType() {
        return LittleEndian.getUInt16(data, BtrfsConstants.SB_CSUM_TYPE);
    }

    /**
     * The volume label (`mkfs.btrfs -L` / `btrfs filesystem label`), or an empty string if unset.
     *
     * @return the label, decoded UTF-8 up to its NUL terminator.
     */
    public String getLabel() {
        int start = BtrfsConstants.SB_LABEL;
        int len = 0;
        while (len < BtrfsConstants.SB_LABEL_SIZE && data[start + len] != 0) {
            len++;
        }
        return new String(data, start, len, java.nio.charset.StandardCharsets.UTF_8);
    }

    public long getNumDevices() {
        return LittleEndian.getInt64(data, BtrfsConstants.SB_NUM_DEVICES);
    }

    public long getTotalBytes() {
        return LittleEndian.getInt64(data, BtrfsConstants.SB_TOTAL_BYTES);
    }

    public long getBytesUsed() {
        return LittleEndian.getInt64(data, BtrfsConstants.SB_BYTES_USED);
    }

    public int getChunkRootLevel() {
        return data[BtrfsConstants.SB_CHUNK_ROOT_LEVEL] & 0xFF;
    }

    public int getSysChunkArraySize() {
        return (int) LittleEndian.getUInt32(data, BtrfsConstants.SB_SYS_CHUNK_ARRAY_SIZE);
    }

    /** The {@code sys_chunk_array} bytes (disk_key + chunk_item pairs) that bootstrap the chunk map. */
    public byte[] getSysChunkArray() {
        int size = getSysChunkArraySize();
        return Arrays.copyOfRange(data, BtrfsConstants.SB_SYS_CHUNK_ARRAY,
                BtrfsConstants.SB_SYS_CHUNK_ARRAY + size);
    }
}
