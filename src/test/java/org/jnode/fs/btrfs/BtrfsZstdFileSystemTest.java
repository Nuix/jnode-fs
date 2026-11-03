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
import static org.junit.Assert.assertTrue;

/**
 * End-to-end zstd: reads a real btrfs image whose files were written through {@code
 * mount -o compress-force=zstd} (so the extents are genuinely zstd, verified with {@code
 * btrfs inspect-internal dump-tree}: all three EXTENT_DATA items report {@code compression 3}).
 * This closes the loop that {@link BtrfsZstdDecodeTest} can only reach at the decode layer — here the
 * bytes travel the whole path: FS-tree lookup → EXTENT_DATA → chunk map → on-disk frame → decode.
 *
 * <p>Covers an <b>inline</b> zstd extent ({@code tiny.txt}, whose 36 raw bytes "compress" to a larger
 * 55-byte frame — real, and handled), a <b>full 128 KiB regular</b> zstd extent ({@code big.log},
 * several on-disk sectors before it compressed to one), and a mid-size regular one ({@code mid.txt}).
 */
public class BtrfsZstdFileSystemTest {

    private File testFile;

    @Before
    public void setUp() throws Exception {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-zstd.img");
    }

    @After
    public void tearDown() {
        if (testFile != null) {
            testFile.delete();
        }
    }

    @Test
    public void readsZstdCompressedContent() throws Exception {
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);

            Map<String, FSEntry> byPath = new HashMap<String, FSEntry>();
            collect(fs.getRootEntry().getDirectory(), "", byPath);

            // structure + sizes (from the inode; independent of compression)
            assertTrue("files: " + byPath.keySet(), byPath.containsKey("big.log"));
            assertTrue(byPath.containsKey("docs/tiny.txt"));
            assertTrue(byPath.containsKey("docs/mid.txt"));
            assertEquals(131072, byPath.get("big.log").getFile().getLength());
            assertEquals(36, byPath.get("docs/tiny.txt").getFile().getLength());
            assertEquals(20000, byPath.get("docs/mid.txt").getFile().getLength());

            // the point: the decompressed bytes are exactly what was written
            assertEquals("hello zstd world, compressed inline\n", read(byPath.get("docs/tiny.txt")));
            assertArrayEquals("128 KiB regular zstd extent",
                    yesHead("the quick brown fox jumps over the lazy dog 0123456789", 131072),
                    readBytes(byPath.get("big.log"), 131072));
            assertArrayEquals("mid-size regular zstd extent",
                    yesHead("repeated content line for compression", 20000),
                    readBytes(byPath.get("docs/mid.txt"), 20000));
        }
    }

    /** Reproduces {@code yes 'line' | head -c n}: the line plus a newline, repeated then truncated. */
    private static byte[] yesHead(String line, int n) {
        byte[] unit = (line + "\n").getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = unit[i % unit.length];
        }
        return out;
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
