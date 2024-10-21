package org.jnode.fs.ntfs.thirdparty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.datarun.CompressedDataRun;
import org.jnode.fs.ntfs.datarun.CompressedDataRun2;
import org.jnode.fs.ntfs.datarun.CompressedDataRunOriginal;
import org.junit.Test;

// Tests with data got from https://github.com/MagnetForensics/rust-lzxpress/tree/main/tests
// I wonder if it works.
// I even run C code https://github.com/MagnetForensics/rust-lzxpress/blob/main/tests/lznt1.c in VS directly. It fails either..
public class ManetForensicTest {
    // the original jnode code, the data is https://github.com/MagnetForensics/rust-lzxpress/tree/main/tests
    // It doesn't work.
    @Test
    public void testDecompression_original() throws IOException {
        // Arrange
        byte[] compressed = FileSystemTestUtils.readFileToByteArray(
                "/Users/tli01/work/nuix/jnode-fs/src/test/resources/org/jnode/fs/ntfs/block1.compressed.bin");
        byte[] expectedUncompressed = FileSystemTestUtils.readFileToByteArray(
                "/Users/tli01/work/nuix/jnode-fs/src/test/resources/org/jnode/fs/ntfs/block1.uncompressed.bin");

        byte[] uncompressed = new byte[1000000];
// Act
        CompressedDataRunOriginal.unCompressUnit(compressed, uncompressed);

        for (int i  = 0; i < expectedUncompressed.length; i++) {
            assertThat((uncompressed[i] == expectedUncompressed[i]), is(true));
        }

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        assertThat(uncompressedContent, containsString("PublicKey=002400000480000094000000060200000024000052534131000400000100010007d1fa57c4aed9f0a32e84aa0faefd0de9e8fd6aec8f87fb03766c834c99921eb23be79ad9d5dcc1dd9ad236132102900b723cf980957fc4e177108fc607774f29e8320e92ea05ece4e821c0a5efe8f1645c4c0c93c1ab99285d622caa652c1dfad63d745d6f2de5f17e5eaf0fc4963d261c8a12436518206dc093344d5ad293")); // Near the end of the chunk
    }

    // the code showed to Luke, the data is https://github.com/MagnetForensics/rust-lzxpress/tree/main/tests
    // It doesn't work.
    @Test
    public void testDecompression_luke() throws IOException {
        // Arrange
        byte[] compressed = FileSystemTestUtils.readFileToByteArray(
                "/Users/tli01/work/nuix/jnode-fs/src/test/resources/org/jnode/fs/ntfs/block1.compressed.bin");
        byte[] expectedUncompressed = FileSystemTestUtils.readFileToByteArray(
                "/Users/tli01/work/nuix/jnode-fs/src/test/resources/org/jnode/fs/ntfs/block1.uncompressed.bin");

        byte[] uncompressed = new byte[1000000];
// Act
        CompressedDataRun2.unCompressUnit(compressed, uncompressed);

        for (int i  = 0; i < expectedUncompressed.length; i++) {
            assertThat((uncompressed[i] == expectedUncompressed[i]), is(true));
        }

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        assertThat(uncompressedContent, containsString("PublicKey=002400000480000094000000060200000024000052534131000400000100010007d1fa57c4aed9f0a32e84aa0faefd0de9e8fd6aec8f87fb03766c834c99921eb23be79ad9d5dcc1dd9ad236132102900b723cf980957fc4e177108fc607774f29e8320e92ea05ece4e821c0a5efe8f1645c4c0c93c1ab99285d622caa652c1dfad63d745d6f2de5f17e5eaf0fc4963d261c8a12436518206dc093344d5ad293")); // Near the end of the chunk
    }

    // the latest jnode code, the data is https://github.com/MagnetForensics/rust-lzxpress/tree/main/tests
    // It doesn't work. Don't run it, there is an infinite loop..
//    @Test
//    public void testDecompression() throws IOException {
//        // Arrange
//        byte[] compressed = FileSystemTestUtils.readFileToByteArray(
//                "/Users/tli01/work/nuix/jnode-fs/src/test/resources/org/jnode/fs/ntfs/block1.compressed.bin");
//        byte[] expectedUncompressed = FileSystemTestUtils.readFileToByteArray(
//                "/Users/tli01/work/nuix/jnode-fs/src/test/resources/org/jnode/fs/ntfs/block1.uncompressed.bin");
//
//        byte[] uncompressed = new byte[1000000];
//// Act
//        CompressedDataRun.unCompressUnit(compressed, uncompressed);
//
//        for (int i  = 0; i < expectedUncompressed.length; i++) {
//            assertThat((uncompressed[i] == expectedUncompressed[i]), is(true));
//        }
//
//        // Assert
//        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
//        assertThat(uncompressedContent, containsString("PublicKey=002400000480000094000000060200000024000052534131000400000100010007d1fa57c4aed9f0a32e84aa0faefd0de9e8fd6aec8f87fb03766c834c99921eb23be79ad9d5dcc1dd9ad236132102900b723cf980957fc4e177108fc607774f29e8320e92ea05ece4e821c0a5efe8f1645c4c0c93c1ab99285d622caa652c1dfad63d745d6f2de5f17e5eaf0fc4963d261c8a12436518206dc093344d5ad293")); // Near the end of the chunk
//    }
}
