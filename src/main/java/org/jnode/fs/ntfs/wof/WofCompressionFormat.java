package org.jnode.fs.ntfs.wof;

/**
 * The compression formats the WOF file provider stores, keyed by the value in its reparse data.
 *
 * @author Luke Quinane
 */
public enum WofCompressionFormat {
    XPRESS4K(0, 4096),
    LZX(1, 32768),
    XPRESS8K(2, 8192),
    XPRESS16K(3, 16384);

    private final int id;
    private final int chunkSize;

    WofCompressionFormat(int id, int chunkSize) {
        this.id = id;
        this.chunkSize = chunkSize;
    }

    /**
     * Gets the identifier stored on disk for this format.
     *
     * @return the identifier.
     */
    public int getId() {
        return id;
    }

    /**
     * Gets the number of uncompressed bytes in each chunk, which is also the LZX window size.
     *
     * @return the chunk size.
     */
    public int getChunkSize() {
        return chunkSize;
    }

    /**
     * Looks up a format by the identifier stored on disk.
     *
     * @param id the identifier.
     * @return the format, or {@code null} if the identifier is not one WOF defines.
     */
    public static WofCompressionFormat fromId(int id) {
        for (WofCompressionFormat format : values()) {
            if (format.id == id) {
                return format;
            }
        }
        return null;
    }

    WofDecompressor createDecompressor() {
        return this == LZX ? new LzxDecompressor() : new XpressHuffmanDecompressor();
    }
}
