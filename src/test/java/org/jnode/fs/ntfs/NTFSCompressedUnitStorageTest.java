package org.jnode.fs.ntfs;

import java.io.IOException;
import java.util.Arrays;

import org.jnode.fs.ntfs.datarun.CompressedDataRun;
import org.jnode.fs.ntfs.datarun.DataRun;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

/**
 * Tests how a compression unit that is stored uncompressed is read back.
 *
 * <p>A unit that does not compress is written to disk as-is, filling the whole unit, and must be handed back
 * verbatim. Running it through the LZNT1 decompressor treats the first two bytes of the data as a chunk header and
 * returns garbage.</p>
 */
public class NTFSCompressedUnitStorageTest {

    private static final int CLUSTER_SIZE = 512;
    private static final int COMPRESSION_UNIT = 16;

    /** Somewhere past the volume header for the unit under test to live. */
    private static final int FIRST_DATA_CLUSTER = 8;

    /**
     * A device with recognisable content in every cluster from {@link #FIRST_DATA_CLUSTER} on.
     */
    private static byte[] device() {
        byte[] data = new byte[64 * CLUSTER_SIZE];
        NTFSTestVolume.fillClusters(data, CLUSTER_SIZE, FIRST_DATA_CLUSTER, 64 - FIRST_DATA_CLUSTER);
        return data;
    }

    /**
     * A compression unit stored in a single run that fills the unit is raw data, and has always been read back
     * directly.
     */
    @Test
    public void testUnitStoredUncompressedInOneRun() throws Exception {
        // Arrange
        byte[] data = device();
        NTFSVolume volume = NTFSTestVolume.volume(CLUSTER_SIZE, data);
        CompressedDataRun run = new CompressedDataRun(
            new DataRun(FIRST_DATA_CLUSTER, COMPRESSION_UNIT, false, 0, 0), COMPRESSION_UNIT);

        // Act
        byte[] actual = new byte[COMPRESSION_UNIT * CLUSTER_SIZE];
        int read = run.readClusters(0, actual, 0, COMPRESSION_UNIT, CLUSTER_SIZE, volume);

        // Assert
        assertThat(read, is(COMPRESSION_UNIT));
        assertThat(actual, is(onDisk(data, FIRST_DATA_CLUSTER, COMPRESSION_UNIT)));
    }

    /**
     * The same unit fragmented across two runs is still stored raw. Each run on its own is shorter than the unit,
     * so a per-run check misses it; the fragments have to be added up.
     */
    @Test
    public void testUnitStoredUncompressedAcrossTwoRuns() throws Exception {
        // Arrange: 10 clusters at one place on the volume and the remaining 6 somewhere else
        byte[] data = device();
        NTFSVolume volume = NTFSTestVolume.volume(CLUSTER_SIZE, data);
        CompressedDataRun run = new CompressedDataRun(
            new DataRun(FIRST_DATA_CLUSTER, 10, false, 0, 0), COMPRESSION_UNIT);
        run.addDataRun(new DataRun(40, 6, false, 0, 10));

        // Act
        byte[] actual = new byte[COMPRESSION_UNIT * CLUSTER_SIZE];
        int read = run.readClusters(0, actual, 0, COMPRESSION_UNIT, CLUSTER_SIZE, volume);

        // Assert: the two fragments, back to back, exactly as they are on disk
        byte[] expected = new byte[COMPRESSION_UNIT * CLUSTER_SIZE];
        System.arraycopy(onDisk(data, FIRST_DATA_CLUSTER, 10), 0, expected, 0, 10 * CLUSTER_SIZE);
        System.arraycopy(onDisk(data, 40, 6), 0, expected, 10 * CLUSTER_SIZE, 6 * CLUSTER_SIZE);

        assertThat(read, is(COMPRESSION_UNIT));
        assertThat(actual, is(expected));
    }

    /**
     * A unit that really is compressed stays shorter than the unit, so it still goes through the decompressor.
     */
    @Test
    public void testShortUnitIsDecompressed() throws Exception {
        // Arrange: an LZNT1 chunk holding 4 literal bytes, in a 7 cluster run padded out to the 16 cluster unit
        byte[] data = device();
        writeLiteralChunk(data, FIRST_DATA_CLUSTER * CLUSTER_SIZE, new byte[] {'a', 'b', 'c', 'd'});
        NTFSVolume volume = NTFSTestVolume.volume(CLUSTER_SIZE, data);
        CompressedDataRun run = new CompressedDataRun(
            new DataRun(FIRST_DATA_CLUSTER, 7, false, 0, 0), COMPRESSION_UNIT);

        // Act
        byte[] actual = new byte[COMPRESSION_UNIT * CLUSTER_SIZE];
        run.readClusters(0, actual, 0, COMPRESSION_UNIT, CLUSTER_SIZE, volume);

        // Assert
        assertThat(Arrays.copyOf(actual, 4), is(new byte[] {'a', 'b', 'c', 'd'}));
    }

    /**
     * Fragments that add up to more than the unit cannot be laid out in it, and previously overran the buffer they
     * were read into.
     */
    @Test
    public void testFragmentsLongerThanTheUnitAreRejected() throws Exception {
        // Arrange
        byte[] data = device();
        NTFSVolume volume = NTFSTestVolume.volume(CLUSTER_SIZE, data);
        CompressedDataRun run = new CompressedDataRun(
            new DataRun(FIRST_DATA_CLUSTER, 10, false, 0, 0), COMPRESSION_UNIT);
        run.addDataRun(new DataRun(40, 9, false, 0, 10));

        // Act
        try {
            run.readClusters(0, new byte[COMPRESSION_UNIT * CLUSTER_SIZE], 0, COMPRESSION_UNIT, CLUSTER_SIZE, volume);
            fail("runs totalling more than the compression unit should be rejected");
        } catch (IOException e) {
            // Assert
            assertThat(e.getMessage(), containsString("more than the 16 cluster compression unit"));
        }
    }

    /**
     * Gets a copy of what is on the device for a range of clusters.
     */
    private static byte[] onDisk(byte[] data, int cluster, int clusters) {
        return Arrays.copyOfRange(data, cluster * CLUSTER_SIZE, (cluster + clusters) * CLUSTER_SIZE);
    }

    /**
     * Writes an LZNT1 chunk of uncompressed literal data, followed by the end of buffer terminator.
     */
    private static void writeLiteralChunk(byte[] data, int offset, byte[] literal) {
        int header = 0x3000 | (literal.length - 1);
        data[offset] = (byte) header;
        data[offset + 1] = (byte) (header >> 8);
        System.arraycopy(literal, 0, data, offset + 2, literal.length);
        data[offset + 2 + literal.length] = 0;
        data[offset + 3 + literal.length] = 0;
    }
}
