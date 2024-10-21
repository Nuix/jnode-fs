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
import org.jnode.fs.ntfs.testdata.CompressedData;
import org.jnode.fs.ntfs.testdata.UncompressedData;
import org.junit.Test;

// Test with data got from https://github.com/libyal/libfsntfs/blob/82181db7c9f272f98257cf3576243d9ccbbe8823/tests/fsntfs_test_compression.c
public class LibfsntfsTest {
    private  static String endOfNonZeroContent =
            "If the Library as you received it specifies that a proxy can decide\n" +
                    "whether future versions of the GNU Lesser General Public License shall\n" +
                    "apply, that proxy's public statement of acceptance of any version is\n" +
                    "permanent authorization for you to choose that version for the\n" +
                    "Library.";

    // the original jnode code, the data is copied from libfsntfs.
    // It works better than the version I showed to Luke :(
    @Test
    public void testDecompression_original() throws IOException {
        byte[] compressed = FileSystemTestUtils.intArrayToByteArray(CompressedData.fsntfs_test_compression_lznt1_compressed_data1);
        byte[] uncompressed = new byte[0x20000];

        byte[] expectedUncompressed = new byte[UncompressedData.fsntfs_test_compression_uncompressed_data1.length];
        for (int i = 0; i < UncompressedData.fsntfs_test_compression_uncompressed_data1.length; i++) {
            expectedUncompressed[i] = (byte) UncompressedData.fsntfs_test_compression_uncompressed_data1[i];
        }

        // Act
        CompressedDataRunOriginal.unCompressUnit(compressed, uncompressed);

        for (int i  = 0; i < expectedUncompressed.length; i++) {
            if (uncompressed[i] != expectedUncompressed[i])
            {
                int a = 0;
            }
            assertThat((uncompressed[i] == expectedUncompressed[i]), is(true));
        }

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        assertThat(uncompressedContent, containsString(endOfNonZeroContent)); // Near the end of the chunk
    }

    // the code shown to Luke before, the data is copied from libfsntfs. It failed on uncompressed index 4092.
    // So the version I showed to Luke does have some problem. But what's it???
    @Test
    public void testDecompression_luke() throws IOException {
        byte[] compressed = FileSystemTestUtils.intArrayToByteArray(CompressedData.fsntfs_test_compression_lznt1_compressed_data1);
        byte[] uncompressed = new byte[0x20000];

        byte[] expectedUncompressed = new byte[UncompressedData.fsntfs_test_compression_uncompressed_data1.length];
        for (int i = 0; i < UncompressedData.fsntfs_test_compression_uncompressed_data1.length; i++) {
            expectedUncompressed[i] = (byte) UncompressedData.fsntfs_test_compression_uncompressed_data1[i];
        }

        // Act
        CompressedDataRun2.unCompressUnit(compressed, uncompressed);

        for (int i  = 0; i < expectedUncompressed.length; i++) {
            if (uncompressed[i] != expectedUncompressed[i])
            {
                int a = 0;
            }
            System.out.println(i);
            assertThat((uncompressed[i] == expectedUncompressed[i]), is(true));
        }

        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        assertThat(uncompressedContent, containsString(endOfNonZeroContent)); // Near the end of the chunk
    }

    // the latest new jnode code, the data is copied from libfsntfs.
    @Test
    public void testDecompression_triage_4924() throws IOException {
        byte[] compressed = FileSystemTestUtils.intArrayToByteArray(CompressedData.fsntfs_test_compression_lznt1_compressed_data1);
        byte[] uncompressed = new byte[0x20000];

        // Act
        CompressedDataRun.unCompressUnit(compressed, uncompressed);

        for (int i = 0; i < UncompressedData.fsntfs_test_compression_uncompressed_data1.length; i++) {
            assertThat((uncompressed[i] == UncompressedData.fsntfs_test_compression_uncompressed_data1[i]), is(true));
        }
        // Assert
        String uncompressedContent = new String(uncompressed, StandardCharsets.US_ASCII);
        assertThat(uncompressedContent, containsString(endOfNonZeroContent)); // Near the end of the chunk
    }
}
