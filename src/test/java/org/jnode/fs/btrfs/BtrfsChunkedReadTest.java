package org.jnode.fs.btrfs;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FSFile;
import org.jnode.fs.FileSystemTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * Reading a file in many small, oddly-sized chunks must yield exactly the same bytes as one whole
 * read — across both read paths: <b>uncompressed regular</b> extents (now fetched by range, never
 * the whole extent) and <b>compressed</b> extents (materialised whole through the per-handle
 * one-slot cache). Chunk sizes are deliberately not sector- or extent-aligned.
 *
 * @author David Baird
 */
public class BtrfsChunkedReadTest {

    @Test
    public void uncompressedFileReadsIdenticallyInOddChunks() throws Exception {
        // multinode fixture: nested/big_a.bin is a 20000-byte uncompressed regular extent
        assertChunkedEqualsWhole("org/jnode/fs/btrfs/btrfs-multinode.img", "nested/big_a.bin", 7001);
    }

    @Test
    public void compressedFileReadsIdenticallyInOddChunks() throws Exception {
        // zstd fixture: big.log is a 128 KiB extent -> exercises the decompressed-extent cache
        assertChunkedEqualsWhole("org/jnode/fs/btrfs/btrfs-zstd.img", "big.log", 9973);
    }

    private static void assertChunkedEqualsWhole(String image, String path, int chunk) throws Exception {
        File testFile = FileSystemTestUtils.getTestFile(image);
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);
            Map<String, FSEntry> byPath = new HashMap<String, FSEntry>();
            collect(fs.getRootEntry().getDirectory(), "", byPath);

            FSFile file = byPath.get(path).getFile();
            int size = (int) file.getLength();

            byte[] whole = new byte[size];
            file.read(0, ByteBuffer.wrap(whole));

            byte[] chunked = new byte[size];
            FSFile again = byPath.get(path).getFile(); // a fresh handle (fresh cache)
            for (int pos = 0; pos < size; pos += chunk) {
                int n = Math.min(chunk, size - pos);
                again.read(pos, ByteBuffer.wrap(chunked, pos, n));
            }

            assertArrayEquals(path + " chunked != whole", whole, chunked);
        } finally {
            testFile.delete();
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
}
