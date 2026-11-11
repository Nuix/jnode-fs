package org.jnode.fs.ntfs;

import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.fs.ntfs.datarun.DataRun;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Reads the X-Ways sample $MFT, whose deleted records have a stale used size that stops before attributes that are
 * still intact. See {@code SOURCE.md} for where it comes from.
 */
public class NTFSAttributesPastUsedSizeTest {

    private static final String IMAGE = "org/jnode/fs/ntfs/xways-sample.mft";

    private static final int RECORD_SIZE = 1024;

    private static final int CLUSTER_SIZE = 4096;

    /**
     * The deleted records whose $DATA lies past the used size.
     */
    private static final int[] DATA_PAST_USED_SIZE = { 179, 180, 496, 524, 531, 532, 546, 554, 557, 566, 567, 568 };

    /**
     * The deleted directories whose $INDEX_ROOT lies past the used size.
     */
    private static final int[] INDEX_ROOT_PAST_USED_SIZE = { 530, 538, 540, 547 };

    private static byte[] mft;

    @BeforeClass
    public static void setUpClass() throws Exception {
        mft = FileSystemTestUtils.readImage(IMAGE);
    }

    private static FileRecord record(int index) throws Exception {
        return new FileRecord(null, CLUSTER_SIZE, false, index, mft, index * RECORD_SIZE);
    }

    @Test
    public void testRecord179KeepsItsData() throws Exception {
        FileRecord record = record(179);

        NTFSNonResidentAttribute data =
            (NTFSNonResidentAttribute) record.findAttributeByType(NTFSAttribute.Types.DATA);

        assertThat(data, is(notNullValue()));
        assertThat(record.getAttributeTotalSize(NTFSAttribute.Types.DATA, null), is(34304L));
        DataRun firstRun = (DataRun) data.getDataRunDecoder().getDataRuns().get(0);
        assertThat(firstRun.getCluster(), is(3614L));
    }

    @Test
    public void testEveryDeletedRecordKeepsItsData() throws Exception {
        for (int index : DATA_PAST_USED_SIZE) {
            assertThat("record " + index, record(index).findAttributeByType(NTFSAttribute.Types.DATA),
                is(notNullValue()));
        }
        for (int index : INDEX_ROOT_PAST_USED_SIZE) {
            assertThat("record " + index, record(index).findAttributeByType(NTFSAttribute.Types.INDEX_ROOT),
                is(notNullValue()));
        }
    }

    /**
     * Record 532's only name, and record 554's Win32 name, are past the used size.
     */
    @Test
    public void testNamesPastTheUsedSizeAreKept() throws Exception {
        assertThat(record(532).getFileNameAttributes(), hasSize(1));
        assertThat(record(554).getFileNameAttributes(), hasSize(2));
    }
}
