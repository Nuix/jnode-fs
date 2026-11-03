package org.jnode.fs.btrfs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jnode.util.LittleEndian;

/**
 * Builds a tiny single-device btrfs image in memory, one tree block at a time, so tests can shape
 * cases that {@code mkfs.btrfs} never produces: writable snapshots, hard links, truncated items,
 * trees that share children, pointers into the wrong block. One chunk maps logical addresses to
 * identical physical offsets, the superblock bootstraps it through {@code sys_chunk_array}, and the
 * chunk tree repeats it. The reader counts reads per block so tests can assert how much of the
 * image a walk touched.
 */
final class SyntheticBtrfs {

    static final int NODE_SIZE = 4096;
    static final int SECTOR_SIZE = 4096;
    static final int IMAGE_SIZE = 0x200000; // 2 MiB; one chunk covers all of it, logical == physical
    static final long FIRST_BLOCK = 0x20000; // tree blocks are handed out from here, NODE_SIZE apart
    static final long DATA_AREA = 0x100000;  // file extents live from here

    final byte[] image = new byte[IMAGE_SIZE];
    private long nextBlock = FIRST_BLOCK;
    final Map<Long, Integer> reads = new HashMap<Long, Integer>();

    /** A leaf item: key plus data bytes. */
    static final class Item {
        final BtrfsDiskKey key;
        final byte[] data;

        Item(long objectId, int type, long offset, byte[] data) {
            this.key = new BtrfsDiskKey(objectId, type, offset);
            this.data = data;
        }
    }

    /** An internal-node key pointer: the first key of the child and the child's address. */
    static final class KeyPtr {
        final BtrfsDiskKey key;
        final long child;

        KeyPtr(long objectId, int type, long offset, long child) {
            this.key = new BtrfsDiskKey(objectId, type, offset);
            this.child = child;
        }
    }

    /** Reserves the next tree block address. */
    long allocBlock() {
        long b = nextBlock;
        nextBlock += NODE_SIZE;
        return b;
    }

    private void header(long logical, int level, int nrItems) {
        int base = (int) logical;
        LittleEndian.setInt64(image, base + BtrfsConstants.HDR_BYTENR, logical);
        LittleEndian.setInt64(image, base + BtrfsConstants.HDR_GENERATION, 1);
        LittleEndian.setInt32(image, base + BtrfsConstants.HDR_NRITEMS, nrItems);
        image[base + BtrfsConstants.HDR_LEVEL] = (byte) level;
    }

    /** Writes a leaf at {@code logical}: items sorted by key, data packed down from the block's end. */
    void leaf(long logical, List<Item> items) {
        List<Item> sorted = new ArrayList<Item>(items);
        sorted.sort((a, b) -> a.key.compareTo(b.key));
        int base = (int) logical;
        header(logical, 0, sorted.size());
        int dataEnd = NODE_SIZE - BtrfsConstants.HEADER_SIZE; // item offsets are relative to the end of the header
        for (int i = 0; i < sorted.size(); i++) {
            Item it = sorted.get(i);
            int itemPos = base + BtrfsConstants.HEADER_SIZE + i * BtrfsConstants.ITEM_SIZE;
            dataEnd -= it.data.length;
            key(itemPos, it.key);
            LittleEndian.setInt32(image, itemPos + BtrfsConstants.KEY_SIZE, dataEnd);
            LittleEndian.setInt32(image, itemPos + BtrfsConstants.KEY_SIZE + 4, it.data.length);
            System.arraycopy(it.data, 0, image, base + BtrfsConstants.HEADER_SIZE + dataEnd, it.data.length);
        }
    }

    /** Writes an internal node at {@code logical} with the given level and child pointers. */
    void node(long logical, int level, List<KeyPtr> ptrs) {
        int base = (int) logical;
        header(logical, level, ptrs.size());
        for (int i = 0; i < ptrs.size(); i++) {
            int pos = base + BtrfsConstants.HEADER_SIZE + i * BtrfsConstants.KEY_PTR_SIZE;
            key(pos, ptrs.get(i).key);
            LittleEndian.setInt64(image, pos + BtrfsConstants.KEY_SIZE, ptrs.get(i).child);
            LittleEndian.setInt64(image, pos + BtrfsConstants.KEY_SIZE + 8, 1);
        }
    }

    private void key(int pos, BtrfsDiskKey k) {
        LittleEndian.setInt64(image, pos, k.getObjectId());
        image[pos + 8] = (byte) k.getType();
        LittleEndian.setInt64(image, pos + 9, k.getOffset());
    }

    // ---- item builders ----

    /** A btrfs_chunk (80 bytes, one stripe) mapping {@code [logical, logical+length)} to {@code physical}. */
    static byte[] chunk(long length, long physical) {
        byte[] c = new byte[BtrfsConstants.CHUNK_STRIPES + BtrfsConstants.STRIPE_SIZE];
        LittleEndian.setInt64(c, BtrfsConstants.CHUNK_LENGTH, length);
        LittleEndian.setInt64(c, 0x10, 65536);                    // stripe_len
        LittleEndian.setInt32(c, 0x28, SECTOR_SIZE);              // sector_size
        LittleEndian.setInt16(c, BtrfsConstants.CHUNK_NUM_STRIPES, 1);
        LittleEndian.setInt64(c, BtrfsConstants.CHUNK_STRIPES, 1); // devid
        LittleEndian.setInt64(c, BtrfsConstants.CHUNK_STRIPES + BtrfsConstants.STRIPE_OFFSET, physical);
        return c;
    }

    static Item chunkItem(long logical, long length, long physical) {
        return new Item(BtrfsConstants.OBJECTID_FIRST_CHUNK_TREE, BtrfsConstants.TYPE_CHUNK_ITEM, logical,
                chunk(length, physical));
    }

    /** A 439-byte ROOT_ITEM for a subvolume whose FS tree root is at {@code bytenr}. */
    static Item rootItem(long subvolId, long bytenr, long dirId, long flags, boolean hasParentUuid) {
        byte[] r = new byte[439];
        LittleEndian.setInt64(r, BtrfsConstants.ROOT_ITEM_DIRID, dirId);
        LittleEndian.setInt64(r, BtrfsConstants.ROOT_ITEM_BYTENR, bytenr);
        LittleEndian.setInt64(r, BtrfsConstants.ROOT_ITEM_FLAGS, flags);
        LittleEndian.setInt32(r, 0xd8, 1); // refs
        if (hasParentUuid) {
            Arrays.fill(r, BtrfsConstants.ROOT_ITEM_PARENT_UUID,
                    BtrfsConstants.ROOT_ITEM_PARENT_UUID + BtrfsConstants.UUID_SIZE, (byte) 0x5a);
        }
        return new Item(subvolId, BtrfsConstants.TYPE_ROOT_ITEM, 0, r);
    }

    /** A 160-byte INODE_ITEM. */
    static Item inode(long objectId, long size, int mode, int nlink) {
        byte[] n = new byte[160];
        LittleEndian.setInt64(n, BtrfsConstants.INODE_SIZE_OFF, size);
        LittleEndian.setInt32(n, BtrfsConstants.INODE_NLINK_OFF, nlink);
        LittleEndian.setInt32(n, BtrfsConstants.INODE_MODE_OFF, mode);
        return new Item(objectId, BtrfsConstants.TYPE_INODE_ITEM, 0, n);
    }

    static Item dir(long objectId) {
        return inode(objectId, 0, BtrfsConstants.S_IFDIR | 0755, 1);
    }

    static Item file(long objectId, long size) {
        return inode(objectId, size, BtrfsConstants.S_IFREG | 0644, 1);
    }

    /** A DIR_INDEX entry {@code name} at {@code index} in directory {@code dirId}, pointing at a child. */
    static Item dirIndex(long dirId, long index, String name, long childId, int locationType, int ftype) {
        byte[] nm = name.getBytes(StandardCharsets.UTF_8);
        byte[] d = new byte[BtrfsConstants.DIR_NAME + nm.length];
        LittleEndian.setInt64(d, BtrfsConstants.DIR_LOCATION_OBJECTID, childId);
        d[BtrfsConstants.DIR_LOCATION_TYPE] = (byte) locationType;
        LittleEndian.setInt16(d, BtrfsConstants.DIR_NAME_LEN, nm.length);
        d[BtrfsConstants.DIR_FTYPE] = (byte) ftype;
        System.arraycopy(nm, 0, d, BtrfsConstants.DIR_NAME, nm.length);
        return new Item(dirId, BtrfsConstants.TYPE_DIR_INDEX, index, d);
    }

    static Item fileEntry(long dirId, long index, String name, long childId) {
        return dirIndex(dirId, index, name, childId, BtrfsConstants.TYPE_INODE_ITEM, BtrfsConstants.FT_REG_FILE);
    }

    static Item subvolEntry(long dirId, long index, String name, long subvolId) {
        return dirIndex(dirId, index, name, subvolId, BtrfsConstants.TYPE_ROOT_ITEM, BtrfsConstants.FT_DIR);
    }

    /** A regular, uncompressed EXTENT_DATA item covering {@code numBytes} of the file from {@code fileOffset}. */
    static Item regularExtent(long inode, long fileOffset, long diskBytenr, long numBytes) {
        byte[] e = new byte[BtrfsConstants.EXTENT_ITEM_SIZE];
        LittleEndian.setInt64(e, BtrfsConstants.EXTENT_RAM_BYTES, numBytes);
        e[BtrfsConstants.EXTENT_TYPE] = BtrfsConstants.EXTENT_TYPE_REGULAR;
        LittleEndian.setInt64(e, BtrfsConstants.EXTENT_DISK_BYTENR, diskBytenr);
        LittleEndian.setInt64(e, BtrfsConstants.EXTENT_DISK_NUM_BYTES, numBytes);
        LittleEndian.setInt64(e, BtrfsConstants.EXTENT_NUM_BYTES, numBytes);
        return new Item(inode, BtrfsConstants.TYPE_EXTENT_DATA, fileOffset, e);
    }

    /** An inline EXTENT_DATA item holding {@code data} at file offset 0. */
    static Item inlineExtent(long inode, byte[] data) {
        byte[] e = new byte[BtrfsConstants.EXTENT_INLINE_DATA + data.length];
        LittleEndian.setInt64(e, BtrfsConstants.EXTENT_RAM_BYTES, data.length);
        e[BtrfsConstants.EXTENT_TYPE] = BtrfsConstants.EXTENT_TYPE_INLINE;
        System.arraycopy(data, 0, e, BtrfsConstants.EXTENT_INLINE_DATA, data.length);
        return new Item(inode, BtrfsConstants.TYPE_EXTENT_DATA, 0, e);
    }

    // ---- assembly ----

    /** Writes the chunk tree leaf and returns its address. */
    long chunkTree() {
        long b = allocBlock();
        leaf(b, Arrays.asList(chunkItem(0, IMAGE_SIZE, 0)));
        return b;
    }

    /** Writes the root tree leaf with the given ROOT_ITEMs and returns its address. */
    long rootTree(Item... rootItems) {
        long b = allocBlock();
        leaf(b, Arrays.asList(rootItems));
        return b;
    }

    /** Writes the superblock: magic, sizes, one device, and a sys_chunk_array holding the chunk. */
    void superblock(long rootTree, long chunkTree) {
        int sb = (int) BtrfsConstants.SUPERBLOCK_OFFSET;
        LittleEndian.setInt64(image, sb + 0x30, BtrfsConstants.SUPERBLOCK_OFFSET);
        System.arraycopy(BtrfsConstants.MAGIC, 0, image, sb + BtrfsConstants.SB_MAGIC, BtrfsConstants.MAGIC.length);
        LittleEndian.setInt64(image, sb + BtrfsConstants.SB_GENERATION, 1);
        LittleEndian.setInt64(image, sb + BtrfsConstants.SB_ROOT, rootTree);
        LittleEndian.setInt64(image, sb + BtrfsConstants.SB_CHUNK_ROOT, chunkTree);
        LittleEndian.setInt64(image, sb + BtrfsConstants.SB_TOTAL_BYTES, IMAGE_SIZE);
        LittleEndian.setInt64(image, sb + BtrfsConstants.SB_BYTES_USED, 0x40000);
        LittleEndian.setInt64(image, sb + BtrfsConstants.SB_ROOT_DIR_OBJECTID, 6);
        LittleEndian.setInt64(image, sb + BtrfsConstants.SB_NUM_DEVICES, 1);
        LittleEndian.setInt32(image, sb + BtrfsConstants.SB_SECTORSIZE, SECTOR_SIZE);
        LittleEndian.setInt32(image, sb + BtrfsConstants.SB_NODESIZE, NODE_SIZE);
        LittleEndian.setInt32(image, sb + 0x98, NODE_SIZE);   // leafsize
        LittleEndian.setInt32(image, sb + 0x9c, SECTOR_SIZE); // stripesize
        byte[] label = "synthetic".getBytes(StandardCharsets.UTF_8);
        System.arraycopy(label, 0, image, sb + BtrfsConstants.SB_LABEL, label.length);
        // sys_chunk_array: (disk_key, btrfs_chunk) for the one chunk
        int arr = sb + BtrfsConstants.SB_SYS_CHUNK_ARRAY;
        key(arr, new BtrfsDiskKey(BtrfsConstants.OBJECTID_FIRST_CHUNK_TREE, BtrfsConstants.TYPE_CHUNK_ITEM, 0));
        byte[] c = chunk(IMAGE_SIZE, 0);
        System.arraycopy(c, 0, image, arr + BtrfsConstants.KEY_SIZE, c.length);
        LittleEndian.setInt32(image, sb + BtrfsConstants.SB_SYS_CHUNK_ARRAY_SIZE, BtrfsConstants.KEY_SIZE + c.length);
    }

    /** Overwrites the superblock's {@code sys_chunk_array_size}. */
    void setSysChunkArraySize(int size) {
        LittleEndian.setInt32(image, (int) BtrfsConstants.SUPERBLOCK_OFFSET + BtrfsConstants.SB_SYS_CHUNK_ARRAY_SIZE, size);
    }

    /** Overwrites item {@code slot}'s (offset, size) pair in the leaf at {@code logical}. */
    void setItemPointer(long logical, int slot, int dataOff, int dataLen) {
        int itemPos = (int) logical + BtrfsConstants.HEADER_SIZE + slot * BtrfsConstants.ITEM_SIZE;
        LittleEndian.setInt32(image, itemPos + BtrfsConstants.KEY_SIZE, dataOff);
        LittleEndian.setInt32(image, itemPos + BtrfsConstants.KEY_SIZE + 4, dataLen);
    }

    /** Stores file bytes at a logical (== physical) data address. */
    void data(long logical, byte[] bytes) {
        System.arraycopy(bytes, 0, image, (int) logical, bytes.length);
    }

    /** A reader over the image that counts reads per offset. */
    BtrfsBlockReader reader() {
        return (offset, dst, off, length) -> {
            if (offset < 0 || offset + length > image.length) {
                throw new IOException("read outside the synthetic image: " + offset + "+" + length);
            }
            reads.merge(offset, 1, Integer::sum);
            System.arraycopy(image, (int) offset, dst, off, length);
        };
    }

    /** How many times the block (or data) at {@code logical} has been read. */
    int readsOf(long logical) {
        Integer n = reads.get(logical);
        return n == null ? 0 : n;
    }

    /** Writes the image to a temporary file (for the {@code FileDevice} / {@code FileSystem} path). */
    File toFile() throws IOException {
        File f = File.createTempFile("synthetic-btrfs", ".img");
        f.deleteOnExit();
        try (RandomAccessFile raf = new RandomAccessFile(f, "rw")) {
            raf.write(image);
        }
        return f;
    }
}
