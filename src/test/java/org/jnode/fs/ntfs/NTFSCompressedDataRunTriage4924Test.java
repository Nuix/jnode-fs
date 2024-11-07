package org.jnode.fs.ntfs;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.datarun.CompressedDataRun;
import org.jnode.fs.ntfs.testdata.CompressedTestData;
import org.jnode.fs.ntfs.testdata.UncompressedTestData;
import org.junit.Test;

/**
 * Tests for {@link CompressedDataRun} for more edge cases.
 */
public class NTFSCompressedDataRunTriage4924Test {

    @Test
    public void testDecompression_read_chunk_missing_previous_chunk() throws IOException {
        // Arrange
        byte[] data = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/triage-4924-E01.compressed.bin");

        // Deliberately skip the previous chunk.
        // the new chunk starts from 787.
        byte[] compressed = new byte[data.length - 787];
        System.arraycopy(data, 787, compressed, 0, data.length - 787);
        byte[] uncompressed = new byte[0x20000];

        //the offset goes to a position before the first bit of the chunk (so it goes to the chunk before the current one)
        new CompressedDataRun(null, 16).decompressUnit(compressed, uncompressed);

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        // still get something.
        assertThat(uncompressedContent.contains(
                "PublicKey=002400000480000094000000060200000024000052534131000400000100010007d1fa57c4aed9f0a32e84aa0faefd0de9e8fd6aec8f87fb03766c834c99921eb2" +
                        "3be79ad9d5dcc1dd9ad236132102900b723cf980957fc4e177108fc607774f29e8320e92ea05ece4e821c0a5efe8f1645c4c0c93c1ab99285d622caa652c1dfad63d" +
                        "745d6f2de5f17e5eaf0fc4963d261c8a12436518206dc093344d5ad293"), is(true));
    }

    @Test
    public void testDecompression_E01() throws IOException {
        // Arrange
        byte[] compressed = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/triage-4924-E01.compressed.bin");

        byte[] uncompressed = new byte[0x200000];

        // Act
        new CompressedDataRun(null, 16).decompressUnit(compressed, uncompressed);

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        assertThat(uncompressedContent, containsString(
                "PublicKey=002400000480000094000000060200000024000052534131000400000100010007d1fa57c4aed9f0a32e84aa0faefd0de9e8fd6aec8f87fb03766c834c99921eb2" +
                        "3be79ad9d5dcc1dd9ad236132102900b723cf980957fc4e177108fc607774f29e8320e92ea05ece4e821c0a5efe8f1645c4c0c93c1ab99285d622caa652c1dfad63d" +
                        "745d6f2de5f17e5eaf0fc4963d261c8a12436518206dc093344d5ad293")); // Near the end of the chunk
    }

    @Test
    public void testDecompression_E01_offsetBeforeStart() throws IOException {
        // Arrange
        byte[] compressed = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/offset-before-start.compressed.bin");

        byte[] uncompressed = new byte[0x20000];

        // Act
        // there is an error, but we ignore it for now to get more meaningful data out.
        new CompressedDataRun(null, 16).decompressUnit(compressed, uncompressed);

        // Assert
        byte[] expectedByteArray = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/offset-before-start.decompressed.bin");

        for (int i = 0; i < expectedByteArray.length; i++) {
            assertThat(uncompressed[0x2000 + i] == expectedByteArray[i], is(true));
        }
    }

    /**
     * Tests with test data generated using <a href="https://github.com/libyal/libfsntfs/blob/82181db7c9f272f98257cf3576243d9ccbbe8823/tests/fsntfs_test_compression.c">libfsntfs test data</a>
     *
     * @throws IOException if any error occurs.
     */
    @Test
    public void testDecompression_libfsntfs_data() throws IOException {
        byte[] compressed = FileSystemTestUtils.intArrayToByteArray(CompressedTestData.fsntfs_test_compression_lznt1_compressed_data1);
        byte[] uncompressed = new byte[0x20000];

        byte[] expectedUncompressed = new byte[UncompressedTestData.fsntfs_test_compression_uncompressed_data1.length];
        for (int i = 0; i < UncompressedTestData.fsntfs_test_compression_uncompressed_data1.length; i++) {
            expectedUncompressed[i] = (byte) UncompressedTestData.fsntfs_test_compression_uncompressed_data1[i];
        }

        // Act
        new CompressedDataRun(null, 16).decompressUnit(compressed, uncompressed);

        // verify it bit by bit.
        for (int i = 0; i < expectedUncompressed.length; i++) {
            if ((uncompressed[i] != expectedUncompressed[i])) {
                int a = 0;
            }
            assertThat((uncompressed[i] == expectedUncompressed[i]), is(true));
        }

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        String endOfNonZeroContent =
                "If the Library as you received it specifies that a proxy can decide\n" +
                        "whether future versions of the GNU Lesser General Public License shall\n" +
                        "apply, that proxy's public statement of acceptance of any version is\n" +
                        "permanent authorization for you to choose that version for the\n" +
                        "Library.";
        assertThat(uncompressedContent, containsString(endOfNonZeroContent)); // Near the end of the chunk
    }

    /**
     * Tests with test data generated using <a href="https://github.com/you0708/lznt1">Python lznt1</a>
     * It is to run "ntfs/py-lznt1/decompressor.py. The content is "THE TRAGEDY OF HAMLET.txt".
     *
     * @throws IOException if any error occurs.
     */
    @Test
    public void testDecompression_py_lznt1_data() throws IOException {
        byte[] compressed = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/py-lznt1/compressed.bin");

        byte[] expectedUncompressed = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/py-lznt1/decompressed.bin");

        byte[] uncompressed = new byte[expectedUncompressed.length];

        // Act
        new CompressedDataRun(null, 16).decompressUnit(compressed, uncompressed);

        for (int i = 0; i < expectedUncompressed.length; i++) {
            if (uncompressed[i] != expectedUncompressed[i]) {
                int a = 0; // for debugging.
            }
            assertThat((uncompressed[i] == expectedUncompressed[i]), is(true));
        }

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        String endOfNonZeroContent =
                "Go, bid the soldiers shoot.\r\n" +
                        "            Exeunt marching; after the which a peal of ordnance\r\n" +
                        "                                                   are shot off.  \r\n" +
                        "\r\n" +
                        "\r\n" +
                        "THE END";
        assertThat(uncompressedContent, containsString(endOfNonZeroContent)); // Near the end of the chunk
    }

    /**
     * Tests with test data got from
     * <a href="https://learn.microsoft.com/en-us/openspecs/windows_protocols/ms-xca/94164d22-2928-4417-876e-d193766c4db6">Microsoft LZNT1 test data</a>
     *
     * @throws IOException if any error occurs.
     */
    @Test
    public void testDecompression_chars() throws IOException {
        // Arrange
        byte[] compressed = FileSystemTestUtils.intArrayToByteArray(new int[]{
                0x38, 0xb0, 0x88, 0x46, 0x23, 0x20, 0x00, 0x20,
                0x47, 0x20, 0x41, 0x00, 0x10, 0xa2, 0x47, 0x01,
                0xa0, 0x45, 0x20, 0x44, 0x00, 0x08, 0x45, 0x01,
                0x50, 0x79, 0x00, 0xc0, 0x45, 0x20, 0x05, 0x24,
                0x13, 0x88, 0x05, 0xb4, 0x02, 0x4a, 0x44, 0xef,
                0x03, 0x58, 0x02, 0x8c, 0x09, 0x16, 0x01, 0x48,
                0x45, 0x00, 0xbe, 0x00, 0x9e, 0x00, 0x04, 0x01,
                0x18, 0x90, 0x00
        });

        byte[] expectedUncompressed = "F# F# G A A G F# E D D E F# F# E E F# F# G A A G F# E D D E F# E D D E E F# D E F# G F# D E F# G F# E D E A F# F# G A A G F# E D D E F# E D D"
                .getBytes(StandardCharsets.UTF_8);

        byte[] uncompressed = new byte[expectedUncompressed.length];
        //Act
        new CompressedDataRun(null, 16).decompressUnit(compressed, uncompressed);

        for (int i = 0; i < uncompressed.length; i++) {
            assertThat((uncompressed[i] == expectedUncompressed[i]), is(true));
        }
    }
}
