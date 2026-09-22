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
 * End-to-end lzo: reads a real btrfs image whose files were written through {@code
 * mount -o compress-force=lzo} (all three EXTENT_DATA items report {@code compression 2} per
 * {@code dump-tree}). This is what proves our btrfs lzo framing <em>and</em> the aircompressor
 * {@code lzo1x} payload handling match the kernel byte-for-byte — the decode-layer test
 * ({@link BtrfsLzoDecodeTest}) only checks our framing against a symmetric encoder.
 *
 * <p>Covers an <b>inline</b> lzo extent ({@code tiny.txt}, one segment) and a <b>full 128 KiB
 * multi-segment</b> regular extent ({@code big.log}, ~32 segments — this is the path that exercises
 * the per-segment length prefixes and the sector-header padding), plus a mid-size regular one.
 */
public class BtrfsLzoFileSystemTest {

    private File testFile;

    @Before
    public void setUp() throws Exception {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-lzo.img");
    }

    @After
    public void tearDown() {
        if (testFile != null) {
            testFile.delete();
        }
    }

    @Test
    public void readsLzoCompressedContent() throws Exception {
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);

            Map<String, FSEntry> byPath = new HashMap<String, FSEntry>();
            collect(fs.getRootEntry().getDirectory(), "", byPath);

            assertTrue("files: " + byPath.keySet(), byPath.containsKey("big.log"));
            assertTrue(byPath.containsKey("docs/tiny.txt"));
            assertTrue(byPath.containsKey("docs/mid.txt"));
            assertEquals(131072, byPath.get("big.log").getFile().getLength());
            assertEquals(35, byPath.get("docs/tiny.txt").getFile().getLength());
            assertEquals(20000, byPath.get("docs/mid.txt").getFile().getLength());

            // decompressed bytes are exactly what was written
            assertEquals("hello lzo world, compressed inline\n", read(byPath.get("docs/tiny.txt")));
            assertArrayEquals("128 KiB multi-segment lzo extent",
                    yesHead("the quick brown fox jumps over the lazy dog 0123456789", 131072),
                    readBytes(byPath.get("big.log"), 131072));
            assertArrayEquals("mid-size lzo extent",
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
