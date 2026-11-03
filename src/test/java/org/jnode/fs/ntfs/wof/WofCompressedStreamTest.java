package org.jnode.fs.ntfs.wof;

import java.io.IOException;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertThrows;

/**
 * The chunk table and chunk lookup, over streams of chunks stored uncompressed so no decompressor is involved.
 */
public class WofCompressedStreamTest {

    /**
     * Past 4 GiB - 1 the table entries widen to 8 bytes. The stream is generated on demand rather than held, as it
     * is as large as the file.
     */
    @Test
    public void testEightByteEntriesPastFourGiB() throws Exception {
        long size = 0x100000000L + 12345;
        StoredStream stored = new StoredStream(size, WofCompressionFormat.XPRESS16K, 8);
        WofCompressedStream stream = stored.open();

        for (long offset : new long[] {0, 16383, 0xffffffffL - 3, 0x100000000L, size - 100}) {
            byte[] actual = new byte[100];
            stream.read(offset, actual, 0, actual.length);
            assertArrayEquals("offset " + offset, stored.expected(offset, actual.length), actual);
        }
    }

    /**
     * At exactly 4 GiB - 1 bytes the entries are still 4 bytes.
     */
    @Test
    public void testFourByteEntriesUpToFourGiB() throws Exception {
        long size = 0xffffffffL;
        StoredStream stored = new StoredStream(size, WofCompressionFormat.XPRESS4K, 4);
        WofCompressedStream stream = stored.open();

        byte[] actual = new byte[5000];
        stream.read(size - actual.length, actual, 0, actual.length);
        assertArrayEquals(stored.expected(size - actual.length, actual.length), actual);
    }

    @Test
    public void testReadAcrossChunks() throws Exception {
        StoredStream stored = new StoredStream(3 * 4096 + 123, WofCompressionFormat.XPRESS4K, 4);
        WofCompressedStream stream = stored.open();

        byte[] actual = new byte[3 * 4096 + 123];
        stream.read(0, actual, 0, actual.length);
        assertArrayEquals(stored.expected(0, actual.length), actual);
    }

    @Test
    public void testReadPastEndIsRejected() throws Exception {
        WofCompressedStream stream = new StoredStream(5000, WofCompressionFormat.XPRESS4K, 4).open();

        IOException e = assertThrows(IOException.class, () -> stream.read(4990, new byte[11], 0, 11));
        assertThat(e.getMessage(), containsString("outside"));
    }

    /**
     * A table entry that goes backwards gives a chunk a negative stored size.
     */
    @Test
    public void testDescendingTableEntryIsRejected() throws Exception {
        StoredStream stored = new StoredStream(3 * 4096, WofCompressionFormat.XPRESS4K, 4);
        stored.entryOverride = 1;
        stored.entryOverrideValue = 100;
        WofCompressedStream stream = stored.open();

        IOException e = assertThrows(IOException.class, () -> stream.read(2 * 4096, new byte[1], 0, 1));
        assertThat(e.getMessage(), containsString("invalid extent"));
    }

    /**
     * A table entry past the end of the stream.
     */
    @Test
    public void testTableEntryPastStreamIsRejected() throws Exception {
        StoredStream stored = new StoredStream(3 * 4096, WofCompressionFormat.XPRESS4K, 4);
        stored.entryOverride = 0;
        stored.entryOverrideValue = 0x7fffffff;
        WofCompressedStream stream = stored.open();

        assertThrows(IOException.class, () -> stream.read(0, new byte[1], 0, 1));
    }

    @Test
    public void testStreamTooSmallForTableIsRejected() {
        assertThrows(IOException.class, () -> new WofCompressedStream((o, d, off, l) -> { }, 3,
            3 * 4096, WofCompressionFormat.XPRESS4K));
    }

    @Test
    public void testEmptyFile() throws Exception {
        WofCompressedStream stream = new WofCompressedStream((o, d, off, l) -> {
            throw new AssertionError("nothing should be read");
        }, 0, 0, WofCompressionFormat.LZX);

        stream.read(0, new byte[0], 0, 0);
        assertThat(stream.getUncompressedSize(), is(0L));
    }

    /**
     * A stream whose chunks are all stored, so chunk {@code i} ends at {@code (i + 1) * chunkSize} past the table.
     */
    private static class StoredStream {
        final long size;
        final WofCompressionFormat format;
        final int entrySize;
        final long chunkCount;
        final long tableSize;
        long entryOverride = -1;
        long entryOverrideValue;

        StoredStream(long size, WofCompressionFormat format, int entrySize) {
            this.size = size;
            this.format = format;
            this.entrySize = entrySize;
            this.chunkCount = (size + format.getChunkSize() - 1) / format.getChunkSize();
            this.tableSize = (chunkCount - 1) * entrySize;
        }

        WofCompressedStream open() throws IOException {
            return new WofCompressedStream(this::read, tableSize + size, size, format);
        }

        byte[] expected(long offset, int length) {
            byte[] data = new byte[length];
            for (int i = 0; i < length; i++) {
                data[i] = dataByte(offset + i);
            }
            return data;
        }

        private static byte dataByte(long offset) {
            return (byte) (offset * 31 + (offset >>> 32) * 7);
        }

        private void read(long offset, byte[] dest, int destOffset, int length) {
            for (int i = 0; i < length; i++) {
                long position = offset + i;
                if (position < tableSize) {
                    long entry = position / entrySize;
                    long value = entry == entryOverride ? entryOverrideValue : (entry + 1) * format.getChunkSize();
                    dest[destOffset + i] = (byte) (value >>> (8 * (position % entrySize)));
                } else {
                    dest[destOffset + i] = dataByte(position - tableSize);
                }
            }
        }
    }
}
