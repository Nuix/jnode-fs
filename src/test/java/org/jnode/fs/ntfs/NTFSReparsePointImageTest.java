package org.jnode.fs.ntfs;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.ReparsePointAttribute;
import org.jnode.fs.ntfs.attribute.ReparsePointAttributeNonRes;
import org.jnode.fs.ntfs.attribute.ReparsePointAttributeRes;
import org.jnode.fs.ntfs.attribute.ReparsePointTags;
import org.jnode.fs.service.FileSystemService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Reads reparse points whose data is too large to stay resident.
 *
 * <p>{@link ReparsePointAttributeNonRes} had no coverage at all: nothing in the corpus has a reparse point of
 * either kind. This volume was built on Windows Server 2022 with symlinks whose targets run to about 1900
 * characters - the reparse buffer holds both the substitute and the print name in UTF-16, so the attribute data
 * reaches 7.8 KB and cannot fit in the MFT record.</p>
 *
 * <pre>
 * diskpart: create vdisk maximum=64 type=fixed, create partition primary, format fs=ntfs
 * mklink /D X:\symlink8 \\?\C:\&lt;240 chars&gt;\...       # x8, non-resident
 * mklink /J X:\junction X:\realdir                      # resident
 * </pre>
 *
 * <p>The Sleuth Kit reads the four long symlinks as non-resident $REPARSE_POINT of 1012, 1980, 3916 and 7788
 * bytes, and the junction and short symlink as resident of 68 bytes.</p>
 */
public class NTFSReparsePointImageTest {

    private static final String IMAGE = "org/jnode/fs/ntfs/ntfs-reparse-nonresident.raw";

    private static File testFile;
    private static FileDevice device;
    private static NTFSFileSystem fs;

    @BeforeClass
    public static void setUpClass() throws Exception {
        FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
        testFile = FileSystemTestUtils.getTestFile(IMAGE);
        device = new FileDevice(testFile, "r");
        fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);
    }

    @AfterClass
    public static void tearDownClass() {
        fs = null;
        if (device != null) {
            device.close();
        }
        if (testFile != null) {
            testFile.delete();
        }
    }

    /**
     * The data lengths, pinned against what The Sleuth Kit reports.
     *
     * <p>This does not discriminate the 16-bit read of the length from a 32-bit one: the reserved field that
     * follows it is zero on real symlinks, so both give the same answer. That case is covered synthetically in
     * {@link NTFSReparseDataLengthTest}, which sets the reserved field. What this covers is
     * {@link ReparsePointAttributeNonRes} at all, which nothing did before.</p>
     */
    @Test
    public void testReparseDataLengths() throws Exception {
        List<Integer> residentLengths = new ArrayList<>();
        List<Integer> nonResidentLengths = new ArrayList<>();

        for (long reference = 0; reference < 64; reference++) {
            ReparsePointAttribute attribute = reparsePointOf(reference);
            if (attribute == null) {
                continue;
            }

            assertThat("record " + reference + " tag", attribute.getReparseTag(),
                anyOf(is(ReparsePointTags.IO_REPARSE_TAG_SYMLINK),
                      is(ReparsePointTags.IO_REPARSE_TAG_MOUNT_POINT)));

            if (attribute instanceof ReparsePointAttributeNonRes) {
                nonResidentLengths.add(attribute.getReparseDataLength());
            } else {
                residentLengths.add(attribute.getReparseDataLength());
            }
        }

        Collections.sort(residentLengths);
        Collections.sort(nonResidentLengths);

        // The four long symlinks, whose targets grow by a factor of two each time. These are eight less than the
        // attribute sizes The Sleuth Kit reports (1012, 1980, 3916, 7788): this field is the length of the data
        // *after* the 4-byte tag, 2-byte length and 2-byte reserved field that precede it.
        assertThat("non-resident reparse data lengths", nonResidentLengths, contains(1004, 1972, 3908, 7780));

        // The junction and the short symlink, 68 bytes of attribute each
        assertThat("resident reparse data lengths", residentLengths, contains(60, 60));
    }

    /**
     * Reads the symlink targets back out of the reparse data. The lengths alone would pass even if the data runs
     * were never followed, since the length lives in the attribute header; decoding the target means the
     * non-resident data itself has been read.
     */
    @Test
    public void testSymlinkTargetsAreReadBack() throws Exception {
        List<String> targets = new ArrayList<>();

        for (long reference = 0; reference < 64; reference++) {
            ReparsePointAttribute attribute = reparsePointOf(reference);
            if (!(attribute instanceof ReparsePointAttributeNonRes)) {
                continue;
            }

            ReparsePointAttributeNonRes nonResident = (ReparsePointAttributeNonRes) attribute;
            assertThat(nonResident.getReparseTag(), is(ReparsePointTags.IO_REPARSE_TAG_SYMLINK));

            // SYMBOLIC_LINK_REPARSE_BUFFER: the path buffer starts at 0x14, and the substitute name is at the
            // offset and length recorded at 0x08 and 0x0a
            int nameOffset = nonResident.getUInt16(0x08);
            int nameLength = nonResident.getUInt16(0x0a);
            StringBuilder target = new StringBuilder();
            for (int i = 0; i < nameLength / 2; i++) {
                target.append(nonResident.getChar16(0x14 + nameOffset + i * 2));
            }
            targets.add(target.toString());
        }

        assertThat("non-resident symlinks", targets, hasSize(4));

        for (String target : targets) {
            assertThat("the target should be the long path the symlink was made with",
                target, containsString(repeat('d', 240)));
        }

        List<Integer> lengths = new ArrayList<>();
        for (String target : targets) {
            lengths.add(target.length());
        }
        Collections.sort(lengths);

        // One, two, four and eight segments: a 7 character prefix, then n segments of 241 joined by n-1
        // separators, i.e. 242n + 6
        assertThat("target lengths", lengths, contains(248, 490, 974, 1942));
    }

    private static String repeat(char c, int times) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    /**
     * Gets the reparse point attribute of a record, or {@code null} if it has none.
     */
    private static ReparsePointAttribute reparsePointOf(long reference) throws Exception {
        FileRecord record;

        try {
            record = fs.getNTFSVolume().getMFT().getRecord(reference);
        } catch (Exception e) {
            return null;
        }

        NTFSAttribute attribute = record.findAttributeByType(NTFSAttribute.Types.REPARSE_POINT);
        if (attribute instanceof ReparsePointAttributeNonRes) {
            return (ReparsePointAttributeNonRes) attribute;
        }
        if (attribute instanceof ReparsePointAttributeRes) {
            return (ReparsePointAttributeRes) attribute;
        }

        return null;
    }
}
