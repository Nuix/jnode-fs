package org.jnode.fs.ntfs;

import java.io.IOException;
import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.index.IndexEntry;
import org.jnode.fs.service.FileSystemService;
import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Tests for {@link NTFSEntry}.
 */
public class NTFSEntryTest {

    private static final String IMAGE = "org/jnode/fs/ntfs/ntfs-split-compression-units.raw";

    /**
     * $FILE_NAME on some reserved records lacks the directory flag, so the MFT record is read for those. When that
     * read fails too, the known reserved directories are still reported as directories; reporting $Extend as a file
     * would make a recursive walk skip everything under it.
     */
    @Test
    public void testReservedDirectoryIsStillADirectoryWhenItsRecordCannotBeRead() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage(IMAGE)) {
            FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
            NTFSFileSystem fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);
            IndexEntry original = ((NTFSEntry) fs.getRootEntry().getDirectory().getEntry("$Extend")).getIndexEntry();
            assertThat(original.getFileReferenceNumber(), is((long) MasterFileTable.SystemFiles.EXTEND));

            // Arrange: a copy of the index entry with the directory flag cleared from its $FILE_NAME
            byte[] bytes = new byte[original.getSize()];
            original.getData(0, bytes, 0, bytes.length);
            int flagsOffset = IndexEntry.CONTENT_OFFSET + 0x38;
            LittleEndian.setInt32(bytes, flagsOffset, LittleEndian.getInt32(bytes, flagsOffset) & ~0x10000000);
            IndexEntry patched = new IndexEntry(original.getParentFileRecord(), bytes, 0);
            assertThat("the flag should be clear in the copy",
                new FileNameAttribute.Structure(patched, IndexEntry.CONTENT_OFFSET).isDirectory(), is(false));

            NTFSEntry unreadable = new NTFSEntry(fs, patched) {
                @Override
                public FileRecord getFileRecord() throws IOException {
                    throw new IOException("simulated read failure");
                }
            };

            // Act + assert
            assertThat(unreadable.isDirectory(), is(true));
            assertThat(unreadable.isFile(), is(false));
        }
    }
}
