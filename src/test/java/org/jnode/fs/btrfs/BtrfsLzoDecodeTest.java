package org.jnode.fs.btrfs;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import io.airlift.compress.lzo.LzoCompressor;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * The btrfs lzo framing/decode ({@link BtrfsFileContent#decompress} with COMPRESS_LZO). Real lzo
 * btrfs fixtures need a privileged {@code mount -o compress-force=lzo}, so here we build btrfs's exact
 * on-disk framing with a symmetric encoder — a 4-byte LE total length, then segments (4-byte LE
 * payload length + an aircompressor {@code lzo1x} block, each &lt;= one sector), with the segment
 * header kept from crossing a sector boundary (up to 3 tail padding bytes) — and assert it
 * round-trips. This proves the framing plumbing; kernel-compat of the {@code lzo1x} payload itself is
 * proven end-to-end against a real image ({@code BtrfsLzoFileSystemTest}).
 */
public class BtrfsLzoDecodeTest {

    /** Reproduce btrfs's on-disk lzo layout for {@code data}, splitting into {@code sectorSize} chunks. */
    private static byte[] frameLikeBtrfs(byte[] data, int sectorSize) {
        LzoCompressor c = new LzoCompressor();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLe32(out, 0); // total-length header placeholder (offset 0)
        int off = 0;
        do {
            int chunk = Math.min(sectorSize, data.length - off);
            byte[] block = new byte[c.maxCompressedLength(chunk)];
            int blen = c.compress(data, off, chunk, block, 0, block.length);
            int sectorLeft = sectorSize - (out.size() % sectorSize);
            if (sectorLeft < 4) {
                for (int i = 0; i < sectorLeft; i++) {
                    out.write(0); // pad so the segment header does not cross a sector boundary
                }
            }
            writeLe32(out, blen);
            out.write(block, 0, blen);
            off += chunk;
        } while (off < data.length);
        byte[] all = out.toByteArray();
        int total = all.length; // total length includes the header
        all[0] = (byte) total;
        all[1] = (byte) (total >>> 8);
        all[2] = (byte) (total >>> 16);
        all[3] = (byte) (total >>> 24);
        return all;
    }

    private static void writeLe32(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 24) & 0xFF);
    }

    private static byte[] repeat(String line, int n) {
        byte[] unit = (line + "\n").getBytes();
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = unit[i % unit.length];
        }
        return out;
    }

    @Test
    public void decodesASingleSegment() throws Exception {
        byte[] data = "hello lzo world, one small segment\n".getBytes("UTF-8");
        byte[] out = BtrfsFileContent.decompress(
                BtrfsConstants.COMPRESS_LZO, frameLikeBtrfs(data, 4096), data.length, 4096);
        assertArrayEquals(data, out);
    }

    @Test
    public void decodesMultipleSegments() throws Exception {
        // > one sector -> several segments; a full 128 KiB extent's worth
        byte[] data = repeat("the quick brown fox jumps over the lazy dog 0123456789", 131072);
        byte[] out = BtrfsFileContent.decompress(
                BtrfsConstants.COMPRESS_LZO, frameLikeBtrfs(data, 4096), data.length, 4096);
        assertArrayEquals(data, out);
    }

    @Test
    public void decodesAcrossSectorHeaderPadding() throws Exception {
        // a tiny sector size makes segment headers land at sector tails, forcing the <4-byte padding
        // branch in both the encoder and the decoder
        byte[] data = repeat("pad", 900);
        for (int sector : new int[] {16, 32, 40, 64}) {
            byte[] out = BtrfsFileContent.decompress(
                    BtrfsConstants.COMPRESS_LZO, frameLikeBtrfs(data, sector), data.length, sector);
            assertArrayEquals("sector=" + sector, data, out);
        }
    }

    @Test
    public void returnsOnlyTheLeadingRamBytes() throws Exception {
        // the decoded extent may run past the file's logical size; only ramBytes are kept
        byte[] data = repeat("abcdef", 4096);
        byte[] out = BtrfsFileContent.decompress(
                BtrfsConstants.COMPRESS_LZO, frameLikeBtrfs(data, 4096), 1000, 4096);
        assertEquals(1000, out.length);
        assertArrayEquals(Arrays.copyOf(data, 1000), out);
    }

    @Test
    public void truncatedHeaderRaisesAReadableError() {
        // (lzo1x is permissive enough that random bytes often "decode" to garbage rather than error,
        // so we assert the deterministic guard: an extent too short to even hold the length header)
        try {
            BtrfsFileContent.decompress(BtrfsConstants.COMPRESS_LZO, new byte[] {1, 2}, 100, 4096);
            fail("expected an IOException for a truncated lzo header");
        } catch (Exception ex) {
            assertEquals(java.io.IOException.class, ex.getClass());
            org.junit.Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("lzo"));
        }
    }
}
