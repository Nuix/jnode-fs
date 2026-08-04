package org.jnode.fs.ntfs;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
     * zero padded. Treating that padding as the end of the stream stopped enumeration at the first gap.
     *
     * <p>On this volume there are 9 distinct descriptors, written at the start of the stream and mirrored at
     * 0x40000, so all 18 should be enumerated.</p>
     */
    @Test
    public void testSecurityDescriptorStreamSkipsBlockPadding() throws Exception {
        // Arrange
        FileRecord secure = fs.getNTFSVolume().getMFT().getRecord(MasterFileTable.SystemFiles.SECURE);
        Map<String, FSFile> streams = new NTFSFile(fs, secure).getStreams();
        assertThat(streams.keySet(), hasItem("$SDS"));

        // Act
        SecurityDescriptorStream sds =
            new SecurityDescriptorStream((NTFSFile.StreamFile) streams.get("$SDS"));
        List<SecurityDescriptorStreamEntry> entries = sds.getEntries();

        // Assert
        assertThat(entries, hasSize(18));

        // The first entry of each 256 KiB block records its own offset within the block
        assertThat(entries.get(0).getSecurityId(), is(256));
        assertThat(entries.get(0).getOffsetToEntry(), is(0));
        assertThat(entries.get(9).getSecurityId(), is(256));
        assertThat(entries.get(9).getOffsetToEntry(), is(0));

        for (SecurityDescriptorStreamEntry entry : entries) {
            assertThat("every entry should carry a security descriptor",
                entry.getSecurityDescriptor().getRevision(), is(1));
        }
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
