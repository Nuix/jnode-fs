package org.jnode.fs.ntfs;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.datarun.CompressionUnitProblem;
import org.jnode.fs.service.FileSystemService;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * Every compressed file that Windows wrote must read without a corrupt compression unit.
 */
public class NTFSCorruptCompressionUnitImageTest {

    @Test
    public void testComplexCompression() throws Exception {
        assertThat(checkEveryCompressedFile("org/jnode/fs/ntfs/complex-compression.dd"), is(greaterThan(0)));
    }

    @Test
    public void testSplitCompressionUnits() throws Exception {
        assertThat(checkEveryCompressedFile("org/jnode/fs/ntfs/ntfs-split-compression-units.raw"),
            is(greaterThan(0)));
    }

    /**
     * Reads every in-use file with a compressed $DATA and checks that no unit failed strict LZNT1.
     *
     * @return the number of compressed files read.
     */
    private static int checkEveryCompressedFile(String image) throws Exception {
        int compressedFiles = 0;
        List<String> corrupt = new ArrayList<>();

        try (TestImageDevice device = FileSystemTestUtils.openImage(image)) {
            FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
            NTFSFileSystem fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);
            MasterFileTable mft = fs.getNTFSVolume().getMFT();
            long records = mft.getMftLength() / fs.getNTFSVolume().getBootRecord().getFileRecordSize();

            for (long index = 0; index < records; index++) {
                FileRecord record;
                try {
                    record = mft.getRecord(index);
                } catch (Exception e) {
                    continue;
                }

                // An extension record holds part of another record's attributes and isn't a file of its own
                if (!record.isInUse() || record.getBaseReferenceNumber() != 0) {
                    continue;
                }

                NTFSAttribute data = record.findAttributeByType(NTFSAttribute.Types.DATA);
                if (data == null || data.isResident() || !data.isCompressedAttribute()) {
                    continue;
                }

                compressedFiles++;
                long size = record.getAttributeTotalSize(NTFSAttribute.Types.DATA, null);
                byte[] buffer = new byte[(int) Math.min(size, 1 << 20)];
                for (long offset = 0; offset < size; offset += buffer.length) {
                    int length = (int) Math.min(buffer.length, size - offset);
                    record.readData(NTFSAttribute.Types.DATA, null, offset, buffer, 0, length, true);
                }

                SortedMap<Long, Set<CompressionUnitProblem>> units =
                    record.getCorruptCompressionUnits(NTFSAttribute.Types.DATA, null);
                if (!units.isEmpty()) {
                    corrupt.add(record + " " + units);
                }


            }
        }

        assertThat(image + " has compressed files with corrupt units", corrupt, is(empty()));
        return compressedFiles;
    }
}
