package org.jnode.fs.ntfs.datarun;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.jnode.fs.ntfs.NTFSStructure;
import org.jnode.fs.util.FSUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A decoder for the NTFS non-resident data runs.
 *
 * @author Chira
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 * @author Daniel Noll (daniel@noll.id.au) (compression support)
 * @author Luke Quinane (luke.quinane@gmail.com)
 */
public class DataRunDecoder {

    /**
     * The logger.
     */
    protected static final Logger log = LoggerFactory.getLogger(DataRunDecoder.class);

    /**
     * A flag indicating whether the data runs are for a compressed attribute.
     */
    private final boolean compressed;

    /**
     * The compression unit. 2 to the power of this value is the number of clusters per compression unit.
     */
    private final int compressionUnit;

    /**
     * The decoded data runs.
     */
    @Getter
    private final List<DataRunInterface> dataRuns = new ArrayList<>();

    /**
     * The number of virtual clusters decoded.
     */
    @Getter
    private int numberOfVCNs;

    /**
     * The current virtual cluster number.
     */
    private long vcn;

    /**
     * Whether a sparse run is expected. If the attribute is compressed we will coalesce compressed/sparse
     * data run pairs into a single data run object for convenience when reading.
     */
    private boolean expectingSparseRunNext = false;

    /**
     * Whether this is the first data-run in the list.
     */
    private boolean firstDataRun = true;

    /**
     * The last compressed run size.
     */
    private long lastCompressedSize = 0;

    /**
     * The last compressed run to append to.
     */
    private CompressedDataRun lastCompressedRun;

    /**
     * Creates a new data run decoder.
     *
     * @param compressed      a flag indicating whether the data runs are from a compressed data attribute.
     * @param compressionUnit the compression unit size. 2 to the power of this value is the number of clusters
     *                        per compression unit.
     */
    public DataRunDecoder(boolean compressed, int compressionUnit) {
        this.compressed = compressed;
        this.compressionUnit = compressed ? compressionUnit : 1;
    }

    /**
     * Read the data runs and decodes them.
     *
     * @param parent         the parent structure.
     * @param offsetInParent the offset within the parent.
     */
    public void readDataRuns(NTFSStructure parent, int offsetInParent) {
        int offset = offsetInParent;
        long previousLCN = 0;

        while (parent.getUInt8(offset) != 0x0) {
            final DataRun dataRun = new DataRun(parent, offset, vcn, previousLCN);

            if (log.isDebugEnabled()) {
                log.debug("Data run at offset: {} {}", offset, dataRun);
            }

            if (compressed) {
                if (dataRun.isSparse() && (expectingSparseRunNext || firstDataRun)) {
                    // Also the sparse run following a compressed run can be coalesced with a subsequent 'real' sparse
                    // run. So add that in if we hit one
                    if (dataRun.getLength() + lastCompressedSize > compressionUnit) {
                        long length = dataRun.getLength() - (compressionUnit - lastCompressedSize);
                        dataRuns.add(new DataRun(0, length, true, 0, vcn));

                        this.numberOfVCNs += FSUtils.checkedCast(length);
                        vcn += length;
                        lastCompressedSize = 0;
                        // This is the sparse run which follows a compressed run.
                        // The number of runs it contains does not count towards the total
                        // as the compressed run reports holding all the runs for the pair.
                        // But we do need to move the offsets.
                        expectingSparseRunNext = false;
                    } else if (dataRun.getLength() + lastCompressedSize < compressionUnit) {
                        lastCompressedSize += dataRun.getLength(); // the size is all the clusters (including both compressed and sparse) in the last dataRun.
                        // this is a sparse run which follows a compressed run or another sparse run, and all of them are in the same compressedRun.
                        // the sum is less than compressionUnit, so another sparse run is expected after.
                        // E.g. the last Data Run can be a CompressedDataRun (assuming 16 is the expected clusters in each run) which contains
                        // [one 3-cluster compressedRun,
                        // one 10-cluster sparseRun,
                        // one 3-cluster sparseRun]
                        // And if we just finish reading the first sparseRun in this Run, then we expect the next few clusters to be sparse as well.

                        // Actually, according to the last sentence in https://flatcap.github.io/linux-ntfs/ntfs/concepts/data_runs.html,
                        // "Compressed and sparse runs can be intermixed. All this to save space.", so the following runs might be compressed run as well.
                        // But we didn't see that scenario in the test data.
                        expectingSparseRunNext = true;
                    } else { // dataRun.getLength() + lastCompressedSize == compressionUnit
                        lastCompressedSize = 0;
                        expectingSparseRunNext = false;
                    }
                } else if (dataRun.getLength() >= compressionUnit) {
                    // Compressed/sparse pairs always add to the compression unit size.  If
                    // the unit only compresses to 16, the system will store it uncompressed.
                    // Also if one-or more of these uncompressed runs happen next to each other then they can be
                    // coalesced into a single run and even coalesced into the next compressed run. In that case the
                    // compressed run needs to be split off

                    long remainder = dataRun.getLength() % compressionUnit;

                    if (remainder != 0) {
                        // Uncompressed run coalesced with compressed run. First add in the uncompressed portion:
                        long uncompressedLength = dataRun.getLength() - remainder;
                        DataRun uncompressed = new DataRun(dataRun.getCluster(), uncompressedLength, false, 0, vcn);
                        dataRuns.add(uncompressed);
                        vcn += uncompressedLength;
                        this.numberOfVCNs += FSUtils.checkedCast(uncompressedLength);

                        // Next add in the compressed portion
                        DataRun compressedRun =
                                new DataRun(dataRun.getCluster() + uncompressedLength, remainder, false, 0, vcn);
                        lastCompressedRun = new CompressedDataRun(compressedRun, compressionUnit);
                        dataRuns.add(lastCompressedRun);
                        expectingSparseRunNext = true;
                        lastCompressedSize = remainder;

                        this.numberOfVCNs += compressionUnit;
                        vcn += compressionUnit;

                    } else {
                        dataRuns.add(dataRun);
                        this.numberOfVCNs += FSUtils.checkedCast(dataRun.getLength());
                        vcn += dataRun.getLength();
                    }

                } else if (expectingSparseRunNext) {
                    // If a sparse data run was expected, but instead we got another regular data run, then this is
                    // likely a list of compressed data run parts inside the same compressed run. Create an adjusted
                    // run and add it to the parent run
                    long adjustedVcn = lastCompressedRun.getFirstVcn() + lastCompressedSize;
                    DataRun adjustedRun = new DataRun(parent, offset, adjustedVcn, previousLCN);
                    lastCompressedRun.addDataRun(adjustedRun);
                    lastCompressedSize += dataRun.getLength();

                } else {
                    lastCompressedRun = new CompressedDataRun(dataRun, compressionUnit);
                    dataRuns.add(lastCompressedRun);
                    expectingSparseRunNext = true;
                    lastCompressedSize = dataRun.getLength();

                    this.numberOfVCNs += compressionUnit;
                    vcn += compressionUnit;
                }
            } else {
                // map VCN -> datarun
                dataRuns.add(dataRun);
                this.numberOfVCNs += FSUtils.checkedCast(dataRun.getLength());
                vcn += dataRun.getLength();
                lastCompressedSize = 0;
                expectingSparseRunNext = false;
            }

            if (!dataRun.isSparse()) {
                previousLCN = dataRun.getCluster();
            }

            offset += dataRun.getSize();
            firstDataRun = false;
        }
        if (log.isDebugEnabled()) {
            log.debug("There are {} data runs in this NTFSStructure", dataRuns.size());
        }
    }

    /**
     * Performs some checks after decoding the data runs.
     *
     * @param clusterSize the cluster size.
     */
    public void checkDecoding(int clusterSize, long attributeAllocatedSize) {
        // Rounds up but won't work for 0, which shouldn't occur here.
        final long allocatedVCNs = (attributeAllocatedSize - 1) / clusterSize + 1;
        if (this.numberOfVCNs != allocatedVCNs) {
            // Probably not a problem, often multiple attributes make up one allocation.
            log.debug("VCN mismatch between data runs and allocated size, possibly a composite attribute. data run " +
                    "VCNs = {}, allocated size = {}, data run count = {}", this.numberOfVCNs, allocatedVCNs, dataRuns.size());
        }
    }
}
