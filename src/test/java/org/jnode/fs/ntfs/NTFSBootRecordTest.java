package org.jnode.fs.ntfs;

import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests for {@link BootRecord}.
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#4-the-volume-header">The volume header</a>
 */
public class NTFSBootRecordTest {

    /**
     * Builds a minimal NTFS boot sector.
     *
     * @param bytesPerSector       the value for offset 0x0b.
     * @param sectorsPerCluster    the raw byte at offset 0x0d.
     * @param mftRecordSize        the raw signed byte at offset 0x40.
     * @param indexRecordSize      the raw signed byte at offset 0x44.
     * @return the boot sector.
     */
    private static byte[] bootSector(int bytesPerSector, int sectorsPerCluster, int mftRecordSize,
                                     int indexRecordSize) {
        byte[] buffer = new byte[512];
        System.arraycopy("NTFS    ".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, buffer, 0x03, 8);
        LittleEndian.setInt16(buffer, 0x0b, bytesPerSector);
        buffer[0x0d] = (byte) sectorsPerCluster;
        buffer[0x15] = (byte) 0xf8;
        LittleEndian.setInt16(buffer, 0x18, 0x3f);
        LittleEndian.setInt64(buffer, 0x28, 0x100000);
        LittleEndian.setInt64(buffer, 0x30, 4);
        LittleEndian.setInt64(buffer, 0x38, 8);
        buffer[0x40] = (byte) mftRecordSize;
        buffer[0x44] = (byte) indexRecordSize;
        buffer[510] = (byte) 0x55;
        buffer[511] = (byte) 0xaa;
        return buffer;
    }

    @Test
    public void testCommonClusterSizes() {
        // 512 byte clusters, as used by test.ntfs and ntfs1-gen2
        assertThat(new BootRecord(bootSector(512, 1, -10, 1)).getClusterSize(), is(512));

        // 1 KiB clusters, as used by compressed.dd
        assertThat(new BootRecord(bootSector(512, 2, -10, 4)).getClusterSize(), is(1024));

        // 4 KiB clusters, as used by complex-compression.dd
        assertThat(new BootRecord(bootSector(512, 8, -10, 1)).getClusterSize(), is(4096));

        // 4 KiB clusters on a 4Kn volume, as used by ntfs-4k-sector-size
        BootRecord fourKn = new BootRecord(bootSector(4096, 1, -10, 1));
        assertThat(fourKn.getBytesPerSector(), is(4096));
        assertThat(fourKn.getClusterSize(), is(4096));

        // 64 KiB clusters, the largest expressible without the 2^(256-n) form
        assertThat(new BootRecord(bootSector(512, 128, -10, 1)).getClusterSize(), is(65536));
    }

    /**
     * Sectors per cluster values of 244 to 255 encode {@code 2^(256-n)} sectors. Windows 10 (1903) and mkntfs use
     * this for the 128 KiB to 2 MiB cluster sizes; read as a plain byte the cluster size comes out nonsensical.
     */
    @Test
    public void testLargeClusterSizesUseThePowerOfTwoEncoding() {
        // 0xf8 => 2^(256-248) = 2^8 = 256 sectors => 128 KiB
        BootRecord oneTwentyEightK = new BootRecord(bootSector(512, 0xf8, -10, 1));
        assertThat(oneTwentyEightK.getSectorsPerCluster(), is(256));
        assertThat(oneTwentyEightK.getClusterSize(), is(128 * 1024));

        // 0xf6 => 2^(256-246) = 2^10 = 1024 sectors => 512 KiB
        BootRecord fiveTwelveK = new BootRecord(bootSector(512, 0xf6, -10, 1));
        assertThat(fiveTwelveK.getSectorsPerCluster(), is(1024));
        assertThat(fiveTwelveK.getClusterSize(), is(512 * 1024));

        // 0xf4 => 2^(256-244) = 2^12 = 4096 sectors => 2 MiB
        BootRecord twoM = new BootRecord(bootSector(512, 0xf4, -10, 1));
        assertThat(twoM.getSectorsPerCluster(), is(4096));
        assertThat(twoM.getClusterSize(), is(2 * 1024 * 1024));
    }

    /**
     * The MFT and index record size bytes use the same convention: 0 to 127 are a count of cluster blocks, and a
     * negative value is {@code 2^(-n)} bytes.
     */
    @Test
    public void testRecordSizes() {
        // -10 => 2^10 => the usual 1024 byte MFT record, regardless of the cluster size
        assertThat(new BootRecord(bootSector(512, 8, -10, 1)).getFileRecordSize(), is(1024));
        assertThat(new BootRecord(bootSector(512, 1, -10, 1)).getFileRecordSize(), is(1024));

        // A positive value is a count of clusters
        assertThat(new BootRecord(bootSector(512, 8, 1, 1)).getFileRecordSize(), is(4096));
        assertThat(new BootRecord(bootSector(512, 2, 4, 4)).getIndexRecordSize(), is(4096));

        // Zero means zero cluster blocks, not 2^0
        assertThat(new BootRecord(bootSector(512, 8, 0, 0)).getFileRecordSize(), is(0));
        assertThat(new BootRecord(bootSector(512, 8, 0, 0)).getIndexRecordSize(), is(0));
    }
}
