package org.jnode.fs.ntfs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.fs.service.FileSystemService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Merges the attributes of a record that has an {@code $ATTRIBUTE_LIST}.
 *
 * <p>Record 39 of {@code ntfs-wof-fragmented} is {@code frag/mixed-xpress4k.bin}. Its {@code WofCompressedData}
 * stream runs to 276 runs, so it lives in an extension record (42), while the base record keeps the resident
 * {@code $FILE_NAME} and {@code $REPARSE_POINT} and the unnamed {@code $DATA}. The attribute list names those base
 * record attributes too.</p>
 */
public class NTFSAttributeListImageTest {

    private static final long BASE_RECORD = 39;

    private static TestImageDevice device;
    private static FileRecord record;

    @BeforeClass
    public static void setUpClass() throws Exception {
        FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
        device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/ntfs-wof-fragmented.raw");
        NTFSFileSystem fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);
        record = fs.getNTFSVolume().getMFT().getRecord(BASE_RECORD);
    }

    @AfterClass
    public static void tearDownClass() {
        record = null;
        if (device != null) {
            device.close();
        }
    }

    /**
     * The attributes the list points back into the base record are the stored ones, and must not be added twice.
     */
    @Test
    public void testBaseRecordAttributesAreListedOnce() {
        Set<NTFSAttribute> seen = new LinkedHashSet<>();
        List<String> duplicates = new ArrayList<>();

        for (NTFSAttribute attribute : record.getAllAttributes()) {
            if (!seen.add(attribute)) {
                duplicates.add(attribute.getAttributeType() + " " + attribute.getAttributeName());
            }
        }

        assertThat(duplicates, is(empty()));
        assertThat(record.getFileNameAttributes(), hasSize(1));
    }

    /**
     * The list does not name itself, so it is kept from the stored attributes.
     */
    @Test
    public void testUnlistedStoredAttributeIsKept() {
        assertThat(record.findAttributeByType(NTFSAttribute.Types.ATTRIBUTE_LIST), is(notNullValue()));
    }

    /**
     * {@code readData()} refuses a resident attribute that appears more than once, so a duplicate made every
     * resident attribute of such a record unreadable.
     */
    @Test
    public void testResidentAttributeIsReadable() throws Exception {
        byte[] reparse = new byte[24];
        record.readData(NTFSAttribute.Types.REPARSE_POINT, null, 0, reparse, 0, reparse.length, true);

        // IO_REPARSE_TAG_WOF, 16 bytes of data, then WOF_EXTERNAL_INFO {version 1, provider 2 (file)}
        assertThat(Arrays.copyOf(reparse, 16), is(new byte[] {
            0x17, 0, 0, (byte) 0x80, 16, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0}));
    }

    /**
     * The extension record's fragment of the stream is still merged in, so all 276 runs are found.
     */
    @Test
    public void testExtensionRecordAttributeIsMerged() {
        List<NTFSAttribute> streams = new ArrayList<>();
        record.findAttributesByTypeAndName(NTFSAttribute.Types.DATA, FileRecord.WOF_STREAM_NAME)
            .forEachRemaining(streams::add);

        assertThat(streams, hasSize(1));
        NTFSNonResidentAttribute stream = (NTFSNonResidentAttribute) streams.get(0);
        assertThat(stream.getDataRunDecoder().getDataRuns(), hasSize(276));
    }
}
