package org.jnode.fs.ntfs.wof;

import java.io.IOException;

/**
 * The bitstream shared by XPRESS Huffman and LZX: little-endian 16-bit words, each read most significant bit
 * first, with raw bytes interleaved at the position after the last word loaded.
 *
 * <p>Words are loaded only as {@link #ensure(int)} needs them, never ahead. Both formats depend on this: the raw
 * bytes (XPRESS match lengths, LZX uncompressed blocks) sit wherever the encoder had got to in the word stream,
 * so loading eagerly would read them from the wrong place.</p>
 *
 * <p>Running off the end supplies zero bits, as the decoder may peek a full codeword's width past the last real
 * bit. A decoder that keeps going past that has been given corrupt data.</p>
 *
 * @author Luke Quinane
 */
final class BitReader {

    /**
     * Zero words that may be supplied past the end before the input is treated as truncated.
     */
    private static final int MAX_OVERRUN_WORDS = 4;

    private final byte[] data;
    private final int end;
    private int position;
    private long buffer;
    private int bitCount;
    private int overrunWords;

    BitReader(byte[] data, int offset, int length) {
        this.data = data;
        this.position = offset;
        this.end = offset + length;
    }

    /**
     * Makes sure at least {@code count} bits, at most 32, are buffered.
     */
    void ensure(int count) throws IOException {
        while (bitCount < count) {
            int word = 0;
            if (end - position >= 2) {
                word = (data[position] & 0xff) | (data[position + 1] & 0xff) << 8;
                position += 2;
            } else if (++overrunWords > MAX_OVERRUN_WORDS) {
                throw new IOException("Compressed data is truncated");
            }
            buffer = (buffer << 16) | word;
            bitCount += 16;
        }
    }

    /**
     * Gets the next {@code count} bits without consuming them; {@link #ensure(int)} must have buffered them.
     */
    int peek(int count) {
        return (int) ((buffer >>> (bitCount - count)) & ((1L << count) - 1));
    }

    void remove(int count) {
        bitCount -= count;
    }

    int readBits(int count) throws IOException {
        ensure(count);
        int bits = peek(count);
        remove(count);
        return bits;
    }

    /**
     * Discards the buffered bits, so the next read starts at the byte after the last word loaded.
     */
    void align() {
        buffer = 0;
        bitCount = 0;
    }

    int readByte() throws IOException {
        if (position >= end) {
            throw new IOException("Compressed data is truncated");
        }
        return data[position++] & 0xff;
    }

    /**
     * Skips a byte if there is one. LZX pads odd-sized uncompressed blocks, but a final block may omit the pad.
     */
    void skipPadByte() {
        if (position < end) {
            position++;
        }
    }

    int readUInt16() throws IOException {
        return readByte() | readByte() << 8;
    }

    int readInt32() throws IOException {
        return readUInt16() | readUInt16() << 16;
    }

    void readBytes(byte[] dest, int offset, int length) throws IOException {
        if (end - position < length) {
            throw new IOException("Compressed data is truncated");
        }
        System.arraycopy(data, position, dest, offset, length);
        position += length;
    }
}
