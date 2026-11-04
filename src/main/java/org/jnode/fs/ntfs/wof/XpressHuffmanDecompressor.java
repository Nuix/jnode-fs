package org.jnode.fs.ntfs.wof;

import java.io.IOException;

/**
 * Decompresses the LZ77+Huffman variant of XPRESS described in [MS-XCA] section 2.2.
 *
 * <p>A chunk is 256 bytes of 4-bit codeword lengths for 512 symbols, then the coded data. Symbols below 256 are
 * literals; the rest carry a match's length in the low nibble and the bit length of its offset in the high one.
 * The specification restarts the code every 64 KiB of output, which WOF chunks never reach.</p>
 *
 * @author Luke Quinane
 */
final class XpressHuffmanDecompressor implements WofDecompressor {

    private static final int SYMBOL_COUNT = 512;
    private static final int MAX_CODEWORD_LENGTH = 15;
    private static final int MIN_MATCH_LENGTH = 3;

    private final byte[] lengths = new byte[SYMBOL_COUNT];
    private final HuffmanDecoder decoder = new HuffmanDecoder(SYMBOL_COUNT, MAX_CODEWORD_LENGTH);

    @Override
    public void decompress(byte[] in, int inOffset, int inLength, byte[] out, int outLength) throws IOException {
        int tableLength = SYMBOL_COUNT / 2;
        if (inLength < tableLength) {
            throw new IOException("XPRESS chunk is shorter than its code table: " + inLength);
        }

        for (int i = 0; i < tableLength; i++) {
            int packed = in[inOffset + i] & 0xff;
            lengths[2 * i] = (byte) (packed & 0xf);
            lengths[2 * i + 1] = (byte) (packed >>> 4);
        }
        decoder.build(lengths, 0);

        BitReader bits = new BitReader(in, inOffset + tableLength, inLength - tableLength);
        int position = 0;

        while (position < outLength) {
            int symbol = decoder.decode(bits);
            if (symbol < 256) {
                out[position++] = (byte) symbol;
                continue;
            }

            int length = symbol & 0xf;
            int offsetBits = (symbol >>> 4) & 0xf;

            bits.ensure(16);
            int offset = (1 << offsetBits) | bits.peek(offsetBits);
            bits.remove(offsetBits);

            if (length == 0xf) {
                length += bits.readByte();
                if (length == 0xf + 0xff) {
                    length = bits.readUInt16();
                }
            }
            length += MIN_MATCH_LENGTH;

            if (offset > position || length > outLength - position) {
                throw new IOException("Invalid XPRESS match at " + position + ": offset " + offset +
                    ", length " + length);
            }
            for (int i = 0; i < length; i++, position++) {
                out[position] = out[position - offset];
            }
        }
    }
}
