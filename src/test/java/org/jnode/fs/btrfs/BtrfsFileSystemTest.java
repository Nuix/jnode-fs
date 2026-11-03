package org.jnode.fs.btrfs;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FileSystemTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Reads a real btrfs image (default single-data / DUP-metadata profile, modern incompat flags:
 * BIG_METADATA + SKINNY_METADATA + NO_HOLES) produced with {@code mkfs.btrfs -r}. Verifies the
 * directory tree, file sizes, and file contents (inline + regular extents).
 */
public class BtrfsFileSystemTest {

    private File testFile;

    @Before
    public void setUp() throws Exception {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-simple.img");
    }

    @After
    public void tearDown() {
        if (testFile != null) {
            testFile.delete();
        }
    }

    @Test
    public void readsTreeSizesAndContent() throws Exception {
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystemType type = new BtrfsFileSystemType();
            BtrfsFileSystem fs = type.create(device, true);

            Map<String, FSEntry> byPath = new HashMap<String, FSEntry>();
            collect(fs.getRootEntry().getDirectory(), "", byPath);

            // structure
            assertTrue("readme.txt at root: " + byPath.keySet(), byPath.containsKey("readme.txt"));
            assertTrue(byPath.containsKey("docs"));
            assertTrue(byPath.containsKey("data"));
            assertTrue(byPath.containsKey("docs/notes.md"));
            assertTrue(byPath.containsKey("docs/tiny.txt"));
            assertTrue(byPath.containsKey("data/repeated.log"));
            assertTrue("docs is a directory", byPath.get("docs").isDirectory());

            // sizes (from the inode)
            assertEquals(17, byPath.get("readme.txt").getFile().getLength());
            assertEquals(23, byPath.get("docs/notes.md").getFile().getLength());
            assertEquals(1, byPath.get("docs/tiny.txt").getFile().getLength());
            assertEquals(5000, byPath.get("data/repeated.log").getFile().getLength());

            // content (inline extents)
            assertEquals("hello btrfs world", read(byPath.get("readme.txt")));
            assertEquals("nested markdown content", read(byPath.get("docs/notes.md")));
            assertEquals("x", read(byPath.get("docs/tiny.txt")));

            // content (regular extent): 5000 'A's, read the exact bytes back
            byte[] expected = new byte[5000];
            java.util.Arrays.fill(expected, (byte) 'A');
            assertArrayEquals(expected, readBytes(byPath.get("data/repeated.log"), 5000));

            // total/free space plausible for a 300 MB image
            assertTrue(fs.getTotalSpace() > 100L * 1024 * 1024);
            assertTrue(fs.getFreeSpace() > 0 && fs.getFreeSpace() <= fs.getTotalSpace());
        }
    }

    private static void collect(FSDirectory dir, String prefix, Map<String, FSEntry> out) throws Exception {
        java.util.Iterator<? extends FSEntry> it = dir.iterator();
        while (it.hasNext()) {
            FSEntry e = it.next();
            String name = e.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String path = prefix.isEmpty() ? name : prefix + "/" + name;
            out.put(path, e);
            if (e.isDirectory()) {
                collect(e.getDirectory(), path, out);
            }
        }
    }

    private static String read(FSEntry entry) throws Exception {
        int len = (int) entry.getFile().getLength();
        return new String(readBytes(entry, len), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(FSEntry entry, int len) throws Exception {
        ByteBuffer buf = ByteBuffer.allocate(len);
        entry.getFile().read(0, buf);
        return buf.array();
    }
}
