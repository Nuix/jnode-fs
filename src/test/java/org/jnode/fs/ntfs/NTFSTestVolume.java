package org.jnode.fs.ntfs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jnode.driver.block.ByteArrayDevice;
import org.jnode.util.LittleEndian;

/**
 * Builds an NTFS volume over an in-memory device, for tests that need to read clusters back without an image.
 */
public final class NTFSTestVolume {

    /**
     * The sector size of the volumes built here.
     */
    public static final int BYTES_PER_SECTOR = 512;

    private NTFSTestVolume() {
    }

    /**
     * Builds a volume backed by the given array.
     *
     * <p>Only the volume header is filled in, so the volume can read clusters but has no usable MFT. The first
     * cluster holds the header, so data runs under test have to live beyond it.</p>
     *
     * @param clusterSize the cluster size in bytes, a multiple of {@link #BYTES_PER_SECTOR}.
     * @param data        the device contents; the first sector is overwritten with the volume header.
     * @return the volume.
     * @throws IOException if the header does not describe a usable volume.
     */
    public static NTFSVolume volume(int clusterSize, byte[] data) throws IOException {
        System.arraycopy("NTFS    ".getBytes(StandardCharsets.US_ASCII), 0, data, 0x03, 8);
        LittleEndian.setInt16(data, 0x0b, BYTES_PER_SECTOR);
        data[0x0d] = (byte) (clusterSize / BYTES_PER_SECTOR);
        data[0x15] = (byte) 0xf8;
        LittleEndian.setInt16(data, 0x18, 0x3f);
        LittleEndian.setInt64(data, 0x28, data.length / BYTES_PER_SECTOR);
        LittleEndian.setInt64(data, 0x30, 1);       // MFT at cluster 1, which nothing here reads
        LittleEndian.setInt64(data, 0x38, 1);
        data[0x40] = (byte) -10;                    // 1 KiB file records
        data[0x44] = (byte) 1;
        data[510] = (byte) 0x55;
        data[511] = (byte) 0xaa;

        return new NTFSVolume(new ByteArrayDevice(data));
    }

    /**
     * Fills a range of clusters with a value that identifies the cluster it came from.
     *
     * @param data        the device contents.
     * @param clusterSize the cluster size in bytes.
     * @param cluster     the first cluster to fill.
     * @param count       the number of clusters to fill.
     */
    public static void fillClusters(byte[] data, int clusterSize, int cluster, int count) {
        for (int i = 0; i < count * clusterSize; i++) {
            data[cluster * clusterSize + i] = (byte) (cluster + i);
        }
    }
}
