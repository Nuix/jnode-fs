package org.jnode.fs.ntfs.wof;

import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.FileRecord;
import org.jnode.fs.ntfs.NTFSFile;
import org.jnode.fs.ntfs.NTFSFileSystem;
import org.jnode.fs.ntfs.NTFSFileSystemType;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

/**
 * Damaged input must fail with an {@link IOException}, never a runtime exception or a hang. The real chunks come
 * from {@code ntfs-wof}; their correct decoding is covered by {@code NTFSWofImageTest}.
 */
public class WofDecompressorTest {

    @Test
    public void testE8TranslationIsUndone() {
        byte[] data = new byte[64];
        putCall(data, 0, 100);              // a target ahead: 100 - 0
        putCall(data, 20, 5);               // a target behind: 5 - 20
        putCall(data, 30, -10);             // the compensating case: -10 + 12000000
        putCall(data, 40, 12000000);        // out of range, left alone
        putCall(data, 54, 7);               // too close to the end, left alone

        LzxDecompressor.undoE8Translation(data, data.length);

        byte[] expected = new byte[64];
        putCall(expected, 0, 100);
        putCall(expected, 20, -15);
        putCall(expected, 30, 12000000 - 10);
        putCall(expected, 40, 12000000);
        putCall(expected, 54, 7);
        assertArrayEquals(expected, data);
    }

    /**
     * The four target bytes are skipped, so an E8 among them is not itself a call.
     */
    @Test
    public void testE8InsideATargetIsNotTranslated() {
        byte[] data = new byte[32];
        putCall(data, 0, 0x00e80000);       // out of range, but its third byte is E8

        LzxDecompressor.undoE8Translation(data, data.length);

        byte[] expected = new byte[32];
        putCall(expected, 0, 0x00e80000);
        assertArrayEquals(expected, data);
    }

    @Test
    public void testDamagedXpressChunks() throws Exception {
        fuzz("xpress4k/text-1m-plus-7.txt", WofCompressionFormat.XPRESS4K);
        fuzz("xpress16k/mixed-1m-plus-333.bin", WofCompressionFormat.XPRESS16K);
    }

    @Test
    public void testDamagedLzxChunks() throws Exception {
        fuzz("lzx/text-1m-plus-7.txt", WofCompressionFormat.LZX);
        fuzz("lzx/mixed-1m-plus-333.bin", WofCompressionFormat.LZX);
    }

    @Test
    public void testTruncatedChunksAreRejected() throws Exception {
        for (WofCompressionFormat format : new WofCompressionFormat[] {
            WofCompressionFormat.XPRESS4K, WofCompressionFormat.LZX}) {

            String path = format == WofCompressionFormat.LZX ? "lzx/text-1m-plus-7.txt" : "xpress4k/text-1m-plus-7.txt";
            byte[] chunk = firstChunk(path, format);
            byte[] out = new byte[format.getChunkSize()];

            for (int length : new int[] {0, 1, chunk.length / 4, chunk.length / 2}) {
                assertThrows(path + " cut to " + length, IOException.class,
                    () -> format.createDecompressor().decompress(chunk, 0, length, out, out.length));
            }
        }
    }

    private static void fuzz(String path, WofCompressionFormat format) throws Exception {
        byte[] chunk = firstChunk(path, format);
        byte[] expected = new byte[format.getChunkSize()];
        format.createDecompressor().decompress(chunk, 0, chunk.length, expected, expected.length);

        Random random = new Random(path.hashCode());
        byte[] out = new byte[format.getChunkSize()];
        WofDecompressor decompressor = format.createDecompressor();

        for (int round = 0; round < 2000; round++) {
            byte[] damaged = Arrays.copyOf(chunk, chunk.length);
            int flips = 1 + random.nextInt(4);
            for (int i = 0; i < flips; i++) {
                damaged[random.nextInt(damaged.length)] ^= (byte) (1 << random.nextInt(8));
            }

            try {
                decompressor.decompress(damaged, 0, damaged.length, out, out.length);
            } catch (IOException e) {
                // Expected for most damage; anything else propagates and fails the test
            }
        }

        // Reusing a decompressor after failures must not leave state behind
        decompressor.decompress(chunk, 0, chunk.length, out, out.length);
        assertArrayEquals(path, expected, out);
    }

    /**
     * Gets the first compressed chunk of a file in {@code ntfs-wof}, which for these files spans a whole chunk.
     */
    private static byte[] firstChunk(String path, WofCompressionFormat format) throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/ntfs-wof.raw")) {
            NTFSFileSystem fs = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName())
                .getFileSystemType(NTFSFileSystemType.ID).create(device, true);

            FSEntry entry = fs.getRootEntry();
            for (String name : path.split("/")) {
                entry = entry.getDirectory().getEntry(name);
            }
            FileRecord record = ((NTFSFile) entry.getFile()).getFileRecord();

            long fileSize = record.getAttributeTotalSize(NTFSAttribute.Types.DATA, null);
            int entrySize = fileSize > 0xffffffffL ? 8 : 4;
            long chunks = (fileSize + format.getChunkSize() - 1) / format.getChunkSize();
            long tableSize = (chunks - 1) * entrySize;

            byte[] firstEntry = new byte[4];
            record.readData(NTFSAttribute.Types.DATA, FileRecord.WOF_STREAM_NAME, 0, firstEntry, 0, 4, true);
            int length = (firstEntry[0] & 0xff) | (firstEntry[1] & 0xff) << 8 | (firstEntry[2] & 0xff) << 16 |
                (firstEntry[3] & 0xff) << 24;

            byte[] chunk = new byte[length];
            record.readData(NTFSAttribute.Types.DATA, FileRecord.WOF_STREAM_NAME, tableSize, chunk, 0, length, true);
            return chunk;
        }
    }

    private static void putCall(byte[] data, int position, int target) {
        data[position] = (byte) 0xe8;
        data[position + 1] = (byte) target;
        data[position + 2] = (byte) (target >>> 8);
        data[position + 3] = (byte) (target >>> 16);
        data[position + 4] = (byte) (target >>> 24);
    }
}
