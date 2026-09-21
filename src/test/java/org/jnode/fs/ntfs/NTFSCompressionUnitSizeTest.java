package org.jnode.fs.ntfs;

import java.util.List;

import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.fs.ntfs.datarun.DataRunInterface;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jnode.fs.FileSystemTestUtils.*;
import static org.jnode.fs.ntfs.NTFSTestRecords.*;

/**
 * Tests how the compression unit size recorded in a non-resident attribute is interpreted.
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#non-resident-mft-attribute">Non-resident MFT attribute</a>
 */
public class NTFSCompressionUnitSizeTest {

    /** The compressed data flag. */
    private static final int COMPRESSED = 0x0001;

    /** A compressed unit stored in 7 clusters, followed by the 9 sparse clusters that pad it out to 16. */
    private static final String COMPRESSED_RUNS = "21 07 B4 08 01 09 00";

    private static List<DataRunInterface> decode(int flags, int storedCompressionUnit) throws Exception {
        NTFSNonResidentAttribute attribute =
            nonResident(0, 15, flags, storedCompressionUnit, toByteArray(COMPRESSED_RUNS));

        attribute.getDataRunDecoder().readDataRuns(attribute, attribute.getDataRunsOffset());
        return attribute.getDataRuns();
    }

    /**
     * Compressed attribute data with a stored compression unit size of 0 has been seen on Windows XP. Taking that
     * literally gives a unit of 1 cluster, under which every run is a whole unit and nothing is ever decompressed
     * - the compressed bytes are handed back raw.
     */
    @Test
    public void testCompressedAttributeWithAZeroCompressionUnit() throws Exception {
        // Act
        List<DataRunInterface> dataRuns = decode(COMPRESSED, 0);

        // Assert
        assertThat(dataRuns, hasSize(1));
        assertThat(dataRuns.get(0).toString(),
            is("[compressed-run vcn:0-15 [[data-run vcn:0-6 cluster:2228]]]"));
    }

    /**
     * The ordinary case: 2^4 = 16 clusters.
     */
    @Test
    public void testCompressedAttributeWithTheUsualCompressionUnit() throws Exception {
        // Act
        List<DataRunInterface> dataRuns = decode(COMPRESSED, 4);

        // Assert
        assertThat(dataRuns, hasSize(1));
        assertThat(dataRuns.get(0).toString(),
            is("[compressed-run vcn:0-15 [[data-run vcn:0-6 cluster:2228]]]"));
    }

    /**
     * An attribute that is not flagged as compressed keeps a unit of 1 whatever is stored, so its runs are decoded
     * as plain data runs.
     */
    @Test
    public void testUncompressedAttributeIsUnaffected() throws Exception {
        // Act
        List<DataRunInterface> dataRuns = decode(0, 0);

        // Assert
        assertThat(dataRuns, hasSize(2));
        assertThat(dataRuns.get(0).toString(), is("[data-run vcn:0-6 cluster:2228]"));
        assertThat(dataRuns.get(1).toString(), is("[sparse-run vcn:7-15 cluster:0]"));
    }
}
