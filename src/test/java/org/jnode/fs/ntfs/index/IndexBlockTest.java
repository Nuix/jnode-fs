package org.jnode.fs.ntfs.index;

import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests for {@link IndexBlock}.
 */
public class IndexBlockTest {

    private static final int BLOCK_SIZE = 4096;
    private static final int SECTOR_SIZE = 512;
    private static final int USN = 0x0102;

    /**
     * Builds an index block with valid fix-up values.
     *
     * @param vcn the value for offset 0x10.
     * @return the index block data.
     */
    private static byte[] indexBlock(long vcn) {
        byte[] buffer = new byte[BLOCK_SIZE];
        System.arraycopy("INDX".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, buffer, 0, 4);

        int fixUpOffset = 0x28;
        int fixUpCount = BLOCK_SIZE / SECTOR_SIZE + 1;
        LittleEndian.setInt16(buffer, 0x04, fixUpOffset);
        LittleEndian.setInt16(buffer, 0x06, fixUpCount);
        LittleEndian.setInt64(buffer, 0x10, vcn);

        // The placeholder, followed by the original value of the last two bytes of each sector
        LittleEndian.setInt16(buffer, fixUpOffset, USN);
        for (int i = 1; i < fixUpCount; i++) {
            LittleEndian.setInt16(buffer, fixUpOffset + i * 2, 0);
            LittleEndian.setInt16(buffer, i * SECTOR_SIZE - 2, USN);
        }

        // Index node header: entries start immediately after it
        LittleEndian.setInt32(buffer, 0x18, 0x10);
        LittleEndian.setInt32(buffer, 0x1c, 0x10);
        LittleEndian.setInt32(buffer, 0x20, BLOCK_SIZE - 0x18);
        return buffer;
    }

    /**
     * The index block VCN is 8 bytes. Read as an unsigned 32-bit value it wrapped for index allocations beyond
     * 4294967295 clusters.
     */
    @Test
    public void testIndexBlockVcnIs64Bit() throws Exception {
        assertThat(new IndexBlock(null, indexBlock(0x1_0000_0000L), 0).getIndexBlockVCN(), is(0x1_0000_0000L));
        assertThat(new IndexBlock(null, indexBlock(596), 0).getIndexBlockVCN(), is(596L));
        assertThat(new IndexBlock(null, indexBlock(0), 0).getIndexBlockVCN(), is(0L));
    }
}
