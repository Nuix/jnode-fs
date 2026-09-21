package org.jnode.fs.ntfs;

import java.io.File;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Parses every record of a real $MFT, as breadth against data nobody here wrote.
 *
 * <p>The rest of the corpus is small purpose-built volumes; this is the master file table of a working Windows XP
 * machine, 12,305 records with the attribute mix that accumulates on a real system. It is the $MFT of the NIST
 * CFReDS Hacking Case, extracted from the published {@code SCHARDT.001-008} raw image with
 * {@code extract_mft.py}. Works of NIST employees are not subject to copyright in the United States; note though
 * that the image it came from is of a Windows XP install, so it is only the file <em>metadata</em> that is
 * reproduced here, not the content of any file on it.</p>
 *
 * <p>Record 5686 is a {@code BAAD} record, one that failed its fix-up and was marked corrupt. Parsing has to
 * step over it rather than throw.</p>
 *
 * @see <a href="https://cfreds-archive.nist.gov/Hacking_Case.html">NIST CFReDS Hacking Case</a>
 */
public class NTFSMasterFileTableSweepTest {

    private static final String IMAGE = "org/jnode/fs/ntfs/nist-hacking-case.mft";

    private static final int RECORD_SIZE = 1024;

    private static final int CLUSTER_SIZE = 512;

    private static byte[] mft;

    @BeforeClass
    public static void setUpClass() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile(IMAGE);
        try {
            mft = Files.readAllBytes(testFile.toPath());
        } finally {
            testFile.delete();
        }
    }

    @AfterClass
    public static void tearDownClass() {
        mft = null;
    }

    /**
     * Walks every record and every attribute in it. The point is that nothing throws and the totals do not drift:
     * an attribute walk that stops early or runs on into the next record shows up as a changed count.
     */
    @Test
    public void testEveryRecordParses() throws Exception {
        int records = mft.length / RECORD_SIZE;
        int fileRecords = 0;
        int inUse = 0;
        int directories = 0;
        int attributes = 0;
        int corrupt = 0;
        Map<NTFSAttribute.Types, Integer> byType = new EnumMap<>(NTFSAttribute.Types.class);

        for (int index = 0; index < records; index++) {
            int offset = index * RECORD_SIZE;

            if (!isFileRecord(offset)) {
                corrupt++;
                continue;
            }

            fileRecords++;
            FileRecord record = new FileRecord(null, CLUSTER_SIZE, false, index, mft, offset);

            if (record.isInUse()) {
                inUse++;
            }
            if (record.isDirectory()) {
                directories++;
            }

            List<NTFSAttribute> stored = record.readStoredAttributes();
            attributes += stored.size();

            for (NTFSAttribute attribute : stored) {
                NTFSAttribute.Types type = attribute.getAttributeType();
                assertThat("record " + index + " has an unrecognised attribute type", type, is(notNullValue()));
                byType.merge(type, 1, Integer::sum);
            }
        }

        assertThat("records", records, is(12305));
        assertThat("records that are not FILE records", corrupt, is(1));
        assertThat("FILE records", fileRecords, is(12304));
        assertThat("records in use", inUse, is(12301));
        assertThat("directories", directories, is(773));
        assertThat("attributes parsed", attributes, is(49429));

        // The attribute mix of a real volume, which the purpose-built images do not have
        assertThat(byType.get(NTFSAttribute.Types.STANDARD_INFORMATION), is(12183));
        assertThat(byType.get(NTFSAttribute.Types.FILE_NAME), is(14832));
        assertThat(byType.get(NTFSAttribute.Types.DATA), is(11460));
        assertThat(byType.get(NTFSAttribute.Types.ATTRIBUTE_LIST), is(74));

        // Every directory carries an $INDEX_ROOT, so these two have to agree
        assertThat(byType.get(NTFSAttribute.Types.INDEX_ROOT), is(directories));
        assertThat(byType.keySet(), hasItems(NTFSAttribute.Types.ATTRIBUTE_LIST,
            NTFSAttribute.Types.INDEX_ALLOCATION, NTFSAttribute.Types.BITMAP,
            NTFSAttribute.Types.SECURITY_DESCRIPTOR, NTFSAttribute.Types.OBJECT_ID));
    }

    /**
     * File names come back intact across the whole table, including the ones that made this case famous.
     */
    @Test
    public void testFileNames() throws Exception {
        int named = 0;
        boolean foundEthereal = false;

        for (int index = 0; index < mft.length / RECORD_SIZE; index++) {
            int offset = index * RECORD_SIZE;
            if (!isFileRecord(offset)) {
                continue;
            }

            FileRecord record = new FileRecord(null, CLUSTER_SIZE, false, index, mft, offset);
            String name = null;

            for (NTFSAttribute attribute : record.readStoredAttributes()) {
                if (attribute instanceof FileNameAttribute) {
                    FileNameAttribute fileName = (FileNameAttribute) attribute;
                    if (name == null || fileName.getNameSpace() == FileNameAttribute.NameSpace.WIN32) {
                        name = fileName.getFileName();
                    }
                }
            }

            if (name != null && !name.isEmpty()) {
                named++;
                foundEthereal |= name.toLowerCase().startsWith("ethereal");
            }
        }

        assertThat("records with a file name", named, is(12181));
        assertThat("the Ethereal install should be in the table", foundEthereal, is(true));
    }

    /**
     * Checks a record starts with the FILE magic, without building a FileRecord, since the constructor applies
     * fix-ups.
     */
    private static boolean isFileRecord(int offset) {
        return mft[offset] == 'F' && mft[offset + 1] == 'I' && mft[offset + 2] == 'L' && mft[offset + 3] == 'E';
    }
}
