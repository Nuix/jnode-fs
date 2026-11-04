package org.jnode.fs.ntfs.wof;

import java.io.IOException;
import java.util.Arrays;

/**
 * Decompresses LZX as WOF and WIM use it: a 32 KiB window that does not slide between chunks, and the E8 call
 * translation always applied with a fixed file size and no header bit to say so.
 *
 * <p>The format is the one in [MS-PATCH] "LZX DELTA Compression and Decompression", without the delta extensions.
 * Blocks within a chunk share the main and length codeword lengths, which are delta-coded against the previous
 * block's and start from zero for each chunk.</p>
 *
 * @author Luke Quinane
 */
final class LzxDecompressor implements WofDecompressor {

    private static final int WINDOW_SIZE = 32768;
    private static final int CHAR_COUNT = 256;
    private static final int OFFSET_SLOT_COUNT = 30;
    private static final int PRIMARY_LENGTHS = 7;
    private static final int LENGTH_HEADERS = PRIMARY_LENGTHS + 1;
    private static final int MAIN_SYMBOL_COUNT = CHAR_COUNT + OFFSET_SLOT_COUNT * LENGTH_HEADERS;
    private static final int LENGTH_SYMBOL_COUNT = 249;
    private static final int PRECODE_SYMBOL_COUNT = 20;
    private static final int ALIGNED_SYMBOL_COUNT = 8;
    private static final int MIN_MATCH_LENGTH = 2;
    private static final int RECENT_OFFSET_COUNT = 3;
    private static final int MIN_ALIGNED_OFFSET_SLOT = 8;
    private static final int ALIGNED_OFFSET_BITS = 3;

    private static final int BLOCK_TYPE_VERBATIM = 1;
    private static final int BLOCK_TYPE_ALIGNED = 2;
    private static final int BLOCK_TYPE_UNCOMPRESSED = 3;

    /**
     * Room past the end of each length array for a run that overshoots it; the longest run is 51.
     */
    private static final int LENGTH_OVERRUN = 50;

    private static final int E8_FILE_SIZE = 12000000;

    private static final int[] EXTRA_OFFSET_BITS = new int[OFFSET_SLOT_COUNT];

    /**
     * The first offset in each slot, less the two that every explicit offset is stored with.
     */
    private static final int[] OFFSET_SLOT_BASE = new int[OFFSET_SLOT_COUNT];

    static {
        int base = -2;
        for (int slot = 0; slot < OFFSET_SLOT_COUNT; slot++) {
            EXTRA_OFFSET_BITS[slot] = slot < 4 ? 0 : Math.min((slot - 2) / 2, 17);
            OFFSET_SLOT_BASE[slot] = base;
            base += 1 << EXTRA_OFFSET_BITS[slot];
        }
    }

    private final byte[] mainLengths = new byte[MAIN_SYMBOL_COUNT + LENGTH_OVERRUN];
    private final byte[] lengthLengths = new byte[LENGTH_SYMBOL_COUNT + LENGTH_OVERRUN];
    private final byte[] alignedLengths = new byte[ALIGNED_SYMBOL_COUNT];
    private final byte[] precodeLengths = new byte[PRECODE_SYMBOL_COUNT];

    private final HuffmanDecoder mainCode = new HuffmanDecoder(MAIN_SYMBOL_COUNT, 16);
    private final HuffmanDecoder lengthCode = new HuffmanDecoder(LENGTH_SYMBOL_COUNT, 16);
    private final HuffmanDecoder alignedCode = new HuffmanDecoder(ALIGNED_SYMBOL_COUNT, 7);
    private final HuffmanDecoder precode = new HuffmanDecoder(PRECODE_SYMBOL_COUNT, 15);

    private final int[] recentOffsets = new int[RECENT_OFFSET_COUNT];

    @Override
    public void decompress(byte[] in, int inOffset, int inLength, byte[] out, int outLength) throws IOException {
        if (outLength > WINDOW_SIZE) {
            throw new IOException("LZX chunk is larger than the window: " + outLength);
        }

        BitReader bits = new BitReader(in, inOffset, inLength);
        Arrays.fill(mainLengths, (byte) 0);
        Arrays.fill(lengthLengths, (byte) 0);
        Arrays.fill(recentOffsets, 1);

        int position = 0;
        while (position < outLength) {
            bits.ensure(4);
            int blockType = bits.peek(3);
            bits.remove(3);
            int blockSize;
            if (bits.peek(1) != 0) {
                bits.remove(1);
                blockSize = WINDOW_SIZE;
            } else {
                bits.remove(1);
                blockSize = bits.readBits(16);
            }

            switch (blockType) {
                case BLOCK_TYPE_ALIGNED:
                    for (int i = 0; i < ALIGNED_SYMBOL_COUNT; i++) {
                        alignedLengths[i] = (byte) bits.readBits(3);
                    }
                    // Fall through: the rest of the header is the same as a verbatim block's
                case BLOCK_TYPE_VERBATIM:
                    readCodewordLengths(bits, mainLengths, 0, CHAR_COUNT);
                    readCodewordLengths(bits, mainLengths, CHAR_COUNT, MAIN_SYMBOL_COUNT - CHAR_COUNT);
                    readCodewordLengths(bits, lengthLengths, 0, LENGTH_SYMBOL_COUNT);
                    break;
                case BLOCK_TYPE_UNCOMPRESSED:
                    // The offsets start at the next word, and if the stream is already on one a whole word is
                    // skipped: ensuring a bit before aligning does both.
                    bits.ensure(1);
                    bits.align();
                    for (int i = 0; i < RECENT_OFFSET_COUNT; i++) {
                        recentOffsets[i] = bits.readInt32();
                        if (recentOffsets[i] == 0) {
                            throw new IOException("Invalid LZX recent offset: " + recentOffsets[i]);
                        }
                    }
                    break;
                default:
                    throw new IOException("Invalid LZX block type: " + blockType);
            }

            if (blockSize < 1 || blockSize > outLength - position) {
                throw new IOException("Invalid LZX block size " + blockSize + " at " + position);
            }

            if (blockType == BLOCK_TYPE_UNCOMPRESSED) {
                bits.readBytes(out, position, blockSize);
                if ((blockSize & 1) != 0) {
                    bits.skipPadByte();
                }
            } else {
                decompressBlock(bits, blockType == BLOCK_TYPE_ALIGNED, out, position, position + blockSize);
            }
            position += blockSize;
        }

        undoE8Translation(out, outLength);
    }

    private void readCodewordLengths(BitReader bits, byte[] lengths, int offset, int count) throws IOException {
        for (int i = 0; i < PRECODE_SYMBOL_COUNT; i++) {
            precodeLengths[i] = (byte) bits.readBits(4);
        }
        precode.build(precodeLengths, 0);

        int position = offset;
        int end = offset + count;
        while (position < end) {
            int symbol = precode.decode(bits);
            if (symbol < 17) {
                lengths[position] = deltaLength(lengths[position], symbol);
                position++;
                continue;
            }

            int run;
            byte length;
            if (symbol == 17) {
                run = 4 + bits.readBits(4);
                length = 0;
            } else if (symbol == 18) {
                run = 20 + bits.readBits(5);
                length = 0;
            } else {
                run = 4 + bits.readBits(1);
                int delta = precode.decode(bits);
                if (delta > 16) {
                    throw new IOException("Invalid LZX precode symbol in a run: " + delta);
                }
                length = deltaLength(lengths[position], delta);
            }

            Arrays.fill(lengths, position, position + run, length);
            position += run;
        }
    }

    private static byte deltaLength(byte previous, int delta) {
        return (byte) ((previous - delta + 17) % 17);
    }

    private void decompressBlock(BitReader bits, boolean aligned, byte[] out, int position, int blockEnd)
        throws IOException {

        mainCode.build(mainLengths, 0);
        lengthCode.build(lengthLengths, 0);
        if (aligned) {
            alignedCode.build(alignedLengths, 0);
        }

        while (position < blockEnd) {
            int symbol = mainCode.decode(bits);
            if (symbol < CHAR_COUNT) {
                out[position++] = (byte) symbol;
                continue;
            }

            symbol -= CHAR_COUNT;
            int length = symbol % LENGTH_HEADERS;
            int slot = symbol / LENGTH_HEADERS;
            if (length == PRIMARY_LENGTHS) {
                length += lengthCode.decode(bits);
            }
            length += MIN_MATCH_LENGTH;

            int offset;
            if (slot < RECENT_OFFSET_COUNT) {
                // Not a true LRU: using R2 swaps it with R0 and leaves R1 alone
                offset = recentOffsets[slot];
                recentOffsets[slot] = recentOffsets[0];
            } else {
                int extraBits = EXTRA_OFFSET_BITS[slot];
                if (aligned && slot >= MIN_ALIGNED_OFFSET_SLOT) {
                    offset = bits.readBits(extraBits - ALIGNED_OFFSET_BITS) << ALIGNED_OFFSET_BITS;
                    offset |= alignedCode.decode(bits);
                } else {
                    offset = bits.readBits(extraBits);
                }
                offset += OFFSET_SLOT_BASE[slot];
                recentOffsets[2] = recentOffsets[1];
                recentOffsets[1] = recentOffsets[0];
            }
            recentOffsets[0] = offset;

            if (offset < 1 || offset > position || length > blockEnd - position) {
                throw new IOException("Invalid LZX match at " + position + ": offset " + offset +
                    ", length " + length);
            }
            for (int i = 0; i < length; i++, position++) {
                out[position] = out[position - offset];
            }
        }
    }

    /**
     * Turns the absolute targets the compressor wrote into x86 CALL instructions (E8 bytes) back into relative
     * ones. A call can only start where all four target bytes lie before the last six bytes of the chunk.
     */
    static void undoE8Translation(byte[] data, int length) {
        for (int i = 0; i < length - 10; i++) {
            if ((data[i] & 0xff) != 0xe8) {
                continue;
            }

            int target = (data[i + 1] & 0xff) | (data[i + 2] & 0xff) << 8 | (data[i + 3] & 0xff) << 16 |
                (data[i + 4] & 0xff) << 24;
            int relative;
            if (target >= 0 && target < E8_FILE_SIZE) {
                relative = target - i;
            } else if (target < 0 && target >= -i) {
                relative = target + E8_FILE_SIZE;
            } else {
                i += 4;
                continue;
            }

            data[i + 1] = (byte) relative;
            data[i + 2] = (byte) (relative >>> 8);
            data[i + 3] = (byte) (relative >>> 16);
            data[i + 4] = (byte) (relative >>> 24);
            i += 4;
        }
    }
}
