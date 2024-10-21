package org.jnode.fs.ntfs;

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

/**
 * Tests for {@link CompressedDataRun}.
 */
public class NTFSCompressedDataRunTriage4924Test {

    // the original jnode code, the data extract from E01
    // throw arraycopy: source index -74 out of bounds for byte[131072] as expected.
    @Test
    public void testDecompression_original() throws IOException {
        // Arrange
        byte[] data = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/compressed-E01.txt");

        //the actual data starts from 787.
        byte[] compressed = new byte[data.length - 787];
        System.arraycopy(data, 787, compressed, 0, data.length - 787);
        byte[] uncompressed = new byte[0x20000];

        // Act
        try {
            CompressedDataRunOriginal.unCompressUnit(compressed, uncompressed);
        } catch (Exception e) {
            assertThat(e instanceof ArrayIndexOutOfBoundsException, is(true));
            return;
        }

        // should not go here
        assertThat(true, is(false));
    }

    // the latest jnode code, the data extract from E01
    @Test
    public void testDecompression() throws IOException {
        // Arrange
        byte[] data = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/compressed-E01.txt");

        //the actual data starts from 787.
        byte[] compressed = new byte[data.length - 787];
        System.arraycopy(data, 787, compressed, 0, data.length - 787);
        byte[] uncompressed = new byte[0x20000];

        // Act
        CompressedDataRun.unCompressUnit(compressed, uncompressed);

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        assertThat(uncompressedContent, containsString("PublicKey=002400000480000094000000060200000024000052534131000400000100010007d1fa57c4aed9f0a32e84aa0faefd0de9e8fd6aec8f87fb03766c834c99921eb23be79ad9d5dcc1dd9ad236132102900b723cf980957fc4e177108fc607774f29e8320e92ea05ece4e821c0a5efe8f1645c4c0c93c1ab99285d622caa652c1dfad63d745d6f2de5f17e5eaf0fc4963d261c8a12436518206dc093344d5ad293")); // Near the end of the chunk
    }

    // the code showed to Luke, the data extract from E01
    @Test
    public void testDecompression_triage_4924_luke() throws IOException {
        // Arrange
        byte[] data = FileSystemTestUtils.readFileToByteArray("org/jnode/fs/ntfs/compressed-E01.txt");
        //the actual data starts from 787.
        byte[] compressed = new byte[data.length - 787];
        System.arraycopy(data, 787, compressed, 0, data.length - 787);
        byte[] uncompressed = new byte[0x20000];

        // Act
        CompressedDataRun2.unCompressUnit(compressed, uncompressed);

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        assertThat(uncompressedContent, containsString("PublicKey=002400000480000094000000060200000024000052534131000400000100010007d1fa57c4aed9f0a32e84aa0faefd0de9e8fd6aec8f87fb03766c834c99921eb23be79ad9d5dcc1dd9ad236132102900b723cf980957fc4e177108fc607774f29e8320e92ea05ece4e821c0a5efe8f1645c4c0c93c1ab99285d622caa652c1dfad63d745d6f2de5f17e5eaf0fc4963d261c8a12436518206dc093344d5ad293")); // Near the end of the chunk
    }
}
