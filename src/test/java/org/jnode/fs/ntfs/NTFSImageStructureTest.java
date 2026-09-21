package org.jnode.fs.ntfs;

import java.io.File;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSEntry;
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
 * Checks against a real volume, using the in-tree test image. Shares a single copy of the image across the tests
 * in this class, since it decompresses to 419 MB.
 */
public class NTFSImageStructureTest {

    private static File testFile;
    private static FileDevice device;
    private static NTFSFileSystem fs;

    @BeforeClass
    public static void setUpClass() throws Exception {
        FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/ntfs/complex-compression.dd");
        device = new FileDevice(testFile, "r");
        fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);
    }

    @AfterClass
    public static void tearDownClass() {
        if (device != null) {
            device.close();
        }
        if (testFile != null) {
            testFile.delete();
        }
    }

    /**
     * $Secure:$SDS is laid out in 256 KiB blocks with no entry crossing a boundary, so the remainder of a block is
     * zero padded. Enumeration has to step over that padding rather than treat it as the end of the stream, since a
     * volume with more than a block of descriptors carries on in the next one.
     *
     * <p>Every descriptor is also written twice, the mirror a block after the original, so stepping over the
     * padding reaches the mirrors. Those are the same descriptors and must not be handed back a second time.</p>
     */
    @Test
    public void testSecurityDescriptorStreamSkipsBlockPaddingWithoutRepeatingTheMirror() throws Exception {
        // Arrange
        FileRecord secure = fs.getNTFSVolume().getMFT().getRecord(MasterFileTable.SystemFiles.SECURE);
        Map<String, FSFile> streams = new NTFSFile(fs, secure).getStreams();
        assertThat(streams.keySet(), hasItem("$SDS"));
        NTFSFile.StreamFile sdsFile = (NTFSFile.StreamFile) streams.get("$SDS");

        // Guard the pre-condition: the stream really does run past the first 256 KiB block, i.e. it holds the
        // mirror, so that the assertions below are not vacuous
        assertThat("the stream should extend past the first block", sdsFile.getLength(), is(greaterThan(0x40000L)));
        assertThat("the mirror of the first descriptor should be at 0x40000",
            new SecurityDescriptorStream(sdsFile).readOneEntry(0x40000).getSecurityId(), is(256));

        // Act
        List<SecurityDescriptorStreamEntry> entries = new SecurityDescriptorStream(sdsFile).getEntries();

        // Assert: the 9 distinct descriptors on this volume, each once
        assertThat(entries, hasSize(9));

        Set<Integer> securityIds = new LinkedHashSet<>();
        for (SecurityDescriptorStreamEntry entry : entries) {
            assertThat("every entry should carry a security descriptor",
                entry.getSecurityDescriptor().getRevision(), is(1));
            assertThat("duplicate security id " + entry.getSecurityId(),
                securityIds.add(entry.getSecurityId()), is(true));
        }

        assertThat(securityIds, contains(256, 257, 258, 259, 260, 261, 262, 263, 264));
    }

    /**
     * Guards the fall back to the MFT record flags in {@link NTFSEntry#isDirectory()}: everything in this volume
     * does set the $FILE_NAME flag, so the two sources must agree for every entry.
     */
    @Test
    public void testDirectoryDetectionAgreesWithTheFileRecord() throws Exception {
        int checked = 0;

        for (Iterator<? extends FSEntry> it = fs.getRootEntry().getDirectory().iterator(); it.hasNext(); ) {
            NTFSEntry entry = (NTFSEntry) it.next();
            boolean fromRecord = entry.getFileRecord().isDirectory();

            assertThat("isDirectory disagrees with the MFT record for '" + entry.getName() + "'",
                entry.isDirectory(), is(fromRecord));
            assertThat("isFile should be the inverse of isDirectory for '" + entry.getName() + "'",
                entry.isFile(), is(!fromRecord));
            checked++;
        }

        assertThat("expected to walk the root directory", checked, is(greaterThan(0)));
    }
}
