package org.jnode.fs.ntfs.index;

import org.jnode.fs.ntfs.NTFSTestRecords;
import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests for {@link IndexRoot}.
 */
public class NTFSIndexRootTest {

    /** The offset of the attribute data within the attribute. */
    private static final int DATA_OFFSET = 0x18;

    /**
     * Builds a resident $INDEX_ROOT attribute.
     *
     * @param indexBlockSize        the index block size, at data offset 0x08.
     * @param clustersPerIndexBlock the raw signed byte at data offset 0x0c.
     * @return the attribute.
     */
    private static byte[] indexRoot(int indexBlockSize, int clustersPerIndexBlock) {
        byte[] buffer = new byte[DATA_OFFSET + 0x30];
        LittleEndian.setInt32(buffer, 0x00, 0x90);              // $INDEX_ROOT
        LittleEndian.setInt32(buffer, 0x04, buffer.length);
        buffer[0x08] = 0;                                       // resident
        LittleEndian.setInt32(buffer, 0x10, 0x30);              // data length
        LittleEndian.setInt16(buffer, 0x14, DATA_OFFSET);       // data offset

        LittleEndian.setInt32(buffer, DATA_OFFSET, 0x30);       // indexed attribute: $FILE_NAME
        LittleEndian.setInt32(buffer, DATA_OFFSET + 0x08, indexBlockSize);
        buffer[DATA_OFFSET + 0x0c] = (byte) clustersPerIndexBlock;
        return buffer;
    }

    private static IndexRoot root(int indexBlockSize, int clustersPerIndexBlock) throws Exception {
        IndexRootAttribute attribute =
            (IndexRootAttribute) NTFSTestRecords.attribute(indexRoot(indexBlockSize, clustersPerIndexBlock));
        return attribute.getRoot();
    }

    @Test
    public void testOrdinaryClustersPerIndexBlock() throws Exception {
        assertThat(root(4096, 1).getClustersPerIndexBlock(), is(1));
        assertThat(root(4096, 8).getClustersPerIndexBlock(), is(8));
        assertThat(root(4096, 1).getIndexBlockSize(), is(4096));
    }

    /**
     * The caller divides the index block size by this, so a stored zero - which happens on a corrupt $INDEX_ROOT,
     * or one recovered from unallocated space - would throw an ArithmeticException that nothing on the path
     * catches.
     */
    @Test
    public void testAZeroClustersPerIndexBlockFallsBackToOne() throws Exception {
        assertThat(root(4096, 0).getClustersPerIndexBlock(), is(1));
    }

    @Test
    public void testANegativeClustersPerIndexBlockFallsBackToOne() throws Exception {
        assertThat(root(4096, -9).getClustersPerIndexBlock(), is(1));
    }
}
