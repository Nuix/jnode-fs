package org.jnode.fs.btrfs;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.zip.Deflater;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FSFile;
import org.jnode.fs.FileSystemException;
import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Cases a real {@code mkfs.btrfs} image can't easily provide, built with {@link SyntheticBtrfs}:
 * the live root being a writable snapshot, hard links sharing an inode, and malformed metadata —
 * which must surface as an {@link IOException} after a bounded amount of work, never as an
 * unchecked exception or an unbounded walk.
 */
public class BtrfsRobustnessTest {

    private static final long FS = BtrfsConstants.OBJECTID_FS_TREE;
    private static final long ROOT_DIR = BtrfsConstants.OBJECTID_FIRST_FREE;

    /** The FS tree for a subvolume: a root dir holding one 5-byte inline file {@code hello}. */
    private static long simpleFsTree(SyntheticBtrfs img, String fileName) {
        long leaf = img.allocBlock();
        img.leaf(leaf, Arrays.asList(
                SyntheticBtrfs.dir(ROOT_DIR),
                SyntheticBtrfs.fileEntry(ROOT_DIR, 2, fileName, 300),
                SyntheticBtrfs.file(300, 5),
                SyntheticBtrfs.inlineExtent(300, "hello".getBytes(StandardCharsets.UTF_8))));
        return leaf;
    }

    private static List<String> names(List<BtrfsNode> nodes) {
        List<String> out = new ArrayList<String>();
        for (BtrfsNode n : nodes) {
            out.add(n.getName());
        }
        return out;
    }

    private static BtrfsNode child(BtrfsNode dir, String name) throws IOException {
        for (BtrfsNode n : dir.getChildren()) {
            if (name.equals(n.getName())) {
                return n;
            }
        }
        fail("no child named " + name + " in " + names(dir.getChildren()));
        return null;
    }

    private static void assertIOException(String what, Runnable r) {
        try {
            r.run();
            fail(what + ": expected an IOException");
        } catch (UncheckedIO e) {
            assertNotNull(e.getCause());
        }
    }

    /** Wraps an IOException so a lambda can throw it through {@link Runnable}. */
    private static final class UncheckedIO extends RuntimeException {
        UncheckedIO(IOException cause) {
            super(cause);
        }
    }

    private interface IOAction {
        void run() throws IOException;
    }

    private static Runnable io(IOAction a) {
        return () -> {
            try {
                a.run();
            } catch (IOException e) {
                throw new UncheckedIO(e);
            }
        };
    }

    // ---- snapshots ----

    /**
     * A volume laid out like a rolled-back openSUSE / restored Timeshift root: the top level holds
     * one entry {@code @} → subvolume 257, whose ROOT_ITEM carries a parent_uuid (it was created as
     * a snapshot) but is writable.
     */
    private static SyntheticBtrfs rootIsASnapshot(long flags) {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long live = simpleFsTree(img, "etc");
        long top = img.allocBlock();
        img.leaf(top, Arrays.asList(
                SyntheticBtrfs.dir(ROOT_DIR),
                SyntheticBtrfs.subvolEntry(ROOT_DIR, 2, "@", 257)));
        long rootTree = img.rootTree(
                SyntheticBtrfs.rootItem(FS, top, ROOT_DIR, 0, false),
                SyntheticBtrfs.rootItem(257, live, ROOT_DIR, flags, true));
        img.superblock(rootTree, chunkTree);
        return img;
    }

    @Test
    public void writableSnapshotThatBecameTheLiveRootIsListed() throws Exception {
        SyntheticBtrfs img = rootIsASnapshot(0);
        BtrfsVolume vol = new BtrfsVolume(img.reader());
        BtrfsNode at = child(vol.getRoot(), "@");
        assertTrue(at.isDirectory());
        assertEquals(Arrays.asList("etc"), names(at.getChildren()));
    }

    @Test
    public void readOnlySnapshotIsSkippedByDefaultAndListedOnRequest() throws Exception {
        SyntheticBtrfs img = rootIsASnapshot(BtrfsConstants.ROOT_SUBVOL_RDONLY);
        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertEquals("a read-only snapshot is gated", Arrays.asList(), names(vol.getRoot().getChildren()));
        vol.setDescendSnapshots(true);
        assertEquals(Arrays.asList("@"), names(vol.getRoot().getChildren()));
    }

    // ---- hard links ----

    @Test
    public void hardLinksInOneDirectoryGetDistinctEntryIds() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long fs = img.allocBlock();
        img.leaf(fs, Arrays.asList(
                SyntheticBtrfs.dir(ROOT_DIR),
                SyntheticBtrfs.fileEntry(ROOT_DIR, 2, "a.txt", 300),
                SyntheticBtrfs.fileEntry(ROOT_DIR, 3, "b.txt", 300),
                SyntheticBtrfs.inode(300, 5, BtrfsConstants.S_IFREG | 0644, 2),
                SyntheticBtrfs.inlineExtent(300, "hello".getBytes(StandardCharsets.UTF_8))));
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, fs, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        File f = img.toFile();
        try (FileDevice device = new FileDevice(f, "r")) {
            BtrfsFileSystem fsys = new BtrfsFileSystemType().create(device, true);
            FSDirectory root = fsys.getRootEntry().getDirectory();
            FSEntry a = root.getEntry("a.txt");
            FSEntry b = root.getEntry("b.txt");
            assertEquals("5-300-2", a.getId());
            assertEquals("5-300-3", b.getId());
            assertNotEquals(a.getId(), b.getId());
            assertEquals("a.txt", root.getEntryById("5-300-2").getName());
            assertEquals("b.txt", root.getEntryById("5-300-3").getName());
            assertEquals("5-256", fsys.getRootEntry().getId());
        }
    }

    // ---- malformed metadata ----

    @Test
    public void truncatedExtentDataItemIsAnIOException() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long fs = img.allocBlock();
        img.leaf(fs, Arrays.asList(
                SyntheticBtrfs.dir(ROOT_DIR),
                SyntheticBtrfs.fileEntry(ROOT_DIR, 2, "short.bin", 300),
                SyntheticBtrfs.file(300, 10),
                new SyntheticBtrfs.Item(300, BtrfsConstants.TYPE_EXTENT_DATA, 0, new byte[4]))); // 4-byte item
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, fs, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        BtrfsNode file = child(vol.getRoot(), "short.bin");
        assertIOException("reading a file with a 4-byte EXTENT_DATA", io(() -> file.read(0, new byte[10], 0, 10)));
    }

    @Test
    public void truncatedSysChunkArrayIsAnIOExceptionAtMount() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, simpleFsTree(img, "f"), ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);
        img.setSysChunkArraySize(20); // key + 3 bytes of chunk

        assertIOException("mounting with a 20-byte sys_chunk_array", io(() -> new BtrfsVolume(img.reader())));
        File f = img.toFile();
        try (FileDevice device = new FileDevice(f, "r")) {
            new BtrfsFileSystemType().create(device, true);
            fail("expected a FileSystemException");
        } catch (FileSystemException expected) {
            // the checked, documented failure — not an ArrayIndexOutOfBoundsException
        }
    }

    @Test
    public void shortChunkItemInTheChunkTreeIsAnIOExceptionAtMount() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.allocBlock();
        img.leaf(chunkTree, Arrays.asList(
                SyntheticBtrfs.chunkItem(0, SyntheticBtrfs.IMAGE_SIZE, 0),
                new SyntheticBtrfs.Item(BtrfsConstants.OBJECTID_FIRST_CHUNK_TREE, BtrfsConstants.TYPE_CHUNK_ITEM,
                        0x180000, new byte[10]))); // a 10-byte CHUNK_ITEM
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, simpleFsTree(img, "f"), ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        assertIOException("mounting with a 10-byte CHUNK_ITEM", io(() -> new BtrfsVolume(img.reader())));
    }

    @Test
    public void treeWhoseNodesShareChildrenIsRejectedAfterOneVisitPerBlock() throws Exception {
        // levels 3 -> 2 -> 1 -> 0, every internal node pointing twice at the same child: a valid-looking
        // DAG that a depth limit alone would walk 2^3 times (and 8^15 times at btrfs's real fan-out)
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long leaf = simpleFsTree(img, "f");
        long n1 = img.allocBlock();
        long n2 = img.allocBlock();
        long n3 = img.allocBlock();
        img.node(n1, 1, Arrays.asList(new SyntheticBtrfs.KeyPtr(ROOT_DIR, 1, 0, leaf), new SyntheticBtrfs.KeyPtr(ROOT_DIR, 1, 0, leaf)));
        img.node(n2, 2, Arrays.asList(new SyntheticBtrfs.KeyPtr(ROOT_DIR, 1, 0, n1), new SyntheticBtrfs.KeyPtr(ROOT_DIR, 1, 0, n1)));
        img.node(n3, 3, Arrays.asList(new SyntheticBtrfs.KeyPtr(ROOT_DIR, 1, 0, n2), new SyntheticBtrfs.KeyPtr(ROOT_DIR, 1, 0, n2)));
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, n3, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertIOException("scanning a DAG-shaped tree", io(() -> vol.getRoot()));
        assertEquals("the leaf is read once, not 2^3 times", 1, img.readsOf(leaf));
        assertEquals(1, img.readsOf(n1));
        assertEquals(1, img.readsOf(n2));
    }

    @Test
    public void childAtTheWrongLevelIsRejected() throws Exception {
        // a level-1 node whose child is another level-1 node (should be a leaf): caught by the level
        // sequence check on the second read, before anything is walked
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long leaf = simpleFsTree(img, "f");
        long inner = img.allocBlock();
        long outer = img.allocBlock();
        img.node(inner, 1, Arrays.asList(new SyntheticBtrfs.KeyPtr(ROOT_DIR, 1, 0, leaf)));
        img.node(outer, 1, Arrays.asList(new SyntheticBtrfs.KeyPtr(ROOT_DIR, 1, 0, inner)));
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, outer, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertIOException("a level-1 child of a level-1 node", io(() -> vol.getRoot()));
        assertEquals("never descended past the bad child", 0, img.readsOf(leaf));
    }

    @Test
    public void blockWhoseHeaderNamesAnotherAddressIsRejected() throws Exception {
        // the FS tree pointer leads to a copy of a valid leaf sitting at the wrong address: the
        // header's bytenr gives it away (a misdirected pointer or a wrong chunk mapping)
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long leaf = simpleFsTree(img, "f");
        long copy = img.allocBlock();
        System.arraycopy(img.image, (int) leaf, img.image, (int) copy, SyntheticBtrfs.NODE_SIZE);
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, copy, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertIOException("a block whose header bytenr != its address", io(() -> vol.getRoot()));
    }

    @Test
    public void headerLevelBeyondBtrfsMaxIsRejected() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long leaf = simpleFsTree(img, "f");
        img.image[(int) leaf + BtrfsConstants.HDR_LEVEL] = 9;
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, leaf, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertIOException("a block claiming level 9", io(() -> vol.getRoot()));
    }

    @Test
    public void itemOffsetNearIntMaxIsSkippedNotThrown() throws Exception {
        // (offset, size) whose int sum wraps negative must fail the bounds check, not sail through it
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long leaf = simpleFsTree(img, "f");
        // slot 1 is the DIR_INDEX (keys sort: inode 256 item 0, dir index 1, inode 300 item 2, extent 3)
        img.setItemPointer(leaf, 1, 0x7FFFFF00, 0x200);
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, leaf, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertEquals("the corrupt entry is dropped, the rest of the leaf survives",
                Arrays.asList(), names(vol.getRoot().getChildren()));
        // the same guard serves the cursor: a keyed read of the corrupt item yields no bytes
        BtrfsTree.Cursor c = vol.tree().search(leaf, new BtrfsDiskKey(ROOT_DIR, BtrfsConstants.TYPE_DIR_INDEX, 0));
        assertTrue(c.valid());
        assertEquals(0, c.data().length);
    }

    /**
     * The cursor's forward walk over a tree whose levels 3 -> 0 each point twice at the same child.
     * {@code leafItems} fill the one shared leaf; the FS tree is otherwise unreachable, so this goes
     * through the keyed path ({@code collectExtents}) without the full scan seeing it first.
     */
    private static SyntheticBtrfs sharedChildrenUnderTheCursor(SyntheticBtrfs img, long leaf) {
        long chunkTree = img.chunkTree();
        long prev = leaf;
        for (int level = 1; level <= 3; level++) {
            long n = img.allocBlock();
            // the first pointer's key is <= the search key, so the descent takes it and the forward
            // walk then reaches the second pointer -- the same block again
            img.node(n, level, Arrays.asList(
                    new SyntheticBtrfs.KeyPtr(300, BtrfsConstants.TYPE_EXTENT_DATA, 0, prev),
                    new SyntheticBtrfs.KeyPtr(300, BtrfsConstants.TYPE_EXTENT_DATA, 1, prev)));
            prev = n;
        }
        img.superblock(img.rootTree(SyntheticBtrfs.rootItem(FS, prev, ROOT_DIR, 0, false)), chunkTree);
        return img;
    }

    @Test
    public void cursorRejectsATreeWhoseNodesShareChildren() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long leaf = img.allocBlock();
        img.leaf(leaf, Arrays.asList(SyntheticBtrfs.regularExtent(300, 0, 0, 4096)));
        sharedChildrenUnderTheCursor(img, leaf);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertIOException("iterating a DAG with the cursor",
                io(() -> BtrfsFileContent.collectExtents(vol, FS, 300)));
        assertEquals("the shared leaf is entered once, not 2^3 times", 1, img.readsOf(leaf));
    }

    @Test
    public void cursorRejectsSharedEmptyLeavesInsteadOfSpinning() throws Exception {
        // empty shared leaves never yield a key, so only a block-level guard can stop the walk
        SyntheticBtrfs img = new SyntheticBtrfs();
        long leaf = img.allocBlock();
        img.leaf(leaf, Arrays.<SyntheticBtrfs.Item>asList());
        sharedChildrenUnderTheCursor(img, leaf);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertIOException("iterating a DAG of empty leaves",
                io(() -> BtrfsFileContent.collectExtents(vol, FS, 300)));
    }

    /** A volume whose only file (inode 300, 4096 bytes) is backed by {@code extent}. */
    private static BtrfsNode fileWithExtent(SyntheticBtrfs.Item extent) throws IOException {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long fs = img.allocBlock();
        img.leaf(fs, Arrays.asList(
                SyntheticBtrfs.dir(ROOT_DIR),
                SyntheticBtrfs.fileEntry(ROOT_DIR, 2, "f", 300),
                SyntheticBtrfs.file(300, 4096),
                extent));
        img.superblock(img.rootTree(SyntheticBtrfs.rootItem(FS, fs, ROOT_DIR, 0, false)), chunkTree);
        return child(new BtrfsVolume(img.reader()).getRoot(), "f");
    }

    @Test
    public void negativeDataOffsetIsAnIOException() throws Exception {
        // on a hole this used to index before the (empty) extent; on a data extent it would have
        // read the disk bytes in front of it
        SyntheticBtrfs.Item hole = SyntheticBtrfs.regularExtent(300, 0, 0, 4096);
        LittleEndian.setInt64(hole.data, BtrfsConstants.EXTENT_DATA_OFFSET, -4096L);
        BtrfsNode f = fileWithExtent(hole);
        assertIOException("a hole with data_offset -4096", io(() -> f.read(0, new byte[16], 0, 16)));

        SyntheticBtrfs.Item data = SyntheticBtrfs.regularExtent(300, 0, SyntheticBtrfs.DATA_AREA, 4096);
        LittleEndian.setInt64(data.data, BtrfsConstants.EXTENT_DATA_OFFSET, -4096L);
        BtrfsNode g = fileWithExtent(data);
        assertIOException("a data extent with data_offset -4096", io(() -> g.read(0, new byte[16], 0, 16)));
    }

    @Test
    public void compressedExtentLargerThanBtrfsAllowsIsAnIOException() throws Exception {
        // ram_bytes / disk_num_bytes size the decompression buffers: past 128 KiB they're corrupt,
        // and must be refused before anything is allocated
        SyntheticBtrfs.Item hugeRam = SyntheticBtrfs.regularExtent(300, 0, SyntheticBtrfs.DATA_AREA, 4096);
        hugeRam.data[BtrfsConstants.EXTENT_COMPRESSION] = BtrfsConstants.COMPRESS_ZSTD;
        LittleEndian.setInt64(hugeRam.data, BtrfsConstants.EXTENT_RAM_BYTES, 0x7FFF0000L);
        BtrfsNode f = fileWithExtent(hugeRam);
        assertIOException("ram_bytes 0x7FFF0000", io(() -> f.read(0, new byte[16], 0, 16)));

        SyntheticBtrfs.Item hugeDisk = SyntheticBtrfs.regularExtent(300, 0, SyntheticBtrfs.DATA_AREA, 4096);
        hugeDisk.data[BtrfsConstants.EXTENT_COMPRESSION] = BtrfsConstants.COMPRESS_LZO;
        LittleEndian.setInt64(hugeDisk.data, BtrfsConstants.EXTENT_DISK_NUM_BYTES, 0x7FFF0000L);
        BtrfsNode g = fileWithExtent(hugeDisk);
        assertIOException("disk_num_bytes 0x7FFF0000", io(() -> g.read(0, new byte[16], 0, 16)));
    }

    @Test
    public void zlibOutputIsCappedAtTheExtentLimit() throws Exception {
        // 4 MiB of zeros deflates to a few KiB: a bomb in miniature. Decoding stops at 128 KiB.
        Deflater deflater = new Deflater();
        deflater.setInput(new byte[4 * 1024 * 1024]);
        deflater.finish();
        byte[] buf = new byte[64 * 1024];
        int len = deflater.deflate(buf);
        deflater.end();
        byte[] out = BtrfsFileContent.decompress(BtrfsConstants.COMPRESS_ZLIB, Arrays.copyOf(buf, len), 4096);
        assertTrue("decoded " + out.length + " bytes", out.length <= 128 * 1024);
        assertTrue("still decodes what the extent needs", out.length >= 4096);
    }

    /** A valid one-file volume, then {@code tweak} applied to its superblock bytes. */
    private static SyntheticBtrfs volumeWithSuperblockField(int field, int value) {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        img.superblock(img.rootTree(SyntheticBtrfs.rootItem(FS, simpleFsTree(img, "f"), ROOT_DIR, 0, false)),
                chunkTree);
        LittleEndian.setInt32(img.image, (int) BtrfsConstants.SUPERBLOCK_OFFSET + field, value);
        return img;
    }

    @Test
    public void superblockSizesOutsideBtrfsLimitsAreRejectedAtMount() throws Exception {
        int[][] cases = {
            {BtrfsConstants.SB_SYS_CHUNK_ARRAY_SIZE, 0xFFFFFFF0}, // used to wrap -> IllegalArgumentException
            {BtrfsConstants.SB_SYS_CHUNK_ARRAY_SIZE, 2049},
            {BtrfsConstants.SB_NODESIZE, 0xFFFFFFFF},             // used to be NegativeArraySizeException
            {BtrfsConstants.SB_NODESIZE, 6000},                   // not a power of two
            {BtrfsConstants.SB_NODESIZE, 128 * 1024},             // above 64 KiB
            {BtrfsConstants.SB_SECTORSIZE, 0},
            {BtrfsConstants.SB_SECTORSIZE, 8192},                 // now larger than the 4 KiB node size
        };
        for (int[] c : cases) {
            SyntheticBtrfs img = volumeWithSuperblockField(c[0], c[1]);
            String what = String.format("superblock field 0x%x = 0x%x", c[0], c[1]);
            assertIOException(what, io(() -> new BtrfsVolume(img.reader())));
        }

        // and through the FileSystemType, the documented checked failure
        File f = volumeWithSuperblockField(BtrfsConstants.SB_NODESIZE, 0xFFFFFFFF).toFile();
        try (FileDevice device = new FileDevice(f, "r")) {
            new BtrfsFileSystemType().create(device, true);
            fail("expected a FileSystemException");
        } catch (FileSystemException expected) {
            // not a NegativeArraySizeException
        }
    }

    // ---- efficiency ----

    @Test
    public void repeatedNodeReadsDescendTheTreeOnce() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long leaf = simpleFsTree(img, "f");
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, leaf, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        BtrfsNode file = child(vol.getRoot(), "f");
        int before = img.readsOf(leaf);
        byte[] one = new byte[1];
        for (int i = 0; i < 5; i++) {
            assertEquals(1, file.read(i, one, 0, 1));
            assertEquals("hello".charAt(i), one[0]);
        }
        assertEquals("five 1-byte reads cost one extent lookup, not five", before + 1, img.readsOf(leaf));
    }

    @Test
    public void openingSubvolumesDoesNotRescanTheRootTree() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long home = simpleFsTree(img, "h");
        long var = simpleFsTree(img, "v");
        long top = img.allocBlock();
        img.leaf(top, Arrays.asList(
                SyntheticBtrfs.dir(ROOT_DIR),
                SyntheticBtrfs.subvolEntry(ROOT_DIR, 2, "home", 257),
                SyntheticBtrfs.subvolEntry(ROOT_DIR, 3, "var", 258)));
        long rootTree = img.rootTree(
                SyntheticBtrfs.rootItem(FS, top, ROOT_DIR, 0, false),
                SyntheticBtrfs.rootItem(257, home, ROOT_DIR, 0, false),
                SyntheticBtrfs.rootItem(258, var, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        assertEquals(1, img.readsOf(rootTree));
        for (BtrfsNode sub : vol.getRoot().getChildren()) {
            assertEquals(1, sub.getChildren().size());
        }
        assertEquals("the root tree is read once at mount, never per subvolume", 1, img.readsOf(rootTree));
    }

    @Test
    public void fileReadIntoABufferSliceLandsAtThePosition() throws Exception {
        // a regular extent read straight into the caller's array (the hasArray path), with the
        // destination a window into a larger buffer
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        byte[] content = new byte[6000];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) ('a' + i % 26);
        }
        img.data(SyntheticBtrfs.DATA_AREA, content);
        long fs = img.allocBlock();
        img.leaf(fs, Arrays.asList(
                SyntheticBtrfs.dir(ROOT_DIR),
                SyntheticBtrfs.fileEntry(ROOT_DIR, 2, "big", 300),
                SyntheticBtrfs.file(300, content.length),
                SyntheticBtrfs.regularExtent(300, 0, SyntheticBtrfs.DATA_AREA, 8192)));
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, fs, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        File f = img.toFile();
        try (FileDevice device = new FileDevice(f, "r")) {
            BtrfsFileSystem fsys = new BtrfsFileSystemType().create(device, true);
            FSFile file = fsys.getRootEntry().getDirectory().getEntry("big").getFile();
            byte[] dst = new byte[100 + 2500 + 100];
            Arrays.fill(dst, (byte) '#');
            ByteBuffer window = ByteBuffer.wrap(dst, 100, 2500);
            file.read(1000, window);
            assertEquals(2600, window.position());
            assertArrayEquals(Arrays.copyOfRange(content, 1000, 3500), Arrays.copyOfRange(dst, 100, 2600));
            assertEquals('#', dst[99]);
            assertEquals('#', dst[2600]);
        }
    }

    @Test
    public void inlineSliceReadsTheRequestedBytesAndZeroFillsBeyondTheData() throws Exception {
        SyntheticBtrfs img = new SyntheticBtrfs();
        long chunkTree = img.chunkTree();
        long fs = img.allocBlock();
        byte[] inline = "helloworld".getBytes(StandardCharsets.UTF_8);
        img.leaf(fs, Arrays.asList(
                SyntheticBtrfs.dir(ROOT_DIR),
                SyntheticBtrfs.fileEntry(ROOT_DIR, 2, "t", 300),
                SyntheticBtrfs.file(300, 16), // the inode says 16 bytes; the inline extent holds 10
                SyntheticBtrfs.inlineExtent(300, inline)));
        long rootTree = img.rootTree(SyntheticBtrfs.rootItem(FS, fs, ROOT_DIR, 0, false));
        img.superblock(rootTree, chunkTree);

        BtrfsVolume vol = new BtrfsVolume(img.reader());
        BtrfsNode t = child(vol.getRoot(), "t");
        byte[] mid = new byte[4];
        assertEquals(4, t.read(3, mid, 0, 4));
        assertEquals("lowo", new String(mid, StandardCharsets.UTF_8));
        byte[] all = new byte[16];
        Arrays.fill(all, (byte) 1);
        assertEquals(16, t.read(0, all, 0, 16));
        assertEquals("helloworld", new String(all, 0, 10, StandardCharsets.UTF_8));
        assertArrayEquals(new byte[6], Arrays.copyOfRange(all, 10, 16));
    }

    @Test
    public void directoryIterationStillWorksOverTheSyntheticVolume() throws Exception {
        // sanity for the builder itself: the FileSystem-level walk sees what was written
        SyntheticBtrfs img = rootIsASnapshot(0);
        File f = img.toFile();
        try (FileDevice device = new FileDevice(f, "r")) {
            BtrfsFileSystem fsys = new BtrfsFileSystemType().create(device, true);
            assertEquals("synthetic", fsys.getVolumeName());
            Iterator<? extends FSEntry> it = fsys.getRootEntry().getDirectory().iterator();
            assertTrue(it.hasNext());
            FSEntry at = it.next();
            assertEquals("@", at.getName());
            assertEquals("etc", at.getDirectory().iterator().next().getName());
        }
    }
}
