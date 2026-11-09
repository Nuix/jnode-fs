package org.jnode.fs.ntfs;

import java.io.IOException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.SortedMap;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.datarun.CompressedDataRun;
import org.jnode.fs.ntfs.datarun.CompressionUnitProblem;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.jnode.fs.FileSystemTestUtils.toByteArray;
import static org.jnode.fs.ntfs.datarun.CompressionUnitProblem.BACK_REFERENCE_BEFORE_CHUNK;
import static org.jnode.fs.ntfs.datarun.CompressionUnitProblem.BACK_REFERENCE_PAST_CHUNK;
import static org.jnode.fs.ntfs.datarun.CompressionUnitProblem.BAD_SIGNATURE;
import static org.jnode.fs.ntfs.datarun.CompressionUnitProblem.CHUNK_PAST_STORED_DATA;
import static org.jnode.fs.ntfs.datarun.CompressionUnitProblem.SHORT_CHUNK;

/**
 * Tests that the LZNT1 decoder notes where a unit fails strict LZNT1, while still decoding it as before.
 */
public class NTFSStrictCompressionTest {

    private static final int CHUNK = 0x1000;

    private static Set<CompressionUnitProblem> problems(byte[] compressed, int uncompressedLength)
        throws IOException {
        Set<CompressionUnitProblem> problems = new LinkedHashSet<>();
        new CompressedDataRun(null, 16).decompressUnit(compressed, new byte[uncompressedLength], problems);
        return problems;
    }

    /**
     * A full literal chunk: header 0x3fff, then 4,096 bytes.
     */
    private static byte[] literalChunk(int header, byte fill) {
        byte[] chunk = new byte[2 + CHUNK];
        chunk[0] = (byte) header;
        chunk[1] = (byte) (header >> 8);
        Arrays.fill(chunk, 2, chunk.length, fill);
        return chunk;
    }

    private static byte[] concat(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] joined = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, joined, offset, part.length);
            offset += part.length;
        }
        return joined;
    }

    @Test
    public void testBadSignature() throws Exception {
        assertThat(problems(literalChunk(0x7fff, (byte) 'A'), CHUNK), is(EnumSet.of(BAD_SIGNATURE)));
    }

    @Test
    public void testBackReferenceBeforeTheChunk() throws Exception {
        // A compressed chunk whose first element is a back-reference: there is nothing before it to copy
        assertThat(problems(toByteArray("02 B0 01 00 00"), CHUNK), hasItem(BACK_REFERENCE_BEFORE_CHUNK));
    }

    @Test
    public void testBackReferencePastTheChunk() throws Exception {
        // A literal 'A', then a back-reference of distance 1 and length 0xfff + 3, which runs past 4,096
        assertThat(problems(toByteArray("03 B0 02 41 FF 0F"), CHUNK), is(EnumSet.of(BACK_REFERENCE_PAST_CHUNK)));
    }

    @Test
    public void testShortChunkFollowedByAnother() throws Exception {
        // Two 4-byte literal chunks: only the last chunk of a unit may be short
        assertThat(problems(toByteArray("03 30 41 42 43 44 03 30 45 46 47 48"), 2 * CHUNK),
            is(EnumSet.of(SHORT_CHUNK)));
    }

    @Test
    public void testChunkClaimingMoreThanIsStored() throws Exception {
        // The shape of the last unit cut short at the valid data length; see getCorruptCompressionUnits
        assertThat(problems(toByteArray("0F B0 00 41 42 43 44"), CHUNK), is(EnumSet.of(CHUNK_PAST_STORED_DATA)));
    }

    /**
     * A unit over clusters that a registry transaction log has since reused, as on defcon-desktop MFT 28415: it
     * starts with the log block's {@code HvLE} magic, which reads as a literal chunk with signature 7.
     */
    @Test
    public void testUnitReusedByARegistryLog() throws Exception {
        byte[] unit = new byte[9 * CHUNK];
        System.arraycopy("HvLE".getBytes("US-ASCII"), 0, unit, 0, 4);
        System.arraycopy("hbin".getBytes("US-ASCII"), 0, unit, 0x40, 4);
        unit[0x64b] = (byte) 0xff;
        unit[0x64c] = (byte) 0xff;

        assertThat(problems(unit, 16 * CHUNK), hasItem(BAD_SIGNATURE));
    }

    /**
     * Six good chunks, then one that is not: the first six still decode, and the unit is flagged.
     */
    @Test
    public void testUnitValidForSixChunksThenCorrupt() throws Exception {
        byte[] good = literalChunk(0x3fff, (byte) 'G');
        byte[] compressed = concat(good, good, good, good, good, good, toByteArray("02 B0 01 00 00"));
        byte[] uncompressed = new byte[16 * CHUNK];
        Set<CompressionUnitProblem> problems = new LinkedHashSet<>();

        new CompressedDataRun(null, 16).decompressUnit(compressed, uncompressed, problems);

        assertThat(problems, is(EnumSet.of(BACK_REFERENCE_BEFORE_CHUNK)));
        for (int i = 0; i < 6 * CHUNK; i++) {
            assertThat(uncompressed[i], is((byte) 'G'));
        }
    }

    /**
     * The two damaged streams from triage 4924 still decode as before, but are now flagged.
     */
    @Test
    public void testTriage4924StreamsAreFlagged() throws Exception {
        byte[] data = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/triage-4924-E01.compressed.bin");
        byte[] missingPreviousChunk = Arrays.copyOfRange(data, 787, data.length);
        byte[] offsetBeforeStart =
            FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/offset-before-start.compressed.bin");

        assertThat(problems(missingPreviousChunk, 0x20000), hasItem(BACK_REFERENCE_BEFORE_CHUNK));
        assertThat(problems(offsetBeforeStart, 0x20000), hasItem(BACK_REFERENCE_BEFORE_CHUNK));
    }

    /**
     * {@code magnet-forensics/block1.compressed.bin} is a saved GitHub web page rather than LZNT1, which makes it a
     * fair stand-in for clusters that now hold some other file.
     */
    @Test
    public void testForeignBytesAreFlagged() throws Exception {
        byte[] html = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/magnet-forensics/block1.compressed.bin");
        assertThat(problems(Arrays.copyOf(html, 16 * CHUNK), 16 * CHUNK), hasItem(BAD_SIGNATURE));
    }

    @Test
    public void testValidStreamsPass() throws Exception {
        assertValid("org/jnode/fs/ntfs/py-lznt1/compressed.bin", "org/jnode/fs/ntfs/py-lznt1/decompressed.bin");
        assertValid("org/jnode/fs/ntfs/libfsntfs/fsntfs_test_compression_lznt1_compressed_data1.bin",
            "org/jnode/fs/ntfs/libfsntfs/fsntfs_test_compression_uncompressed_data1.bin");
    }

    private static void assertValid(String compressedPath, String expectedPath) throws Exception {
        byte[] compressed = FileSystemTestUtils.readFileToByteArray(compressedPath);
        byte[] expected = FileSystemTestUtils.readFileToByteArray(expectedPath);
        byte[] uncompressed = new byte[expected.length];
        Set<CompressionUnitProblem> problems = new LinkedHashSet<>();

        new CompressedDataRun(null, 16).decompressUnit(compressed, uncompressed, problems);

        assertThat(compressedPath, problems, is(empty()));
        assertThat(compressedPath, uncompressed, is(expected));
    }

    /**
     * Builds a compressed file of one 16-cluster unit whose only stored cluster holds a chunk claiming the full
     * 4,096 bytes: more than the 512 stored, as in a last unit cut short at the valid data length.
     */
    private static FileRecord fileWithAChunkCutShort(long validDataLength) throws Exception {
        return fileWithAChunkCutShort(validDataLength, true);
    }

    private static FileRecord fileWithAChunkCutShort(long validDataLength, boolean read) throws Exception {
        int clusterSize = 512;
        byte[] device = new byte[32 * clusterSize];
        NTFSVolume volume = NTFSTestVolume.volume(clusterSize, device);
        int stored = 2 * clusterSize;
        device[stored] = (byte) 0xff;
        device[stored + 1] = (byte) 0xbf;
        for (int i = 2; i < clusterSize; i++) {
            device[stored + i] = (byte) (i % 9 == 2 ? 0 : 'A');
        }

        long size = 16 * clusterSize;
        byte[] attribute = NTFSTestRecords.nonResidentAttribute(0, 15, 0x0001, 4, toByteArray("11 01 02 01 0F 00"),
            size, size, validDataLength);
        FileRecord record = NTFSTestRecords.record(volume, clusterSize, attribute);
        if (read) {
            record.readData(NTFSAttribute.Types.DATA, null, 0, new byte[(int) size], 0, (int) size, true);
        }
        return record;
    }

    /**
     * In the unit that holds the valid data length a chunk cut short is expected, so it isn't reported.
     */
    @Test
    public void testAChunkCutShortAtTheValidDataLengthIsNotReported() throws Exception {
        FileRecord record = fileWithAChunkCutShort(300);

        assertThat(record.getCorruptCompressionUnits(NTFSAttribute.Types.DATA, null).isEmpty(), is(true));
    }

    /**
     * Anywhere else it is.
     */
    @Test
    public void testAChunkCutShortElsewhereIsReported() throws Exception {
        FileRecord record = fileWithAChunkCutShort(16 * 512);

        SortedMap<Long, Set<CompressionUnitProblem>> corrupt =
            record.getCorruptCompressionUnits(NTFSAttribute.Types.DATA, null);
        assertThat(corrupt.keySet(), contains(0L));
        assertThat(corrupt.get(0L), is(EnumSet.of(CHUNK_PAST_STORED_DATA)));
    }

    /**
     * A unit that hasn't been read is read to check it, rather than reported as passing.
     */
    @Test
    public void testAUnitNotYetReadIsChecked() throws Exception {
        FileRecord record = fileWithAChunkCutShort(16 * 512, false);

        SortedMap<Long, Set<CompressionUnitProblem>> corrupt =
            record.getCorruptCompressionUnits(NTFSAttribute.Types.DATA, null);
        assertThat(corrupt.keySet(), contains(0L));
        assertThat(corrupt.get(0L), is(EnumSet.of(CHUNK_PAST_STORED_DATA)));
    }
}
