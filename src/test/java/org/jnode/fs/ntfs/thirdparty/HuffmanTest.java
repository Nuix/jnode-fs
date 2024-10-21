package org.jnode.fs.ntfs.thirdparty;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.datarun.CompressedDataRun;
import org.jnode.fs.ntfs.testdata.HuffmanCompressed;
import org.jnode.fs.ntfs.testdata.UncompressedData;
import org.junit.Test;

public class HuffmanTest {

    // Test if huffman is supported.
    // It is not.
    @Test
    public void testDecompression_huffman() throws IOException {
        byte[] compressed = FileSystemTestUtils.intArrayToByteArray(HuffmanCompressed.fsntfs_test_compression_lzxpress_huffman_compressed_data1);
        byte[] uncompressed = new byte[0x20000];

        // Act
        CompressedDataRun.unCompressUnit(compressed, uncompressed);

        for (int i = 0; i < UncompressedData.fsntfs_test_compression_uncompressed_data1.length; i++) {
            if(uncompressed[i] != UncompressedData.fsntfs_test_compression_uncompressed_data1[i]) {
                int a = 0;
            }
            assertThat((uncompressed[i] == UncompressedData.fsntfs_test_compression_uncompressed_data1[i]), is(true));
        }
        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        int a = 0;
    }
}
