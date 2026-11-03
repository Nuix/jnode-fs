package org.jnode.fs.ntfs;

import org.jnode.fs.ntfs.attribute.AttributeListBlock;
import org.jnode.fs.ntfs.attribute.AttributeListEntry;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jnode.fs.ntfs.NTFSTestRecords.*;

/**
 * Tests that the 64-bit virtual cluster number fields are read at their full width.
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#non-resident-mft-attribute">Non-resident MFT attribute</a>
 */
public class NTFSVirtualClusterNumberTest {

    /**
     * A last VCN of -1 in combination with a data size of 0 occurs on essentially every volume - it is present in
     * all 19 NTFS images in the nuix-core test corpus. Read as an unsigned 32-bit value it came back as
     * 4294967295.
     */
    @Test
    public void testLastVcnOfMinusOne() throws Exception {
        // Arrange
        byte[] buffer = nonResidentAttribute(0, -1L);

        // Act
        NTFSNonResidentAttribute attribute = (NTFSNonResidentAttribute) attribute(buffer);

        // Assert
        assertThat(attribute.getLastVCN(), is(-1L));
    }

    @Test
    public void testVcnsBeyond32Bits() throws Exception {
        // Arrange
        byte[] buffer = nonResidentAttribute(0x100000000L, 0x1000000FFL);

        // Act
        NTFSNonResidentAttribute attribute = (NTFSNonResidentAttribute) attribute(buffer);

        // Assert
        assertThat(attribute.getStartVCN(), is(0x100000000L));
        assertThat(attribute.getLastVCN(), is(0x1000000FFL));
    }

    @Test
    public void testOrdinaryVcnsAreUnaffected() throws Exception {
        // Arrange: the largest start VCN seen in the corpus is 41840, in ntfs1-gen2
        byte[] buffer = nonResidentAttribute(41840, 41855);

        // Act
        NTFSNonResidentAttribute attribute = (NTFSNonResidentAttribute) attribute(buffer);

        // Assert
        assertThat(attribute.getStartVCN(), is(41840L));
        assertThat(attribute.getLastVCN(), is(41855L));
    }

    /**
     * The attribute list entry's data first VCN is also 8 bytes. Read as 16 bits it wrapped above 65535, which is
     * only 256 MB into a file at the usual 4 KiB cluster size.
     */
    @Test
    public void testAttributeListEntryStartingVcn() throws Exception {
        // Arrange: a single $DATA entry, 0x20 bytes, with a starting VCN that does not fit in 16 bits
        byte[] buffer = new byte[0x20];
        LittleEndian.setInt32(buffer, 0x00, 0x80);      // $DATA
        LittleEndian.setInt16(buffer, 0x04, 0x20);      // entry length
        LittleEndian.setInt64(buffer, 0x08, 0x1234_5678L);
        LittleEndian.setInt48(buffer, 0x10, 42);        // file reference
        LittleEndian.setInt16(buffer, 0x18, 3);         // attribute id

        // Act
        AttributeListBlock block = new AttributeListBlock(buffer, 0, buffer.length);
        AttributeListEntry entry = block.getAllEntries().next();

        // Assert
        assertThat(entry.getStartingVCN(), is(0x1234_5678L));
        assertThat(entry.getFileReferenceNumber(), is(42L));
    }
}
