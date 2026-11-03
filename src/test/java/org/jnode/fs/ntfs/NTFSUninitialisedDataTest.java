package org.jnode.fs.ntfs;

import java.util.Arrays;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jnode.fs.FileSystemTestUtils.*;

/**
 * Tests that a non-resident attribute reads as zeros past its initialised size, a.k.a. the valid data length.
 *
 * <p>Clusters are allocated to an attribute before they are written to, so the range between the initialised size
 * and the data size still holds whatever was on the disk beforehand - which may be the content of a deleted file.
 * NTFS returns that range as zeros, and so must we.</p>
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#non-resident-mft-attribute">Non-resident MFT attribute</a>
 */
public class NTFSUninitialisedDataTest {

    private static final int CLUSTER_SIZE = 512;
    private static final int RUN_CLUSTERS = 16;
    private static final int FILE_SIZE = RUN_CLUSTERS * CLUSTER_SIZE;

    /** Somewhere past the volume header for the attribute's data to live. */
    private static final int FIRST_DATA_CLUSTER = 8;

    /** A run of 16 clusters starting at cluster 8. */
    private static final String DATA_RUNS = "21 10 08 00 00";

    /** Recognisably non-zero, so uninitialised bytes coming through are obvious. */
    private static final byte STALE = (byte) 0xAB;

    /**
     * A device whose clusters are full of stale content.
     */
    private static byte[] device() {
        byte[] data = new byte[64 * CLUSTER_SIZE];
        Arrays.fill(data, FIRST_DATA_CLUSTER * CLUSTER_SIZE, data.length, STALE);
        return data;
    }

    /**
     * Builds a file record with one non-resident $DATA attribute over a volume full of stale content.
     *
     * @param startVcn        the attribute's first VCN, which is 0 unless it is a continuation fragment.
     * @param dataSize        the attribute's data size.
     * @param initialisedSize the attribute's initialised size.
     * @return the record.
     */
    private static FileRecord fileRecord(long startVcn, long dataSize, long initialisedSize) throws Exception {
        NTFSVolume volume = NTFSTestVolume.volume(CLUSTER_SIZE, device());
        byte[] attribute = NTFSTestRecords.nonResidentAttribute(startVcn, startVcn + RUN_CLUSTERS - 1, 0, 0,
            toByteArray(DATA_RUNS), FILE_SIZE, dataSize, initialisedSize);

        return NTFSTestRecords.record(volume, CLUSTER_SIZE, attribute);
    }

    /**
     * An attribute with an initialised size of zero has had nothing written to it, so the whole of it reads as
     * zeros however much has been allocated. This is the state of the VSS store files under
     * {@code System Volume Information}, which were coming back as raw disk content.
     */
    @Test
    public void testAttributeWithNothingInitialisedReadsAsZeros() throws Exception {
        // Arrange
        FileRecord record = fileRecord(0, FILE_SIZE, 0);

        // Act
        byte[] actual = new byte[FILE_SIZE];
        record.readData(0, actual, 0, actual.length);

        // Assert
        assertThat(actual, is(new byte[FILE_SIZE]));
    }

    /**
     * The initialised size is not cluster aligned, so the tail of the last initialised cluster is stale too.
     */
    @Test
    public void testAttributeReadsAsZerosPastTheInitialisedSize() throws Exception {
        // Arrange: 600 bytes written, which is part way into the second cluster
        int initialised = 600;
        FileRecord record = fileRecord(0, FILE_SIZE, initialised);

        // Act
        byte[] actual = new byte[FILE_SIZE];
        record.readData(0, actual, 0, actual.length);

        // Assert
        byte[] expected = new byte[FILE_SIZE];
        Arrays.fill(expected, 0, initialised, STALE);

        assertThat(actual, is(expected));
    }

    /**
     * A read that starts past the initialised size is all zeros as well.
     */
    @Test
    public void testReadStartingPastTheInitialisedSizeIsZeros() throws Exception {
        // Arrange
        FileRecord record = fileRecord(0, FILE_SIZE, 600);

        // Act
        byte[] actual = new byte[CLUSTER_SIZE];
        record.readData(4 * CLUSTER_SIZE, actual, 0, actual.length);

        // Assert
        assertThat(actual, is(new byte[CLUSTER_SIZE]));
    }

    /**
     * An attribute that is fully initialised is unaffected.
     */
    @Test
    public void testFullyInitialisedAttributeIsUnaffected() throws Exception {
        // Arrange
        FileRecord record = fileRecord(0, FILE_SIZE, FILE_SIZE);

        // Act
        byte[] actual = new byte[FILE_SIZE];
        record.readData(0, actual, 0, actual.length);

        // Assert
        byte[] expected = new byte[FILE_SIZE];
        Arrays.fill(expected, STALE);

        assertThat(actual, is(expected));
    }

    /**
     * Only the fragment starting at VCN 0 records the sizes: a fragment continuing a split attribute carries zero in
     * all three, which means 'not recorded' rather than 'nothing initialised'. Reading one of those as zeros would
     * blank out the tail of every attribute that spans more than one MFT record.
     */
    @Test
    public void testContinuationFragmentWithNoRecordedSizesStillReadsItsData() throws Exception {
        // Arrange: a fragment starting at VCN 16, with the size fields zeroed as NTFS leaves them
        FileRecord record = fileRecord(RUN_CLUSTERS, 0, 0);

        // Act
        byte[] actual = new byte[FILE_SIZE];
        record.readData(0, actual, 0, actual.length);

        // Assert
        byte[] expected = new byte[FILE_SIZE];
        Arrays.fill(expected, STALE);

        assertThat(actual, is(expected));
    }
}
