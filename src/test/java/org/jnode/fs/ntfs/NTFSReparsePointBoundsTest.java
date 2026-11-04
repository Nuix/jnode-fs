package org.jnode.fs.ntfs;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.ReparsePointAttributeNonRes;
import org.jnode.fs.ntfs.attribute.ReparsePointAttributeRes;
import org.jnode.fs.ntfs.attribute.ReparsePointTags;
import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;

/**
 * Damaged reparse point lengths and unreadable values fail with an I/O exception rather than reading out of bounds
 * or reading the attribute header as the value.
 */
public class NTFSReparsePointBoundsTest {

    private static final int DATA_OFFSET = 0x18;
    private static final int CLUSTER_SIZE = 4096;

    /**
     * A resident $REPARSE_POINT holding {@code valueLength} bytes of value, with the given header fields.
     */
    private static byte[] resident(int valueLength, int dataOffset, int declaredLength) {
        // Room for the header even when the value is said to be shorter, so the attribute can claim less than it
        byte[] buffer = new byte[DATA_OFFSET + Math.max(valueLength, 8)];
        LittleEndian.setInt32(buffer, 0x00, 0xC0);
        LittleEndian.setInt32(buffer, 0x04, DATA_OFFSET + valueLength);
        LittleEndian.setInt32(buffer, 0x10, valueLength);
        LittleEndian.setInt16(buffer, 0x14, dataOffset);
        LittleEndian.setInt32(buffer, DATA_OFFSET, ReparsePointTags.IO_REPARSE_TAG_SYMLINK);
        LittleEndian.setInt16(buffer, DATA_OFFSET + 4, declaredLength);
        return buffer;
    }

    /**
     * A data length longer than the attribute holds is cut short to the attribute.
     */
    @Test
    public void testDeclaredLengthPastTheAttributeIsCutShort() throws Exception {
        ReparsePointAttributeRes attribute =
            (ReparsePointAttributeRes) NTFSTestRecords.attribute(resident(24, DATA_OFFSET, 0xffff));

        assertThat(attribute.getReparseData().length, is(16));
    }

    /**
     * A value length longer than the attribute is cut short to the attribute, not read from the next one.
     */
    @Test
    public void testValueLengthPastTheAttributeIsCutShort() throws Exception {
        byte[] buffer = resident(24, DATA_OFFSET, 0xffff);
        LittleEndian.setInt32(buffer, 0x10, 0x7fff0000);

        ReparsePointAttributeRes attribute = (ReparsePointAttributeRes) NTFSTestRecords.attribute(buffer);

        assertThat(attribute.getReparseData().length, is(16));
    }

    @Test
    public void testValueOutsideTheBufferIsRejected() throws Exception {
        ReparsePointAttributeRes attribute =
            (ReparsePointAttributeRes) NTFSTestRecords.attribute(resident(24, 0xfff0, 16));

        assertThrows(IOException.class, attribute::getReparseData);
        assertThrows(UncheckedIOException.class, attribute::getReparseTag);
        assertThrows(UncheckedIOException.class, attribute::getReparseDataLength);
    }

    @Test
    public void testValueTooShortForItsHeaderIsRejected() throws Exception {
        ReparsePointAttributeRes attribute =
            (ReparsePointAttributeRes) NTFSTestRecords.attribute(resident(4, DATA_OFFSET, 0));

        assertThrows(IOException.class, attribute::getReparseData);
        assertThrows(UncheckedIOException.class, attribute::getReparseTag);
    }

    /**
     * A non-resident value whose cluster cannot be read fails, and keeps failing, rather than reading the attribute
     * header in the MFT record as the value.
     */
    @Test
    public void testUnreadableNonResidentValueFails() throws Exception {
        NTFSVolume volume = NTFSTestVolume.volume(CLUSTER_SIZE, new byte[8 * CLUSTER_SIZE]);

        // One cluster at LCN 100, past the end of the eight cluster device
        byte[] attribute = NTFSTestRecords.nonResidentAttribute(0, 0, 0, 0, new byte[] {0x11, 0x01, 0x64, 0x00},
            CLUSTER_SIZE, 24, 24);
        LittleEndian.setInt32(attribute, 0x00, 0xC0);

        FileRecord record = NTFSTestRecords.record(volume, CLUSTER_SIZE, attribute);
        ReparsePointAttributeNonRes reparsePoint = (ReparsePointAttributeNonRes)
            NTFSAttribute.getAttribute(record, NTFSTestRecords.FIRST_ATTRIBUTE_OFFSET);

        assertThrows(UncheckedIOException.class, reparsePoint::getReparseTag);
        assertThrows(UncheckedIOException.class, reparsePoint::getReparseTag);
        assertThrows(IOException.class, reparsePoint::getReparseData);
    }
}
