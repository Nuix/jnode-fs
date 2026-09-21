package org.jnode.fs.ntfs;

import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests for {@link StandardInformationAttribute}.
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#the-standard-information-attribute">The standard information attribute</a>
 */
public class NTFSStandardInformationAttributeTest {

    /** The offset of the attribute data within the attribute. */
    private static final int DATA_OFFSET = 0x18;

    /**
     * Builds a resident $STANDARD_INFORMATION attribute with the 72 byte (version 3.0+) data.
     *
     * @param quotaCharged the value for data offset 0x38.
     * @param usn          the value for data offset 0x40.
     * @return the attribute.
     */
    private static byte[] standardInformation(long quotaCharged, long usn) {
        byte[] buffer = new byte[DATA_OFFSET + 72];
        LittleEndian.setInt32(buffer, 0x00, 0x10);              // $STANDARD_INFORMATION
        LittleEndian.setInt32(buffer, 0x04, buffer.length);
        buffer[0x08] = 0;                                       // resident
        LittleEndian.setInt32(buffer, 0x10, 72);                // data length
        LittleEndian.setInt16(buffer, 0x14, DATA_OFFSET);       // data offset

        LittleEndian.setInt64(buffer, DATA_OFFSET, 0x01D5A1B2C3D4E5F6L);        // creation
        LittleEndian.setInt64(buffer, DATA_OFFSET + 0x08, 0x01D5A1B2C3D4E5F7L); // modification
        LittleEndian.setInt64(buffer, DATA_OFFSET + 0x10, 0x01D5A1B2C3D4E5F8L); // MFT change
        LittleEndian.setInt64(buffer, DATA_OFFSET + 0x18, 0x01D5A1B2C3D4E5F9L); // access
        LittleEndian.setInt32(buffer, DATA_OFFSET + 0x20, 0x820);               // ARCHIVE | COMPRESSED
        LittleEndian.setInt32(buffer, DATA_OFFSET + 0x30, 7);                   // owner id
        LittleEndian.setInt32(buffer, DATA_OFFSET + 0x34, 5862);                // security id
        LittleEndian.setInt64(buffer, DATA_OFFSET + 0x38, quotaCharged);
        LittleEndian.setInt64(buffer, DATA_OFFSET + 0x40, usn);
        return buffer;
    }

    /**
     * The update sequence number is 8 bytes and routinely exceeds 2^31 - the worked example in the libyal
     * documentation is 11553149976. Read as a signed 32-bit value it came back truncated.
     */
    @Test
    public void testUpdateSequenceNumberIs64Bit() throws Exception {
        // Arrange
        byte[] buffer = standardInformation(0, 11553149976L);

        // Act
        StandardInformationAttribute attribute =
            (StandardInformationAttribute) NTFSTestRecords.attribute(buffer);

        // Assert
        assertThat(attribute.getUpdateSequenceNumber(), is(11553149976L));
    }

    @Test
    public void testQuotaChargedIs64Bit() throws Exception {
        // Arrange
        byte[] buffer = standardInformation(0x1_0000_0000L, 0);

        // Act
        StandardInformationAttribute attribute =
            (StandardInformationAttribute) NTFSTestRecords.attribute(buffer);

        // Assert
        assertThat(attribute.getQuotaCharged(), is(0x1_0000_0000L));
    }

    /**
     * The flag names must match the documented file attribute flags. DEVICE used to be labelled "Archive", and
     * several flags were missing entirely so they surfaced as "Unknown 0x...".
     */
    @Test
    public void testFlagNames() throws Exception {
        assertThat(StandardInformationAttribute.Flags.getNames(0x40), contains("Device"));
        assertThat(StandardInformationAttribute.Flags.getNames(0x20), contains("Archive"));
        assertThat(StandardInformationAttribute.Flags.getNames(0x10), contains("Directory"));
        assertThat(StandardInformationAttribute.Flags.getNames(0x8), contains("Volume Label"));
        assertThat(StandardInformationAttribute.Flags.getNames(0x10000), contains("Virtual"));
        assertThat(StandardInformationAttribute.Flags.getNames(0x10000000), contains("Has Index"));
        assertThat(StandardInformationAttribute.Flags.getNames(0x20000000), contains("Is Index View"));

        // ARCHIVE | COMPRESSED, the common combination in complex-compression.dd
        assertThat(StandardInformationAttribute.Flags.getNames(0x820), contains("Archive", "Compressed"));

        // Genuinely undocumented bits still report as unknown
        assertThat(StandardInformationAttribute.Flags.getNames(0x40000000), contains("Unknown 0x40000000"));
    }

    /**
     * The fields either side of the two widened ones are genuinely 4 bytes and must be left alone.
     */
    @Test
    public void testSurroundingFieldsAreUnchanged() throws Exception {
        // Arrange: 22720 is the largest update sequence number in the nuix-core corpus
        byte[] buffer = standardInformation(0, 22720);

        // Act
        StandardInformationAttribute attribute =
            (StandardInformationAttribute) NTFSTestRecords.attribute(buffer);

        // Assert
        assertThat(attribute.getUpdateSequenceNumber(), is(22720L));
        assertThat(attribute.getOwnerId(), is(7));
        assertThat(attribute.getSecurityId(), is(5862));
        assertThat(attribute.getFlags(), is(0x820));
        assertThat(attribute.getCreationTime(), is(0x01D5A1B2C3D4E5F6L));
        assertThat(attribute.getAccessTime(), is(0x01D5A1B2C3D4E5F9L));
    }
}
