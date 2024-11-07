/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

package org.jnode.fs.ntfs.datarun;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jnode.fs.ntfs.NTFSVolume;
import org.jnode.fs.util.FSUtils;
import org.jnode.util.LittleEndian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Daniel Noll (daniel@noll.id.au)
 */
public final class CompressedDataRun implements DataRunInterface {
    /**
     * Size of a compressed block in NTFS.  This is always the same even if the cluster size
     * is not 4k.
     */
    private static final int BLOCK_SIZE = 0x1000;

    /**
     * Logger.
     */
    private static final Logger log = LoggerFactory.getLogger(CompressedDataRun.class);

    /**
     * The underlying data runs containing the compressed data.
     */
    private final List<DataRun> compressedRuns = new ArrayList<DataRun>();

    /**
     * The number of clusters which make up a compression unit.
     */
    private final int compressionUnitSize;

    /**
     * Constructs a compressed run which when read, will decrypt data found
     * in the provided data run.
     *
     * @param compressedRun       the compressed data run.
     * @param compressionUnitSize the number of clusters which make up a compression unit.
     */
    public CompressedDataRun(DataRun compressedRun, int compressionUnitSize) {
        this.compressedRuns.add(compressedRun);
        this.compressionUnitSize = compressionUnitSize;
    }

    /**
     * Gets the length of the data run in clusters.
     *
     * @return the length of the run in clusters.
     */
    public long getLength() {
        return compressionUnitSize;
    }

    /**
     * Reads clusters from this datarun.
     *
     * @param vcn         the VCN to read, offset from the start of the entire file.
     * @param dst         destination buffer.
     * @param dstOffset   offset into destination buffer.
     * @param nrClusters  number of clusters to read.
     * @param clusterSize size of each cluster.
     * @param volume      reference to the NTFS volume structure.
     * @return the number of clusters read.
     * @throws IOException if an error occurs reading.
     */
    public int readClusters(long vcn, byte[] dst, int dstOffset, int nrClusters, int clusterSize, NTFSVolume volume)
            throws IOException {

        // Logic to determine whether we own the VCN which has been requested.
        // XXX: Lifted from DataRun.  Consider moving to some good common location.
        final long myFirstVcn = getFirstVcn();
        final long myLastVcn = getLastVcn();
        final long reqLastVcn = vcn + nrClusters - 1;

        if ((vcn > myLastVcn) || (myFirstVcn > reqLastVcn)) {
            // Not my region
            return 0;
        }

        if (log.isDebugEnabled()) {
            log.debug("me:{}-{}, req:{}-{}", myFirstVcn, myLastVcn, vcn, reqLastVcn);
        }

        // Now we know it's in our data run, here's the actual fragment to read.
        final long actFirstVcn = Math.max(myFirstVcn, vcn);
        final int actLength = (int) (Math.min(myLastVcn, reqLastVcn) - actFirstVcn + 1);
        final int vcnOffsetWithinUnit = (int) (actFirstVcn - myFirstVcn);
        final byte[] tempCompressed = new byte[compressionUnitSize * clusterSize];
        long readVcn = myFirstVcn;
        int tempCompressedOffset = 0;

        for (DataRun compressedRun : compressedRuns) {
            // This is the actual number of stored clusters after compression.
            // If the number of stored clusters is the same as the compression unit size,
            // then the data can be read directly without decompressing it.
            int compClusters = FSUtils.checkedCast(compressedRun.getLength());
            if (compClusters == compressionUnitSize) {
                return compressedRun.readClusters(vcn, dst, dstOffset, compClusters, clusterSize, volume);
            }

            // skip the sparse data runs.
            if (!compressedRun.isSparse()) {
                // Now we know the data is compressed.  Read in the compressed block...
                final int read = compressedRun.readClusters(readVcn, tempCompressed, tempCompressedOffset,
                        compClusters, clusterSize, volume);
                if (read != compClusters) {
                    throw new IOException("Needed " + compClusters + " clusters but could " + "only read " + read);
                }

                tempCompressedOffset += clusterSize * compClusters;
            }

            readVcn += compClusters;
        }

        final byte[] compressed = new byte[tempCompressedOffset];
        System.arraycopy(tempCompressed, 0, compressed, 0, tempCompressedOffset);

        // Decompress it, and copy into the destination.
        final byte[] tempUncompressed = new byte[compressionUnitSize * clusterSize];
        // XXX: We could potentially reduce the overhead by modifying the compression
        //      routine such that it's capable of skipping chunks that aren't needed.
        int actualUncompressedLength = decompressUnit(compressed, tempUncompressed);

        int copySource = vcnOffsetWithinUnit * clusterSize;
        int copyDest = dstOffset + (int) (actFirstVcn - vcn) * clusterSize;
        int copyLength = actLength * clusterSize;

        if (copyDest + copyLength > dst.length) {
            throw new ArrayIndexOutOfBoundsException(
                    String
                            .format("Copy dest %d length %d is too big for destination %d", copyDest, copyLength, dst.length));
        }

        if (copySource + copyLength > tempUncompressed.length) {
            throw new ArrayIndexOutOfBoundsException(
                    String.format("Copy source %d length %d is too big for source %d", copySource, copyLength,
                            tempUncompressed.length));
        }

        System.arraycopy(tempUncompressed, copySource, dst, copyDest, copyLength);

        return actLength;
    }

    /**
     * Decompresses a single unit of multiple compressed blocks.
     *
     * @param compressed   the compressed data (in.)
     * @param uncompressed the uncompressed data (out.)
     * @return the actual length of the uncompressed data.
     * @throws IOException if the decompression fails.
     */
    public int decompressUnit(final byte[] compressed,
                              final byte[] uncompressed) throws IOException {

        // This is just a convenient way to simulate the original code's pointer arithmetic.
        // I tried using buffers but positions in those are always from the beginning and
        // I had to also maintain a position from the start of the current block.
        final OffsetByteArray compressedData = new OffsetByteArray(compressed);
        final OffsetByteArray uncompressedData = new OffsetByteArray(uncompressed);

        // "compressedData.offset < compressed.length - 1" means we can read in at least two bytes (the 16-bit header)
        // TODO,
        //  figure out why "compressedData.offset < compressed.length" is not the end condition.
        //  It indicates that somewhere we didn't read the compressed array correctly before reaching here..
        while (compressedData.offset < compressed.length - 1) {
            // Bits [11:0] contain the size of the compressed chunk, minus three bytes.
            int compressedChunkSize = (compressedData.getShort(0) & (BLOCK_SIZE - 1)) + 3;
            final int uncompressedChunkSize = decompressBlock(compressedData, uncompressedData);

            if (uncompressedChunkSize == 0) {
                break;
            }

            compressedData.offset += compressedChunkSize;
            uncompressedData.offset += uncompressedChunkSize;
        }

        return uncompressedData.offset; //the actual length of the uncompressed data.
    }

    /**
     * Decompresses a single block.
     *
     * @param compressed   the compressed buffer (in.)
     * @param uncompressed the uncompressed buffer (out.)
     * @return the number of bytes consumed from the uncompressed buffer.
     * @see <a href="https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-xca/cba0fa15-bd62-4eda-8838-8fc7ab406df1">LZNT1 Algorithm Details</a>
     */
    private int decompressBlock(final OffsetByteArray compressed,
                                final OffsetByteArray uncompressed) {

        // nothing to read from the compressed array.
        if (compressed.offset >= compressed.array.length) {
            return 0;
        }

        // the current index in the uncompressed array. (based on the uncompressed.offset)
        int pos = 0;

        // the current index in the compressed array. (based on the compressed.offset)
        int cpos = 0;

        // A compressed buffer consists of a series of one or more compressed output chunks. Each chunk begins with a 16-bit header.
        // If both bytes of the header are 0, the header is an End_of_buffer terminal that denotes the end of the compressed data stream.
        // Otherwise, the header MUST be formatted as follows:
        //  Bit 15 indicates whether the chunk contains compressed data.
        //  Bits [14:12] contain a signature indicating the format of the subsequent data.
        //  Bits [11:0] contain the size of the compressed chunk, minus three bytes.
        final int rawLen = compressed.getShort(cpos);
        cpos += 2;

        // Bits 14 down to 12 contain a signature value. This value MUST always be 3 (unless the header denotes the end of the compressed buffer).
        final int signature = rawLen & 0x7000;
        if (rawLen != 0 && signature != 0x3000 && log.isDebugEnabled()) {
            log.error("ntfs_uncompblock: signature {} is not 3", signature);
        }

        // Bits 11 down to 0 contain the size of the compressed chunk minus three bytes. This size otherwise
        // includes the size of any metadata in the chunk, including the chunk header. If the chunk is
        // uncompressed, the total amount of uncompressed data therein can be computed by adding 1 to this
        // value (adding 3 bytes to get the total chunk size, then subtracting 2 bytes to account for the chunk
        // header).
        final int len = rawLen & 0xFFF;
        if (log.isDebugEnabled()) {
            log.debug("ntfs_uncompblock: block length: " + len + " + 3, 0x" +
                    Integer.toHexString(len) + ",0x" + Integer.toHexString(rawLen));
        }

        // If both bytes of the header are 0, the header is an End_of_buffer terminal that denotes the end of the compressed data stream.
        if (rawLen == 0) {
            // End of sequence, rest is zero.  For some reason there is nothing
            // of the sort documented in the Linux kernel's description of compression.
            return 0;
        }

        // Bit 15 indicates whether the chunk contains compressed data. If this bit is zero, the chunk header is followed by uncompressed literal data.
        // If this bit is set, the next byte of the chunk is the beginning of a Flag_group nonterminal that describes some compressed data.
        if ((rawLen & 0x8000) == 0) {
            // Uncompressed chunks store length as 0xFFF always.
            if ((len + 1) != BLOCK_SIZE) {
                log.debug("ntfs_uncompblock: len: " + len + " instead of 0xfff");
            }

            for (int k = 0; k < len + 1; k++) {
                uncompressed.put(pos++, compressed.get(cpos++));
            }

            // If the chunk is uncompressed, the total amount of uncompressed data therein can be computed by adding 1 to this
            // value (adding 3 bytes to get the total chunk size, then subtracting 2 bytes to account for the chunk
            // header).
            return len + 1;
        }

        int rightmostInUncompressed = Math.min(uncompressed.array.length, BLOCK_SIZE);

        // Now this chunk contains compressed data, decompress it.
        while (cpos < len + 3 && pos < rightmostInUncompressed) {
            // https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-xca/1fd21d29-f42f-4fc4-b677-9de7cc386be8
            // If a chunk is compressed, its chunk header is immediately followed by the first byte of a Flag_group nonterminal.
            //
            // A flag group consists of a flag byte followed by zero or more data elements.
            // Each data element is either a single literal byte or a two-byte compressed word.
            // The individual bits of a flag byte, taken from low-order bits to high-order bits, specify the formats of the subsequent data elements
            // (such that bit 0 corresponds to the first data element, bit 1 to the second, and so on).
            // If the bit corresponding to a data element is set, the element is a two-byte compressed word; otherwise, it is a one-byte literal.
            byte ctag = compressed.get(cpos++);

            final int bitsInByte = 8;
            for (int i = 0; i < bitsInByte; i++) {

                // To process compressed buffers,
                // the size of the compressed chunk that is stored in the chunk header MUST be used
                // to determine the position of the last valid byte in the chunk.
                // The size value MUST ignore flag bits that correspond to bytes outside the chunk.
                if (cpos >= len + 3 || pos >= rightmostInUncompressed) {
                    break;
                }

                if ((ctag & 1) != 0) {
                    int j, lmask, dshift;
                    for (j = pos - 1, lmask = 0xFFF, dshift = 12;
                         j >= 0x10; j >>>= 1) {
                        dshift--;
                        lmask >>>= 1;
                    }

                    // If the bit corresponding to a data element is set, the element is a two-byte compressed word.
                    final int tmp = compressed.getShort(cpos);
                    cpos += 2;

                    // While using the compressed buffers,
                    // the stored displacement must be incremented by 1 and
                    // the stored length must be incremented by 3,
                    // to get the actual displacement and length.
                    final int boff = (tmp >> dshift) + 1;
                    int blen = (tmp & lmask) + 3;

                    // Some of the bits in a flag byte might not be used.
                    // To process compressed buffers,
                    // the size of the compressed chunk that is stored in the chunk header MUST be used
                    // to determine the position of the last valid byte in the chunk.
                    // The size value MUST ignore flag bits that correspond to bytes outside the chunk.
                    blen = Math.min(blen, rightmostInUncompressed - pos);

                    // TODO,
                    //  no idea why the offset may be even larger than the (uncompressed.array.offset + pos),
                    //  -- it apparently makes it no place to start to read the data.
                    //  we didn't find any documentation explaining this case..
                    //  just return as an error for now.
                    if (boff > uncompressed.offset + pos) {
                        log.error("Failed to decompress data, the offset (to start to back to read) {} exceeds the sum of " +
                                "the current position {} and the uncompressed.offset {}", boff, pos, uncompressed.offset);
                        return pos;
                    }

                    // https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-xca/b1ba6d34-499c-4017-ab0c-fe2daee93efc
                    // Lempel-Ziv compression does not require that the entirety of the data to which
                    // a compressed word refers actually be in the uncompressed buffer when the word is processed.
                    // In other words, it is not required that (U – displacement + length < U).
                    // Therefore, when processing a compressed word, data MUST be copied from the start of the uncompressed target region to the end—that is,
                    // the byte at (U – displacement) MUST be copied first, then (U – displacement + 1), and so on,
                    // because the compressed word might refer to data that will be written during decompression.

                    // Also, the Linux code is https://github.com/torvalds/linux/blob/master/fs/ntfs3/lznt.c#L275-L277 as a reference.

                    // DO NOT use arrayCopy in any case.
                    for (int k = 0; k < blen; k++) {
                        uncompressed.put(pos, uncompressed.get(pos - boff));
                        pos++;
                    }
                } else {
                    // If the bit corresponding to a data element is NOT set, the element is a one-byte literal.
                    uncompressed.put(pos++, compressed.get(cpos++));
                }
                ctag >>>= 1;
            }
        }

        return pos;
    }

    @Override
    public long getFirstVcn() {
        return compressedRuns.get(0).getFirstVcn();
    }

    @Override
    public long getLastVcn() {
        return getFirstVcn() + getLength() - 1;
    }

    /**
     * Gets the number of clusters which make up a compression unit.
     *
     * @return the number of clusters.
     */
    public int getCompressionUnitSize() {
        return compressionUnitSize;
    }

    /**
     * Gets the underlying data runs containing the compressed data.
     *
     * @return the data runs.
     */
    public List<DataRun> getCompressedRuns() {
        return compressedRuns;
    }

    /**
     * Adds a data run to this compressed run.
     *
     * @param dataRun the data run to add.
     */
    public void addDataRun(DataRun dataRun) {
        compressedRuns.add(dataRun);
    }

    /**
     * Convenience class wrapping an array with its offset.  An alternative to pointer
     * arithmetic without going to the level of using an NIO buffer.
     */
    private static class OffsetByteArray {

        /**
         * The contained array.
         */
        private final byte[] array;

        /**
         * The current offset.
         */
        private int offset;

        /**
         * Constructs the offset byte array.  The offset begins at zero.
         *
         * @param array the contained array.
         */
        private OffsetByteArray(final byte[] array) {
            this.array = array;
        }

        /**
         * Gets a single byte from the array.
         *
         * @param offset the offset from the contained offset.
         * @return the byte.
         */
        private byte get(int offset) {
            return array[this.offset + offset];
        }

        /**
         * Puts a single byte into the array.
         *
         * @param offset the offset from the contained offset.
         * @param value  the byte.
         */
        private void put(int offset, byte value) {
            array[this.offset + offset] = value;
        }

        /**
         * Gets a 16-bit little-endian value from the array.
         *
         * @param offset the offset from the contained offset.
         * @return the short.
         */
        private int getShort(int offset) {
            return LittleEndian.getUInt16(array, this.offset + offset);
        }

        /**
         * Copies a slice from the provided array into our own array.  Uses {@code System.arraycopy}
         * where possible; if the slices overlap, copies one byte at a time to avoid a problem with
         * using {@code System.arraycopy} in this situation.
         *
         * @param src        the source offset byte array.
         * @param srcOffset  offset from the source array's offset.
         * @param destOffset offset from our own offset.
         * @param length     the number of bytes to copy.
         */
        private void copyFrom(OffsetByteArray src, int srcOffset, int destOffset, int length) {
            int realSrcOffset = src.offset + srcOffset;
            int realDestOffset = offset + destOffset;
            byte[] srcArray = src.array;
            byte[] destArray = array;

            // If the arrays are the same and the slices overlap we can't use the optimisation
            // because System.arraycopy effectively copies to a temp area. :-(
            if (srcArray == destArray &&
                    (realSrcOffset < realDestOffset && realSrcOffset + length > realDestOffset ||
                            realDestOffset < realSrcOffset && realDestOffset + length > realSrcOffset)) {

                // Don't change to System.arraycopy (see above)
                for (int i = 0; i < length; i++) {
                    destArray[realDestOffset + i] = srcArray[realSrcOffset + i];
                }

                return;
            }

            System.arraycopy(srcArray, realSrcOffset, destArray, realDestOffset, length);
        }

        /**
         * Zeroes out elements of the array.
         *
         * @param offset the offset from the contained offset.
         * @param length the number of sequential bytes to zero out.
         */
        private void zero(int offset, int length) {
            Arrays.fill(array,
                    Math.min(this.array.length - 1, this.offset + offset), // avoid out of boundary.
                    Math.min(this.array.length - 1, this.offset + offset + length), // avoid out of boundary.
                    (byte) 0);
        }
    }

    @Override
    public String toString() {
        return String.format("[compressed-run vcn:%d-%d %s]", getFirstVcn(), getLastVcn(), compressedRuns);
    }
}
