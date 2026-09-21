package org.jnode.fs.ntfs;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FSFile;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.service.FileSystemService;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Reads volumes whose geometry is outside what the rest of the test corpus covers, where every image uses 512 byte
 * sectors and no more than 8 sectors per cluster.
 *
 * <p>The images are built for this test rather than sourced, so they carry no third party licence:</p>
 *
 * <pre>
 * truncate -s 32M ntfs-64k-clusters.raw
 * mkntfs -F -Q -s 512  -c 65536  -L GEOM ntfs-64k-clusters.raw
 * mkntfs -F -Q -s 512  -c 131072 -L GEOM ntfs-128k-clusters.raw   # 64M
 * mkntfs -F -Q -s 512  -c 2097152 -L GEOM ntfs-2m-clusters.raw    # 512M, the smallest mkntfs accepts here
 * mkntfs -F -Q -s 4096 -c 4096   -L GEOM ntfs-4kn-sectors.raw     # 16M
 * ntfscp &lt;image&gt; hello.txt hello.txt
 * </pre>
 *
 * <p>The two cluster sizes straddle the change of encoding at offset 0x0d of the volume header: up to 128 the byte
 * is the sector count itself, and above that it is stored as {@code 2^(256-n)}. A 128 KiB cluster is written as
 * 0xf8, which read as a plain byte gives 248 sectors and a nonsensical cluster size.</p>
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#4-the-volume-header">The volume header</a>
 */
public class NTFSVolumeGeometryTest {

    /** The content {@code ntfscp} put in hello.txt on each volume. */
    private static final String LINE = "jnode-fs geometry test\n";

    private static final int REPEATS = 64;

    /**
     * 64 KiB clusters: 128 sectors, the largest the plain form can hold.
     */
    @Test
    public void testSixtyFourKibibyteClusters() throws Exception {
        assertVolume("ntfs-64k-clusters", 512, 128, 65536);
    }

    /**
     * 128 KiB clusters: stored as 0xf8, i.e. 2^(256-248) = 256 sectors.
     */
    @Test
    public void testOneHundredAndTwentyEightKibibyteClusters() throws Exception {
        assertVolume("ntfs-128k-clusters", 512, 256, 131072);
    }

    /**
     * 2 MiB clusters, the largest NTFS supports: stored as 0xf4, i.e. 2^(256-244) = 4096 sectors.
     */
    @Test
    public void testTwoMebibyteClusters() throws Exception {
        assertVolume("ntfs-2m-clusters", 512, 4096, 2 * 1024 * 1024);
    }

    /**
     * A 4Kn volume, where the sector is 4096 bytes rather than 512.
     */
    @Test
    public void testFourKilobyteNativeSectors() throws Exception {
        assertVolume("ntfs-4kn-sectors", 4096, 1, 4096);
    }

    /**
     * Checks a volume's decoded geometry, and reads a file off it so the cluster size is exercised for real rather
     * than just parsed out of the header.
     *
     * @param image               the image name, without the .raw.gz suffix.
     * @param bytesPerSector      the expected sector size.
     * @param sectorsPerCluster   the expected number of sectors per cluster.
     * @param clusterSize         the expected cluster size in bytes.
     */
    private static void assertVolume(String image, int bytesPerSector, int sectorsPerCluster, int clusterSize)
        throws Exception {

        try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/" + image + ".raw")) {
            FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
            NTFSFileSystem fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);

            BootRecord boot = fs.getNTFSVolume().getBootRecord();
            assertThat(image + " bytes per sector", boot.getBytesPerSector(), is(bytesPerSector));
            assertThat(image + " sectors per cluster", boot.getSectorsPerCluster(), is(sectorsPerCluster));
            assertThat(image + " cluster size", boot.getClusterSize(), is(clusterSize));

            // Reading a file back exercises the cluster size rather than just the header decode
            FSFile hello = fs.getRootEntry().getDirectory().getEntry("hello.txt").getFile();
            byte[] expected = repeat(LINE, REPEATS);
            assertThat(image + " file length", hello.getLength(), is((long) expected.length));

            ByteBuffer buffer = ByteBuffer.allocate(expected.length);
            hello.read(0, buffer);

            assertThat(image + " file content", buffer.array(), is(expected));
        }
    }

    private static byte[] repeat(String text, int times) {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < times; i++) {
            builder.append(text);
        }

        return builder.toString().getBytes(StandardCharsets.US_ASCII);
    }
}
