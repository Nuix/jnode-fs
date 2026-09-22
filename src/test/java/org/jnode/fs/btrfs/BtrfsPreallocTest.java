package org.jnode.fs.btrfs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Preallocated (fallocate) extents must read as zeros, not the stale data still in the
 * allocated-but-never-written blocks on disk. A real prealloc fixture needs a privileged mount, so
 * this drives {@link BtrfsFileContent#readFromExtents} directly with synthetic extents — which also
 * lets it prove the prealloc path never touches the device (passing a null volume/reader).
 */
public class BtrfsPreallocTest {

    private static BtrfsFileContent.Extent prealloc(long fileOffset, long numBytes) {
        // a prealloc extent has an allocated disk range (diskBytenr set) but no valid data
        return new BtrfsFileContent.Extent(fileOffset, numBytes, numBytes,
                BtrfsConstants.EXTENT_TYPE_PREALLOC, BtrfsConstants.COMPRESS_NONE,
                0x9999, numBytes, 0, null);
    }

    @Test
    public void preallocExtentReadsAsZerosWithoutTouchingTheDevice() throws Exception {
        List<BtrfsFileContent.Extent> extents = new ArrayList<BtrfsFileContent.Extent>();
        extents.add(prealloc(0, 100));

        byte[] dst = new byte[100];
        Arrays.fill(dst, (byte) 0xAA); // pre-dirty so a missed zero-fill would show

        // null volume: if the prealloc path tried to read the disk (chunkMap/reader) this would NPE
        int n = BtrfsFileContent.readFromExtents(null, extents, 100, 0, dst, 0, 100);

        assertEquals(100, n);
        assertArrayEquals(new byte[100], dst);
    }

    @Test
    public void writtenDataAroundAPreallocHoleStillReadsZerosForTheHole() throws Exception {
        // file = [0..50) prealloc (zeros), [50..100) prealloc (zeros): two prealloc extents, all zero
        List<BtrfsFileContent.Extent> extents = new ArrayList<BtrfsFileContent.Extent>();
        extents.add(prealloc(0, 50));
        extents.add(prealloc(50, 50));

        byte[] dst = new byte[100];
        Arrays.fill(dst, (byte) 0x7F);

        int n = BtrfsFileContent.readFromExtents(null, extents, 100, 0, dst, 0, 100);

        assertEquals(100, n);
        assertArrayEquals(new byte[100], dst);
    }
}
