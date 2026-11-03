package org.jnode.fs.btrfs;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jnode.fs.FileSystemTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Locks the keyed B-tree {@link BtrfsTree.Cursor} against the trusted full-tree {@link
 * BtrfsTree#scanLeaves scan}: for the same tree, cursor iteration must yield exactly the same items,
 * in the same order, with the same bytes. Uses a multi-node fixture (an FS tree at level 1 with ~60
 * leaves) so the cursor really descends internal nodes and crosses leaf boundaries — the tiny
 * single-leaf {@code btrfs-simple} fixture would not exercise either.
 */
public class BtrfsTreeCursorTest {

    private File testFile;
    private RandomAccessFile raf;
    private BtrfsVolume volume;
    private long fsRoot;

    @Before
    public void setUp() throws Exception {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-multinode.img");
        raf = new RandomAccessFile(testFile, "r");
        BtrfsBlockReader reader = (offset, dst, off, length) -> {
            raf.seek(offset);
            raf.readFully(dst, off, length);
        };
        volume = new BtrfsVolume(reader);
        fsRoot = volume.subvolBytenr(BtrfsConstants.OBJECTID_FS_TREE);
    }

    @After
    public void tearDown() throws Exception {
        if (raf != null) {
            raf.close();
        }
        if (testFile != null) {
            testFile.delete();
        }
    }

    /** A stable signature of an item: its key plus a fingerprint of its data bytes. */
    private static String sig(BtrfsDiskKey key, byte[] data) {
        return key.getObjectId() + "/" + key.getType() + "/" + key.getOffset()
                + " len=" + data.length + " h=" + Arrays.hashCode(data);
    }

    @Test
    public void cursorFromMinimumWalksTheWholeTreeExactlyLikeAFullScan() throws Exception {
        List<String> scanned = new ArrayList<String>();
        volume.tree().scanLeaves(fsRoot, (key, data) -> scanned.add(sig(key, data)));

        List<String> cursored = new ArrayList<String>();
        BtrfsTree.Cursor c = volume.tree().search(fsRoot, new BtrfsDiskKey(0, 0, 0)); // before every key
        while (c.valid()) {
            cursored.add(sig(c.key(), c.data()));
            c.next();
        }

        assertTrue("fixture should be multi-leaf (" + scanned.size() + " items)", scanned.size() > 2000);
        assertEquals("cursor order + bytes must match the full scan exactly", scanned, cursored);
    }

    @Test
    public void keyedRangeMatchesAScanFilterForEveryInode() throws Exception {
        // Reference: every inode's EXTENT_DATA items, grouped in scan (== key) order.
        Map<Long, List<String>> byInode = new LinkedHashMap<Long, List<String>>();
        volume.tree().scanLeaves(fsRoot, (key, data) -> {
            if (key.getType() == BtrfsConstants.TYPE_EXTENT_DATA) {
                byInode.computeIfAbsent(key.getObjectId(), k -> new ArrayList<String>()).add(sig(key, data));
            }
        });
        assertTrue("fixture should have many files with content", byInode.size() > 100);

        for (Map.Entry<Long, List<String>> e : byInode.entrySet()) {
            long objectId = e.getKey();
            List<String> got = new ArrayList<String>();
            BtrfsTree.Cursor c = volume.tree().search(fsRoot,
                    new BtrfsDiskKey(objectId, BtrfsConstants.TYPE_EXTENT_DATA, 0));
            while (c.valid()) {
                BtrfsDiskKey k = c.key();
                if (k.getObjectId() != objectId || k.getType() != BtrfsConstants.TYPE_EXTENT_DATA) {
                    break; // walked past this inode's extent range
                }
                got.add(sig(k, c.data()));
                c.next();
            }
            assertEquals("extents for inode " + objectId, e.getValue(), got);
        }
    }

    @Test
    public void searchForAnAbsentKeyLandsOnTheNextItem() throws Exception {
        // Collect the FS tree's keys in order, then probe between two known keys.
        List<BtrfsDiskKey> keys = new ArrayList<BtrfsDiskKey>();
        volume.tree().scanLeaves(fsRoot, (key, data) -> keys.add(key));
        assertTrue(keys.size() > 2000);

        // A key strictly between item[500] and its successor: same objectid/type, offset+1 won't
        // exist as an EXTENT/REF key here, so the lower bound must be the very next distinct key.
        int i = 500;
        BtrfsDiskKey base = keys.get(i);
        BtrfsDiskKey probe = new BtrfsDiskKey(base.getObjectId(), base.getType(), base.getOffset() + 1);
        // expected = first real key strictly greater than probe-1, i.e. the first key >= probe
        BtrfsDiskKey expected = null;
        for (BtrfsDiskKey k : keys) {
            if (k.compareTo(probe) >= 0) {
                expected = k;
                break;
            }
        }

        BtrfsTree.Cursor c = volume.tree().search(fsRoot, probe);
        assertTrue(c.valid());
        assertEquals(0, expected.compareTo(c.key()));
    }

    @Test
    public void searchBeyondTheLastKeyIsNotValid() throws Exception {
        BtrfsTree.Cursor c = volume.tree().search(fsRoot,
                new BtrfsDiskKey(-1L, 0xFF, -1L)); // 0xFFFF... unsigned = past every real key
        assertFalse(c.valid());
    }

    /**
     * End-to-end: file content read through the now keyed {@link BtrfsFileContent#collectExtents}
     * still returns the right bytes on a multi-node tree — a regular extent (20 KB) and a small
     * inline file whose inode lives on a non-root leaf.
     */
    @Test
    public void contentReadsCorrectlyViaTheKeyedExtentPath() throws Exception {
        BtrfsNode nested = child(volume.getRoot(), "nested");
        BtrfsNode bigA = child(nested, "big_a.bin");
        assertEquals(20000, bigA.getSize());
        byte[] expectedA = new byte[20000];
        Arrays.fill(expectedA, (byte) 'A');
        assertArrayEquals(expectedA, readAll(bigA));

        BtrfsNode item = child(child(volume.getRoot(), "gamma"), "item_gamma_200.txt");
        assertEquals("file gamma/200 contents 200\n",
                new String(readAll(item), StandardCharsets.UTF_8));
    }

    private static BtrfsNode child(BtrfsNode dir, String name) throws Exception {
        for (BtrfsNode n : dir.getChildren()) {
            if (name.equals(n.getName())) {
                return n;
            }
        }
        assertNotNull("child not found: " + name, null);
        return null;
    }

    private static byte[] readAll(BtrfsNode file) throws Exception {
        byte[] buf = new byte[(int) file.getSize()];
        int pos = 0;
        while (pos < buf.length) {
            int n = file.read(pos, buf, pos, buf.length - pos);
            if (n <= 0) {
                break;
            }
            pos += n;
        }
        assertEquals("read the whole file", buf.length, pos);
        return buf;
    }
}
