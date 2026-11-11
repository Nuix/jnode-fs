package org.jnode.fs.ntfs;

import java.util.List;

import org.jnode.fs.ntfs.attribute.AttributeListBuilder;
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
        return decode(0, flags, storedCompressionUnit);
    }

    private static List<DataRunInterface> decode(long firstVcn, int flags, int storedCompressionUnit)
        throws Exception {
        NTFSNonResidentAttribute attribute =
            nonResident(firstVcn, firstVcn + 15, flags, storedCompressionUnit, toByteArray(COMPRESSED_RUNS));

        attribute.getDataRunDecoder().readDataRuns(attribute, attribute.getDataRunsOffset());
        return attribute.getDataRuns();
    }

    /**
     * A compressed attribute whose extent at VCN 0 stores a compression unit of 0 has no usable unit. Its runs are
     * read as plain data, as v1.0.1 did, rather than decoded as 16-cluster LZNT1, which makes up content.
     */
    @Test
    public void testZeroCompressionUnitOnTheFirstExtentReadsPlainRuns() throws Exception {
        // Act
        List<DataRunInterface> dataRuns = decode(0, COMPRESSED, 0);

        // Assert
        assertThat(dataRuns, hasSize(2));
        assertThat(dataRuns.get(0).toString(), is("[data-run vcn:0-6 cluster:2228]"));
        assertThat(dataRuns.get(1).toString(), is("[sparse-run vcn:7-15 cluster:0]"));
    }

    /**
     * Windows stores a compression unit of 0 on the later extents of a compressed attribute. Decoded on its own,
     * such an extent takes the usual 16 clusters.
     */
    @Test
    public void testZeroCompressionUnitOnALaterExtentUsesTheDefault() throws Exception {
        // Act
        List<DataRunInterface> dataRuns = decode(16, COMPRESSED, 0);

        // Assert: a run list decoded on its own counts VCNs from 0
        assertThat(dataRuns, hasSize(1));
        assertThat(dataRuns.get(0).toString(),
            is("[compressed-run vcn:0-15 [[data-run vcn:0-6 cluster:2228]]]"));
    }

    /**
     * A later extent's runs are read into the first extent's decoder, so they use the first extent's unit whatever
     * the later extent stores.
     */
    @Test
    public void testALaterExtentInheritsTheFirstExtentsUnit() throws Exception {
        // Arrange
        NTFSNonResidentAttribute first = nonResident(0, 15, COMPRESSED, 4, toByteArray(COMPRESSED_RUNS));
        NTFSNonResidentAttribute later = nonResident(16, 31, COMPRESSED, 0, toByteArray(COMPRESSED_RUNS));
        AttributeListBuilder builder = new AttributeListBuilder();

        // Act
        builder.add(first);
        builder.add(later);

        // Assert
        List<DataRunInterface> dataRuns = first.getDataRuns();
        assertThat(dataRuns, hasSize(2));
        assertThat(dataRuns.get(0).toString(),
            is("[compressed-run vcn:0-15 [[data-run vcn:0-6 cluster:2228]]]"));
        assertThat(dataRuns.get(1).toString(),
            is("[compressed-run vcn:16-31 [[data-run vcn:16-22 cluster:2228]]]"));
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
