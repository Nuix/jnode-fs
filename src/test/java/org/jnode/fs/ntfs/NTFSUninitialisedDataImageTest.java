package org.jnode.fs.ntfs;

import java.io.File;
import java.nio.ByteBuffer;
import java.security.MessageDigest;

import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSFile;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.fs.service.FileSystemService;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Reads back a file whose clusters are allocated but whose valid data length is zero, from a real volume.
 *
 * <p>{@code ntfs-valid-data-length.raw} is a 16 MiB volume built for this test:</p>
 *
 * <pre>
 * truncate -s 16M ntfs-valid-data-length.raw
 * mkntfs -F -Q -c 4096 -L VDLTEST ntfs-valid-data-length.raw
 * ntfscp ntfs-valid-data-length.raw stale.bin  stale.bin     # 64 KiB of 0xAB
 * ntfscp ntfs-valid-data-length.raw normal.bin normal.bin    # 4 KiB of 0xCD
 * </pre>
 *
 * <p>The valid data length of {@code stale.bin} was then set to zero in its $DATA attribute, which is the state
 * NTFS leaves a file in when clusters have been allocated to it but never written. The clusters still hold the
 * 0xAB bytes, and a correct reader has to return zeros rather than handing them back. {@code icat} from The Sleuth
 * Kit reads the file as 64 KiB of zeros, and {@code normal.bin} is there to show ordinary reads are unaffected.</p>
 *
 * @see NTFSUninitialisedDataTest for the same behaviour covered synthetically
 */
public class NTFSUninitialisedDataImageTest {

    private static final String IMAGE = "org/jnode/fs/ntfs/ntfs-valid-data-length.raw";

    /** 64 KiB of 0xAB on disk, with a valid data length of zero. */
    private static final String STALE_FILE = "stale.bin";

    private static final int STALE_SIZE = 65536;

    /** The digest of 65536 zero bytes, which is what icat and ntfs3 return for this file. */
    private static final String ZEROS_MD5 = "fcd6bcb56c1689fcef28b57c22475bad";

    /** The digest of the 0xAB bytes that are actually on the disk. */
    private static final String STALE_MD5 = "b6936734ef093dabc4e17f0c29fa4718";

    /** 4 KiB of 0xCD, fully initialised. */
    private static final String NORMAL_FILE = "normal.bin";

    private static final int NORMAL_SIZE = 4096;

    private static final String NORMAL_MD5 = "5fed275e7617a806f94c173746a2a723";

    @Test
    public void testFileWithNoValidDataReadsAsZeros() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage(IMAGE)) {
            NTFSFileSystem fs = fileSystem(device);
            FSDirectory root = fs.getRootEntry().getDirectory();

            FSFile stale = root.getEntry(STALE_FILE).getFile();
            assertThat(stale.getLength(), is((long) STALE_SIZE));

            // Act
            ByteBuffer buffer = ByteBuffer.allocate(STALE_SIZE);
            stale.read(0, buffer);

            // Assert
            assertThat(md5(buffer.array()), is(ZEROS_MD5));
        }
    }

    /**
     * Guards the test above against passing for the wrong reason: the clusters behind the file have to be holding
     * something other than zeros, otherwise it would pass on any reader at all. Reading the attribute without
     * limiting to the valid data length shows what is really down there.
     */
    @Test
    public void testTheClustersBehindTheFileAreNotAlreadyZeros() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage(IMAGE)) {
            NTFSFileSystem fs = fileSystem(device);
            NTFSEntry entry = (NTFSEntry) fs.getRootEntry().getDirectory().getEntry(STALE_FILE);
            FileRecord record = entry.getFileRecord();

            NTFSNonResidentAttribute data =
                (NTFSNonResidentAttribute) record.findAttributeByType(NTFSAttribute.Types.DATA);
            assertThat("the file should have no initialised data",
                data.getAttributeInitializedSize(), is(0L));
            assertThat(data.getAttributeActualSize(), is((long) STALE_SIZE));

            // Act: read the same attribute without limiting to the valid data length
            byte[] raw = new byte[STALE_SIZE];
            record.readData(NTFSAttribute.Types.DATA, null, 0, raw, 0, raw.length, false);

            // Assert
            assertThat(md5(raw), is(STALE_MD5));
        }
    }

    /**
     * A fully initialised file on the same volume is returned as-is.
     */
    @Test
    public void testFullyInitialisedFileIsUnaffected() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage(IMAGE)) {
            NTFSFileSystem fs = fileSystem(device);
            FSFile normal = fs.getRootEntry().getDirectory().getEntry(NORMAL_FILE).getFile();
            assertThat(normal.getLength(), is((long) NORMAL_SIZE));

            // Act
            ByteBuffer buffer = ByteBuffer.allocate(NORMAL_SIZE);
            normal.read(0, buffer);

            // Assert
            assertThat(md5(buffer.array()), is(NORMAL_MD5));
        }
    }

    private static NTFSFileSystem fileSystem(TestImageDevice device) throws Exception {
        FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
        return fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);
    }

    private static String md5(byte[] data) throws Exception {
        StringBuilder builder = new StringBuilder();

        for (byte b : MessageDigest.getInstance("MD5").digest(data)) {
            builder.append(String.format("%02x", b));
        }

        return builder.toString();
    }
}
