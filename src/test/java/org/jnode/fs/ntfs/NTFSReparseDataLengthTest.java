package org.jnode.fs.ntfs;

import org.jnode.fs.ntfs.attribute.ReparsePointAttributeRes;
import org.jnode.fs.ntfs.attribute.ReparsePointTags;
import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests that the reparse data length is read as the 16-bit field it is.
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#the-reparse-point">The reparse point</a>
 */
public class NTFSReparseDataLengthTest {

    /** The offset of the attribute data within the attribute. */
    private static final int DATA_OFFSET = 0x18;

    /**
     * Builds a resident $REPARSE_POINT attribute.
     *
     * @param tag        the reparse point tag.
     * @param dataLength the 16-bit reparse data length at data offset 0x04.
     * @param reserved   the 16-bit reserved field at data offset 0x06.
     * @return the attribute.
     */
    private static byte[] reparsePoint(int tag, int dataLength, int reserved) {
        byte[] buffer = new byte[DATA_OFFSET + 8 + dataLength];
        LittleEndian.setInt32(buffer, 0x00, 0xC0);              // $REPARSE_POINT
        LittleEndian.setInt32(buffer, 0x04, buffer.length);
        buffer[0x08] = 0;                                       // resident
        LittleEndian.setInt32(buffer, 0x10, 8 + dataLength);    // data length
        LittleEndian.setInt16(buffer, 0x14, DATA_OFFSET);       // data offset

        LittleEndian.setInt32(buffer, DATA_OFFSET, tag);
        LittleEndian.setInt16(buffer, DATA_OFFSET + 0x04, dataLength);
        LittleEndian.setInt16(buffer, DATA_OFFSET + 0x06, reserved);
        return buffer;
    }

    /**
     * The reserved field is normally zero, which is why folding it into the length happened to give the right
     * answer on real data. A non-zero reserved value is what exposes it.
     */
    @Test
    public void testReparseDataLengthIgnoresTheReservedField() throws Exception {
        // Arrange
        byte[] buffer = reparsePoint(ReparsePointTags.IO_REPARSE_TAG_MOUNT_POINT, 0x30, 0xABCD);

        // Act
        ReparsePointAttributeRes attribute =
            (ReparsePointAttributeRes) NTFSTestRecords.attribute(buffer);

        // Assert
        assertThat(attribute.getReparseTag(), is(ReparsePointTags.IO_REPARSE_TAG_MOUNT_POINT));
        assertThat(attribute.getReparseDataLength(), is(0x30));
    }

    @Test
    public void testReparseDataLengthWithAZeroReservedField() throws Exception {
        // Arrange
        byte[] buffer = reparsePoint(ReparsePointTags.IO_REPARSE_TAG_SYMLINK, 0x1234, 0);

        // Act
        ReparsePointAttributeRes attribute =
            (ReparsePointAttributeRes) NTFSTestRecords.attribute(buffer);

        // Assert
        assertThat(attribute.getReparseTag(), is(ReparsePointTags.IO_REPARSE_TAG_SYMLINK));
        assertThat(attribute.getReparseDataLength(), is(0x1234));
    }
}
