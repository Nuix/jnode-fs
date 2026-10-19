package org.jnode.fs.ntfs;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.fs.service.FileSystemService;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Reads compressed files whose compression units are stored raw and split across data runs.
 *
 * <p>A unit that does not compress is stored as-is in all 16 of its clusters, and on a fragmented volume those
 * clusters can come from several runs. The decoder has to add the fragments up: a unit whose fragments total exactly
 * the unit is full, so the next run starts a new unit, and a run longer than the unit that arrives while a unit is
 * open closes that unit with its leading clusters.</p>
 *
 * <p>The image is built for this test with mkntfs and an ntfs-3g mount in a container, so it carries no third party
 * licence; {@code scripts/build-split-compression-units.sh} is the recipe. The files are in {@code comp/}:</p>
 * <ul>
 *     <li>{@code split-last.bin}: one raw unit as 10 + 6 clusters and nothing after it.</li>
 *     <li>{@code split-then-compressed.bin}: the same, followed by a unit that compresses.</li>
 *     <li>{@code coalesced.bin}: a raw unit as 10 + 6 clusters where the 6 run straight into the next raw unit, so
 *     the two arrive as one 22 cluster run, followed by a unit that compresses.</li>
 * </ul>
 */
public class NTFSSplitCompressionUnitImageTest {

    private static final String IMAGE = "org/jnode/fs/ntfs/ntfs-split-compression-units.raw";

    @Test
    public void testRawUnitSplitAcrossTwoRuns() throws Exception {
        assertFile("split-last.bin", 65536, "4a3f24b7d254dec761e742ba5b09038b",
            "[[compressed-run vcn:0-15 [[data-run vcn:0-9 cluster:2560], [data-run vcn:10-15 cluster:2571]]]]");
    }

    @Test
    public void testRawUnitSplitAcrossTwoRunsThenACompressedUnit() throws Exception {
        assertFile("split-then-compressed.bin", 131072, "4034ee8d2b0f72750a57757e7c1dd6a9",
            "[[compressed-run vcn:0-15 [[data-run vcn:0-9 cluster:2629], [data-run vcn:10-15 cluster:2640]]], " +
            "[compressed-run vcn:16-31 [[data-run vcn:16-17 cluster:2647]]]]");
    }

    @Test
    public void testClosingFragmentCoalescedWithTheNextRawUnit() throws Exception {
        assertFile("coalesced.bin", 196608, "3e27443db00bebaf62faf3d957bcafcd",
            "[[compressed-run vcn:0-15 [[data-run vcn:0-9 cluster:2578], [data-run vcn:10-15 cluster:2589]]], " +
            "[data-run vcn:16-31 cluster:2595], " +
            "[compressed-run vcn:32-47 [[data-run vcn:32-33 cluster:2612]]]]");
    }

    private static void assertFile(String name, int size, String md5, String expectedRuns) throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage(IMAGE)) {
            FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
            NTFSFileSystem fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);
            FSDirectory comp = fs.getRootEntry().getDirectory().getEntry("comp").getDirectory();
            NTFSEntry entry = (NTFSEntry) comp.getEntry(name);

            NTFSNonResidentAttribute data = (NTFSNonResidentAttribute)
                entry.getFileRecord().findAttributeByType(NTFSAttribute.Types.DATA);
            assertThat("the file should be compressed", data.isCompressedAttribute(), is(true));
            assertThat(data.getDataRuns().toString(), is(expectedRuns));

            // Act
            assertThat(entry.getFile().getLength(), is((long) size));
            ByteBuffer buffer = ByteBuffer.allocate(size);
            entry.getFile().read(0, buffer);

            // Assert
            assertThat(md5(buffer.array()), is(md5));
        }
    }

    private static String md5(byte[] data) throws Exception {
        StringBuilder builder = new StringBuilder();
        for (byte b : MessageDigest.getInstance("MD5").digest(data)) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }
}
