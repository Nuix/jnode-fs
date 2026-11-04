package org.jnode.fs.ntfs.wof;

import java.io.IOException;
import java.util.Arrays;

/**
 * Decodes a canonical Huffman code given its codeword lengths: codes are assigned in order of length, then of
 * symbol value, as both XPRESS and LZX define them.
 *
 * <p>Codes up to {@link #MAX_ROOT_BITS} long are looked up in a table; longer ones are found by walking the lengths
 * above it. Codes are rebuilt for every LZX block, so a full table for 16-bit codes would cost more to fill than it
 * saves.</p>
 *
 * @author Luke Quinane
 */
final class HuffmanDecoder {

    private static final int MAX_ROOT_BITS = 10;
    private static final int LENGTH_BITS = 5;
    private static final int LENGTH_MASK = (1 << LENGTH_BITS) - 1;

    private final int symbolCount;
    private final int maxLength;
    private final int rootBits;

    /**
     * Entries are {@code symbol << LENGTH_BITS | length}, or -1 where the code is longer than the root bits.
     */
    private final int[] root;
    private final int[] counts;
    private final int[] firstCode;
    private final int[] firstIndex;
    private final int[] sortedSymbols;

    HuffmanDecoder(int symbolCount, int maxLength) {
        this.symbolCount = symbolCount;
        this.maxLength = maxLength;
        this.rootBits = Math.min(maxLength, MAX_ROOT_BITS);
        this.root = new int[1 << rootBits];
        this.counts = new int[maxLength + 1];
        this.firstCode = new int[maxLength + 1];
        this.firstIndex = new int[maxLength + 1];
        this.sortedSymbols = new int[symbolCount];
    }

    /**
     * Builds the code from {@code symbolCount} lengths starting at {@code offset}. A length of zero means the symbol
     * is unused. A code that is incomplete is accepted, and fails only if an unassigned codeword turns up.
     *
     * @throws IOException if a length is too long or the lengths oversubscribe the code space.
     */
    void build(byte[] lengths, int offset) throws IOException {
        Arrays.fill(counts, 0);
        for (int symbol = 0; symbol < symbolCount; symbol++) {
            int length = lengths[offset + symbol];
            if (length < 0 || length > maxLength) {
                throw new IOException("Invalid Huffman codeword length: " + length);
            }
            counts[length]++;
        }
        counts[0] = 0;

        int remaining = 1;
        for (int length = 1; length <= maxLength; length++) {
            remaining = (remaining << 1) - counts[length];
            if (remaining < 0) {
                throw new IOException("Huffman code is oversubscribed");
            }
        }

        int code = 0;
        int index = 0;
        for (int length = 1; length <= maxLength; length++) {
            firstCode[length] = code;
            firstIndex[length] = index;
            code = (code + counts[length]) << 1;
            index += counts[length];
        }

        int[] next = Arrays.copyOf(firstIndex, maxLength + 1);
        for (int symbol = 0; symbol < symbolCount; symbol++) {
            int length = lengths[offset + symbol];
            if (length != 0) {
                sortedSymbols[next[length]++] = symbol;
            }
        }

        Arrays.fill(root, -1);
        for (int length = 1; length <= rootBits; length++) {
            int span = 1 << (rootBits - length);
            for (int i = 0; i < counts[length]; i++) {
                int entry = sortedSymbols[firstIndex[length] + i] << LENGTH_BITS | length;
                int start = (firstCode[length] + i) * span;
                Arrays.fill(root, start, start + span, entry);
            }
        }
    }

    int decode(BitReader in) throws IOException {
        in.ensure(maxLength);
        int bits = in.peek(maxLength);

        int entry = root[bits >>> (maxLength - rootBits)];
        if (entry >= 0) {
            in.remove(entry & LENGTH_MASK);
            return entry >>> LENGTH_BITS;
        }

        for (int length = rootBits + 1; length <= maxLength; length++) {
            int index = (bits >>> (maxLength - length)) - firstCode[length];
            if (index >= 0 && index < counts[length]) {
                in.remove(length);
                return sortedSymbols[firstIndex[length] + index];
            }
        }

        throw new IOException("Invalid Huffman codeword");
    }
}
