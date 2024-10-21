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
import lombok.Getter;
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
     * @see <a href="https://github.com/torvalds/linux/blob/master/fs/ntfs3/lznt.c#L114">The max of the offset</a>
     */
    private static final int[] sMaxOff = {
            0x10, 0x20, 0x40, 0x80, 0x100, 0x200, 0x400, 0x800, 0x1000,
    };

    /**
     * Invalid argument.
     * It used in <a href="https://github.com/torvalds/linux/blob/master/fs/ntfs3/lznt.c">LZNT C code</a>, but we didn't find the definition.
     * It's similar to <a href="https://github.com/torvalds/linux/blob/c964ced7726294d40913f2127c3f185a92cb4a41/arch/powerpc/boot/stdio.h#L8>Invalid argument</a>
     */
    private static final int EINVAL = 22;

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
        final int vcnOffsetWithinUnit = (int) (actFirstVcn % compressionUnitSize);
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

            // Now we know the data is compressed.  Read in the compressed block...
            final int read = compressedRun.readClusters(readVcn, tempCompressed, tempCompressedOffset,
                    compClusters, clusterSize, volume);
            if (read != compClusters) {
                throw new IOException("Needed " + compClusters + " clusters but could " + "only read " + read);
            }

            tempCompressedOffset += clusterSize * compClusters;
            readVcn += compClusters;
        }

        // Uncompress it, and copy into the destination.
        final byte[] tempUncompressed = new byte[compressionUnitSize * clusterSize];
        // XXX: We could potentially reduce the overhead by modifying the compression
        //      routine such that it's capable of skipping chunks that aren't needed.
        unCompressUnit(tempCompressed, tempUncompressed);

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
     * Uncompresses a single unit of multiple compressed blocks.
     *
     * @param compressed   the compressed data (in.)
     * @param uncompressed the uncompressed data (out.)
     * @throws IOException if the decompression fails.
     */
    public static void unCompressUnit(final byte[] compressed,
                                      final byte[] uncompressed) throws IOException {

        // This is just a convenient way to simulate the original code's pointer arithmetic.
        // I tried using buffers but positions in those are always from the beginning and
        // I had to also maintain a position from the start of the current block.
        final OffsetByteArray compressedData = new OffsetByteArray(compressed);
        final OffsetByteArray uncompressedData = new OffsetByteArray(uncompressed);

        /* Loop through decompressing chunks. */
        while (uncompressedData.offset < uncompressed.length &&
                compressedData.offset < compressed.length) {
            // the length to write with zero in error case.
            // calculate it before uncompressBlock() since uncompressBlock() may change them.
            int lengthToWriteZeroInErrorCase = uncompressed.length - uncompressedData.offset;
            final int consumed = uncompressBlock(compressedData, uncompressedData);

            // Apple's code had this as an error but to me it looks like this simply
            // terminates the sequence of compressed blocks.
            if (consumed == 0) {
                // At the current point in time this is already zero but if the code
                // changes in the future to reuse the temp buffer, this is a good idea.
                uncompressedData.zero(0, lengthToWriteZeroInErrorCase);
                break;
            }
        }
    }

    /**
     * Uncompresses a single block.
     *
     * @param compressed   the compressed buffer (in.)
     * @param uncompressed the uncompressed buffer (out.)
     * @return the number of bytes consumed from the compressed buffer.
     */
    private static int uncompressBlock(final OffsetByteArray compressed,
                                       final OffsetByteArray uncompressed) {
        /* Read chunk header. */
        final int rawLen = compressed.getShort(0);
        final int len = rawLen & (BLOCK_SIZE - 1);
        if (log.isDebugEnabled()) {
            log.debug("ntfs_uncompblock: block length: " + len + " + 3, 0x" +
                    Integer.toHexString(len) + ",0x" + Integer.toHexString(rawLen));
        }

        if (rawLen == 0) {
            // End of sequence, rest is zero.  For some reason there is nothing
            // of the sort documented in the Linux kernel's description of compression.
            return 0;
        }

        // I didn't get why 3 is needed here, same to https://github.com/torvalds/linux/blob/master/fs/ntfs3/lznt.c#L380
        int cmprUse = 3 + len;

        /* First make sure the chunk contains compressed data. */
        if ((rawLen & 0x8000) != 0) {
            int uncompressedWrittenLength = decompressChunk(uncompressed, BLOCK_SIZE, compressed, cmprUse);
            if (uncompressedWrittenLength < 0) {
                // there is an error in the current chunk, skip it and go to the next one.
                compressed.offset += cmprUse;
                uncompressed.offset += BLOCK_SIZE;
            }
        } else {
            /* This chunk does not contain compressed data. */
            // Uncompressed chunks store length as 0xFFF always.
            if ((len + 1) != BLOCK_SIZE) {
                log.debug("ntfs_uncompblock: len: " + len + " instead of 0xfff");
            }

            // Copies the entire compression block as-is, need to skip the compression flag,
            // no idea why they even stored it given that it isn't used.
            // Darwin's version I was referring to doesn't skip this, which seems be a bug.
            try {
                uncompressed.copyFrom(compressed, 2, 0, len + 1);
            } catch (Exception e) {
                return cmprUse;
            }

            uncompressed.zero(len + 1, BLOCK_SIZE - 1 - len);
            compressed.offset += len + 1;
            uncompressed.offset += BLOCK_SIZE;
        }

        return cmprUse;
    }

    private static int decompressChunk(OffsetByteArray uncompressed, int uncEnd, OffsetByteArray compressed, int cmprEnd) {
        // the index to calculate how many bits are used for offset and how many bits are used for length
        int index = 0;

        // the current position in the uncompressed. (the offset based on the existing compressed.offset). It is the "up" in c code.
        int uPos = 0;

        // the current position in the compressed. (the offset based on the existing uncompressed.offset). It is the "cmpr" in c code.
        // initialised as 2 as the rawLen (the chunk header) has been read before this method is invoked.
        int cPos = 2;

        int blockIndex = 0; // temp value for testing only.

        while (uPos < uncEnd && cPos < cmprEnd) {
            /* Advance flag bit value. */
            byte ctag = compressed.get(cPos++);

            for (int i = 0; i < 8 && cPos < cmprEnd; i++) {
                // return err if more than LZNT_CHUNK_SIZE bytes are written
                if (uPos > BLOCK_SIZE)
                    return -EINVAL;

                /* Correct index */
                while (sMaxOff[index] < uPos) {
                    index += 1;
                }

                /* Check the current flag for zero. */
                if ((ctag & 1) == 0) {
                    /* Just copy byte. */
                    uncompressed.put(uPos++, compressed.get(cPos++));
                } else {
                    /* Check for boundary. */
                    if (cPos + 1 >= cmprEnd) {
                        log.error("Failed to decompress data, the compressed data position {} exceeds the boundary {}", cPos, cmprEnd);
                        return -EINVAL;
                    }

                    /* Read a short from little endian stream. */
                    final int pair = compressed.getShort(cPos);
                    cPos += 2;

                    /* Translate packed information into offset and length. */
                    final int boff = 1 + (pair >> (12 - index));
                    int blen = 3 + (pair & ((1 << (12 - index)) - 1));

                    /* Check offset for boundary. */
                    if (boff > uPos) {
                        log.error("Failed to decompress data, the uncompressed data offset {} exceeds the current position {}", boff, uPos);
                        return -EINVAL;
                    }

                    /* Truncate the length if necessary. */
                    blen = Math.min(blen, BLOCK_SIZE - uPos);

                    /* Now we copy bytes. This is the heart of LZ algorithm. */
                    for (; blen > 0; blen--, uPos++) {
                        uncompressed.put(uPos, uncompressed.get(uPos - boff));
                    }
                    uPos += blen; // don't forget to update the index in the uncompressed array.
                }

                ctag >>>= 1; // >> or >>> are both OK here as we only check it 8 times (all bits in the byte will be and only be checked once)
            }

            blockIndex++;
        }

        compressed.offset += cmprEnd;
        uncompressed.offset += uncEnd;
        return uPos;
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
    @Getter
    static class OffsetByteArray {

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


            for (int i = 0; i < length; i++) {
                destArray[realDestOffset + i] = srcArray[realSrcOffset + i];
            }

//            // If the arrays are the same and the slices overlap we can't use the optimisation
//            // because System.arraycopy effectively copies to a temp area. :-(
//            if (srcArray == destArray &&
//                    (realSrcOffset < realDestOffset && realSrcOffset + length > realDestOffset ||
//                            realDestOffset < realSrcOffset && realDestOffset + length > realSrcOffset)) {
//
//                // Don't change to System.arraycopy (see above)
//                for (int i = 0; i < length; i++) {
//                    destArray[realDestOffset + i] = srcArray[realSrcOffset + i];
//                }
//
//                return;
//            }
//
//            try {
//                System.arraycopy(srcArray, realSrcOffset, destArray, realDestOffset, length);
//            }
//            catch (Exception e) {
//                //log.error("Failed to decompress", e);
//                return;
//            }
        }

        /**
         * Zeroes out elements of the array.
         *
         * @param offset the offset from the contained offset.
         * @param length the number of sequential bytes to zero out.
         */
        private void zero(int offset, int length) {
            Arrays.fill(array, this.offset + offset, this.offset + offset + length, (byte) 0);
        }
    }

    @Override
    public String toString() {
        return String.format("[compressed-run vcn:%d-%d %s]", getFirstVcn(), getLastVcn(), compressedRuns);
    }
}
