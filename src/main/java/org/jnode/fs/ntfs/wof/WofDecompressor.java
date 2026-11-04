package org.jnode.fs.ntfs.wof;

import java.io.IOException;

/**
 * Decompresses one WOF chunk. Chunks are independent: no state carries over from one to the next.
 *
 * @author Luke Quinane
 */
interface WofDecompressor {

    /**
     * Decompresses a chunk.
     *
     * @param in       the buffer holding the compressed chunk.
     * @param inOffset the offset of the chunk in {@code in}.
     * @param inLength the compressed length.
     * @param out      the buffer to decompress into, from offset zero.
     * @param outLength the uncompressed length of the chunk.
     * @throws IOException if the compressed data is invalid.
     */
    void decompress(byte[] in, int inOffset, int inLength, byte[] out, int outLength) throws IOException;
}
