package org.jnode.fs.ntfs;

import java.io.File;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.fs.service.FileSystemService;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.fail;

/**
 * Tests that data past the valid data length (a.k.a. the initialised size) reads back as zeros.
 *
 * <p>Windows fills the range between the valid data length and the end of the allocated data with zeros when
 * reading. The data size is unchanged, so the on-disk content of that range is whatever happened to be in the
 * cluster and must not be handed back to the caller.</p>
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#non-resident-mft-attribute">Non-resident MFT attribute</a>
 */
public class NTFSValidDataLengthTest {

    /**
     * MFT records in {@code complex-compression.dd} whose last initialised cluster is only partially valid, and
     * which extend past that cluster. These have non-zero bytes on disk between the valid data length and the end
     * of the cluster holding it.
     */
    private static final long[] PARTIALLY_INITIALISED_RECORDS = {591, 898, 950};

    private FileSystemService fss;

    @Before
    public void setUp() throws Exception {
        fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
    }

    @Test
    public void testReadPastValidDataLengthReturnsZeros() throws Exception {
        // The image decompresses to 419 MB and getTestFile leaves deleting the copy to the caller
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/ntfs/complex-compression.dd");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            NTFSFileSystemType type = fss.getFileSystemType(NTFSFileSystemType.ID);
            NTFSFileSystem fs = type.create(device, true);
            MasterFileTable mft = fs.getNTFSVolume().getMFT();

            for (long reference : PARTIALLY_INITIALISED_RECORDS) {
                FileRecord record = mft.getRecord(reference);
                NTFSNonResidentAttribute data =
                    (NTFSNonResidentAttribute) record.findAttributeByType(NTFSAttribute.Types.DATA);

                long dataSize = data.getAttributeActualSize();
                long validDataLength = data.getAttributeInitializedSize();

                // Guard the pre-conditions, so that a change to the test image shows up as a clear failure here
                // rather than as a silently vacuous assertion below.
                assertThat("record " + reference + " should be compressed", data.isCompressedAttribute(), is(true));
                assertThat("record " + reference + " should be partially initialised",
                    validDataLength, is(both(greaterThan(0L)).and(lessThan(dataSize))));

                byte[] buffer = new byte[(int) dataSize];
                record.readData(0, buffer, 0, buffer.length);

                for (int i = (int) validDataLength; i < buffer.length; i++) {
                    if (buffer[i] != 0) {
                        fail(String.format(
                            "record %d ('%s'): byte %d is past the valid data length (%d of %d bytes) so it must " +
                                "read as zero, but was 0x%02x",
                            reference, record.getFileName(-1), i, validDataLength, dataSize, buffer[i]));
                    }
                }
            }
        } finally {
            testFile.delete();
        }
    }
}
