package org.jnode.fs.ntfs;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSFile;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.security.SecurityDescriptorStream;
import org.jnode.fs.ntfs.security.SecurityDescriptorStreamEntry;
import org.jnode.fs.service.FileSystemService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Enumerates a $Secure:$SDS that runs well past its first 256 KiB block.
 *
 * <p>Every other volume to hand has a handful of descriptors, so $SDS is one block of data followed by its mirror
 * and the code that steps over the padding to reach the *next* block of descriptors never runs. This volume was
 * built on Windows Server 2022 with 6,000 files, each carrying an ACE for a different deliberately unresolvable
 * SID, so NTFS has to store 6,000 distinct descriptors.</p>
 *
 * <p>Varying the access mask instead does not work: .NET canonicalises the rights, and 6,000 masks collapsed to
 * 137 descriptors.</p>
 *
 * <p>The Sleuth Kit reads the stream as 1,459,680 bytes holding 12,018 entries - 6,009 descriptors, each written
 * twice - spread over six 256 KiB blocks.</p>
 */
public class NTFSLargeSecurityStreamTest {

    private static final String IMAGE = "org/jnode/fs/ntfs/ntfs-large-sds.raw";

    private static final long BLOCK_SIZE = 0x40000;

    private static File testFile;
    private static FileDevice device;
    private static NTFSFile.StreamFile sds;

    @BeforeClass
    public static void setUpClass() throws Exception {
        FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
        testFile = FileSystemTestUtils.getTestFile(IMAGE);
        device = new FileDevice(testFile, "r");
        NTFSFileSystem fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);

        FileRecord secure = fs.getNTFSVolume().getMFT().getRecord(MasterFileTable.SystemFiles.SECURE);
        Map<String, FSFile> streams = new NTFSFile(fs, secure).getStreams();
        sds = (NTFSFile.StreamFile) streams.get("$SDS");
    }

    @AfterClass
    public static void tearDownClass() {
        sds = null;
        if (device != null) {
            device.close();
        }
        if (testFile != null) {
            testFile.delete();
        }
    }

    @Test
    public void testDescriptorsPastTheFirstBlockAreReached() throws Exception {
        // Guard the pre-condition: the descriptors themselves have to outgrow one block, otherwise everything
        // below would pass on a volume that never needs the continuation
        assertThat("the stream should span several blocks", sds.getLength(), is(greaterThan(4 * BLOCK_SIZE)));

        // Act
        List<SecurityDescriptorStreamEntry> entries = new SecurityDescriptorStream(sds).getEntries();

        // Assert: 6,009 distinct descriptors, each stored twice, and none handed back twice
        assertThat("descriptors", entries, hasSize(6009));

        Set<Integer> ids = new HashSet<>();
        int pastFirstBlock = 0;
        for (SecurityDescriptorStreamEntry entry : entries) {
            assertThat("duplicate security id " + entry.getSecurityId(), ids.add(entry.getSecurityId()), is(true));
            assertThat("every entry should carry a descriptor", entry.getSecurityDescriptor().getRevision(), is(1));

            if (Integer.toUnsignedLong(entry.getOffsetToEntry()) >= BLOCK_SIZE) {
                pastFirstBlock++;
            }
        }

        assertThat("descriptors recorded past the first block", pastFirstBlock, is(greaterThan(0)));
    }

    /**
     * The mirror of each descriptor sits a block after the original. Reading one directly shows the stream really
     * does hold each twice, so the de-duplication above is doing something.
     */
    @Test
    public void testTheStreamHoldsEachDescriptorTwice() throws Exception {
        SecurityDescriptorStream stream = new SecurityDescriptorStream(sds);

        SecurityDescriptorStreamEntry first = stream.readOneEntry(0);
        SecurityDescriptorStreamEntry mirror = stream.readOneEntry(BLOCK_SIZE);

        assertThat(first, is(notNullValue()));
        assertThat(mirror, is(notNullValue()));
        assertThat("the mirror should repeat the first descriptor",
            mirror.getSecurityId(), is(first.getSecurityId()));
    }
}
