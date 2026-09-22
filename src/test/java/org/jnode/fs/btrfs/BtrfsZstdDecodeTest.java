package org.jnode.fs.btrfs;

import java.util.Arrays;
import java.util.Random;

import io.airlift.compress.zstd.ZstdCompressor;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * The btrfs zstd decode path ({@link BtrfsFileContent#decompress}). Real zstd-compressed btrfs
 * fixtures need root (a {@code mount -o compress=zstd} + writes), so instead we exercise the decoder
 * directly: compress with aircompressor, reproduce btrfs's on-disk framing — a single zstd frame
 * padded with zeros up to the sector size ({@code disk_num_bytes} is rounded up) — and assert the
 * whole extent ({@code ram_bytes}) comes back byte-for-byte. The padding case is the one that would
 * trip a naive "decode the whole buffer" decoder, so it is the point of the test.
 */
public class BtrfsZstdDecodeTest {

    private static final int SECTOR = 4096;

    /** A zstd frame padded with trailing zeros to a sector boundary, exactly as btrfs stores it. */
    private static byte[] compressAsBtrfsOnDisk(byte[] data) {
        ZstdCompressor c = new ZstdCompressor();
        byte[] scratch = new byte[c.maxCompressedLength(data.length)];
        int n = c.compress(data, 0, data.length, scratch, 0, scratch.length);
        int padded = ((n + SECTOR - 1) / SECTOR) * SECTOR;
        return Arrays.copyOf(scratch, padded); // frame + trailing zero padding
    }

    @Test
    public void decodesAZstdExtentThroughItsSectorPadding() throws Exception {
        byte[] data = "the quick brown fox jumps over the lazy dog, repeatedly and compressibly"
                .getBytes("UTF-8");
        byte[] onDisk = compressAsBtrfsOnDisk(data);

        byte[] out = BtrfsFileContent.decompress(BtrfsConstants.COMPRESS_ZSTD, onDisk, data.length);

        assertArrayEquals(data, out);
    }

    @Test
    public void decodesAHighlyCompressibleExtent() throws Exception {
        // a log-like extent: the frame is far smaller than one sector, so it is almost all padding
        byte[] data = new byte[5000];
        Arrays.fill(data, (byte) 'A');

        byte[] out = BtrfsFileContent.decompress(
                BtrfsConstants.COMPRESS_ZSTD, compressAsBtrfsOnDisk(data), data.length);

        assertArrayEquals(data, out);
    }

    @Test
    public void decodesAFullMaxSizeExtent() throws Exception {
        // btrfs caps a compressed extent at 128 KiB uncompressed; use mixed data so it spans several
        // sectors of real compressed output (not just padding)
        byte[] data = new byte[128 * 1024];
        Random rng = new Random(20260707L);
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (rng.nextInt(8) == 0 ? rng.nextInt(256) : 'x'); // mostly 'x', some noise
        }

        byte[] out = BtrfsFileContent.decompress(
                BtrfsConstants.COMPRESS_ZSTD, compressAsBtrfsOnDisk(data), data.length);

        assertArrayEquals(data, out);
    }

    @Test
    public void aFrameThatDecodesToMoreThanRamBytesIsTruncated() throws Exception {
        // btrfs inline extents compress a whole page, but ram_bytes is the real (smaller) file size:
        // the frame's declared content size (here 4096) exceeds ram_bytes, so the decoder must size to
        // the frame and return only the leading ram_bytes -- decoding straight into ram_bytes overflows.
        byte[] real = "hello zstd world, compressed inline\n".getBytes("UTF-8");
        byte[] page = new byte[4096];
        System.arraycopy(real, 0, page, 0, real.length); // remainder stays zero (page padding)

        byte[] out = BtrfsFileContent.decompress(
                BtrfsConstants.COMPRESS_ZSTD, compressAsBtrfsOnDisk(page), real.length);

        assertArrayEquals(real, out);
    }

    @Test
    public void zeroLengthExtentDecodesToEmpty() throws Exception {
        byte[] out = BtrfsFileContent.decompress(BtrfsConstants.COMPRESS_ZSTD, new byte[SECTOR], 0);
        assertEquals(0, out.length);
    }

    @Test
    public void uncompressedExtentIsReturnedAsIs() throws Exception {
        byte[] raw = { 1, 2, 3, 4, 5 };
        // NONE must not copy or transform -- the caller slices the returned array directly
        assertSame(raw, BtrfsFileContent.decompress(BtrfsConstants.COMPRESS_NONE, raw, raw.length));
    }

    @Test
    public void corruptZstdRaisesAReadableError() {
        byte[] garbage = new byte[SECTOR]; // no zstd magic
        Arrays.fill(garbage, (byte) 0x7f);
        try {
            BtrfsFileContent.decompress(BtrfsConstants.COMPRESS_ZSTD, garbage, 1024);
            fail("expected an IOException for non-zstd input");
        } catch (Exception ex) {
            assertEquals(java.io.IOException.class, ex.getClass());
            org.junit.Assert.assertTrue(ex.getMessage(), ex.getMessage().contains("zstd"));
        }
    }
}
