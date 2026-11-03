package org.jnode.fs.ntfs.wof;

import java.io.IOException;

/**
 * Reads a file the WOF file provider compressed ({@code compact /exe}), from its {@code WofCompressedData} stream.
 *
 * <p>The file is cut into chunks of {@link WofCompressionFormat#getChunkSize()} bytes, compressed independently.
 * The stream opens with a table of where each chunk after the first ends, relative to the end of the table, then
 * holds the chunks back to back. Entries are 4 bytes, or 8 once the file is larger than 4 GiB - 1. A chunk whose
 * stored size equals its uncompressed size is stored as is.</p>
 *
 * <p>Not safe for concurrent use without the synchronisation {@link #read} provides: the last chunk decompressed is
 * cached, as reads are mostly sequential and smaller than a chunk.</p>
 *
 * @author Luke Quinane
 */
public class WofCompressedStream {

    /**
     * Reads bytes from the compressed stream.
     */
    public interface Source {
        void read(long offset, byte[] dest, int destOffset, int length) throws IOException;
    }

    private static final int TABLE_CACHE_ENTRIES = 1024;

    private final Source source;
    private final long compressedSize;
    private final long uncompressedSize;
    private final WofCompressionFormat format;
    private final int chunkSize;
    private final long chunkCount;
    private final int entrySize;
    private final long tableSize;

    private WofDecompressor decompressor;
    private byte[] compressedChunk;
    private byte[] cachedChunk;
    private long cachedChunkIndex = -1;
    private byte[] tableCache;
    private long tableCacheFirstEntry = -1;
    private int tableCacheEntries;

    /**
     * Creates a reader over a compressed stream.
     *
     * @param source           the compressed stream.
     * @param compressedSize   the size of the compressed stream.
     * @param uncompressedSize the size of the file, from its unnamed data attribute.
     * @param format           the compression format from the reparse point.
     * @throws IOException if the stream is too small to hold the chunk table the sizes imply.
     */
    public WofCompressedStream(Source source, long compressedSize, long uncompressedSize, WofCompressionFormat format)
        throws IOException {
        this.source = source;
        this.compressedSize = compressedSize;
        this.uncompressedSize = uncompressedSize;
        this.format = format;
        this.chunkSize = format.getChunkSize();
        this.chunkCount = (uncompressedSize + chunkSize - 1) / chunkSize;
        this.entrySize = uncompressedSize > 0xffffffffL ? 8 : 4;
        this.tableSize = Math.max(chunkCount - 1, 0) * entrySize;

        if (uncompressedSize < 0 || compressedSize < tableSize) {
            throw new IOException("WOF stream of " + compressedSize + " bytes cannot hold the chunk table for " +
                uncompressedSize + " bytes of " + format);
        }
    }

    public long getUncompressedSize() {
        return uncompressedSize;
    }

    public WofCompressionFormat getFormat() {
        return format;
    }

    /**
     * Reads decompressed file data.
     *
     * @param offset     the offset into the file.
     * @param dest       the buffer to read into.
     * @param destOffset the offset into {@code dest}.
     * @param length     the number of bytes to read.
     * @throws IOException if the read runs past the end of the file or the stream is corrupt.
     */
    public synchronized void read(long offset, byte[] dest, int destOffset, int length) throws IOException {
        if (offset < 0 || length < 0 || offset > uncompressedSize - length) {
            throw new IOException("Read of " + length + " bytes at " + offset + " is outside the " +
                uncompressedSize + " byte file");
        }

        while (length > 0) {
            long chunkIndex = offset / chunkSize;
            int chunkOffset = (int) (offset % chunkSize);
            byte[] chunk = getChunk(chunkIndex);
            int count = Math.min(length, chunkLength(chunkIndex) - chunkOffset);

            System.arraycopy(chunk, chunkOffset, dest, destOffset, count);
            offset += count;
            destOffset += count;
            length -= count;
        }
    }

    private int chunkLength(long chunkIndex) {
        return (int) Math.min(chunkSize, uncompressedSize - chunkIndex * chunkSize);
    }

    private byte[] getChunk(long chunkIndex) throws IOException {
        if (chunkIndex == cachedChunkIndex) {
            return cachedChunk;
        }

        if (cachedChunk == null) {
            cachedChunk = new byte[chunkSize];
            compressedChunk = new byte[chunkSize];
            decompressor = format.createDecompressor();
        }

        long start = chunkIndex == 0 ? 0 : tableEntry(chunkIndex - 1);
        long end = chunkIndex == chunkCount - 1 ? compressedSize - tableSize : tableEntry(chunkIndex);
        int length = chunkLength(chunkIndex);
        long storedLength = end - start;

        if (start < 0 || end > compressedSize - tableSize || storedLength <= 0 || storedLength > length) {
            throw new IOException("WOF chunk " + chunkIndex + " has an invalid extent " + start + "-" + end +
                " for " + length + " bytes, stream size " + compressedSize);
        }

        // Invalidate first: a failure part way through must not leave a half-written chunk looking cached
        cachedChunkIndex = -1;

        if (storedLength == length) {
            source.read(tableSize + start, cachedChunk, 0, length);
        } else {
            source.read(tableSize + start, compressedChunk, 0, (int) storedLength);
            try {
                decompressor.decompress(compressedChunk, 0, (int) storedLength, cachedChunk, length);
            } catch (IOException e) {
                throw new IOException("Failed to decompress " + format + " chunk " + chunkIndex + ": " +
                    e.getMessage(), e);
            }
        }

        cachedChunkIndex = chunkIndex;
        return cachedChunk;
    }

    private long tableEntry(long index) throws IOException {
        if (index < tableCacheFirstEntry || index >= tableCacheFirstEntry + tableCacheEntries) {
            long entryCount = chunkCount - 1;
            tableCacheFirstEntry = index - index % TABLE_CACHE_ENTRIES;
            tableCacheEntries = (int) Math.min(TABLE_CACHE_ENTRIES, entryCount - tableCacheFirstEntry);
            if (tableCache == null) {
                tableCache = new byte[TABLE_CACHE_ENTRIES * entrySize];
            }

            try {
                source.read(tableCacheFirstEntry * entrySize, tableCache, 0, tableCacheEntries * entrySize);
            } catch (IOException e) {
                tableCacheFirstEntry = -1;
                tableCacheEntries = 0;
                throw e;
            }
        }

        int position = (int) (index - tableCacheFirstEntry) * entrySize;
        long value = 0;
        for (int i = entrySize - 1; i >= 0; i--) {
            value = value << 8 | (tableCache[position + i] & 0xff);
        }
        return value;
    }
}
