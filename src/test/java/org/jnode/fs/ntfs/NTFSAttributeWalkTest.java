package org.jnode.fs.ntfs;

import java.util.List;

import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests that the attribute walk stays inside the MFT record it belongs to.
 */
public class NTFSAttributeWalkTest {

    private static final int RECORD_SIZE = 1024;
    private static final int FIRST_ATTRIBUTE_OFFSET = 0x38;

    /**
     * Builds an MFT record holding a single resident $FILE_NAME attribute.
     *
     * @param buffer          the buffer to build into.
     * @param recordOffset    the offset of the record within the buffer.
     * @param attributeLength the length to record for the attribute, which may deliberately be wrong.
     * @param terminate       whether to write the 0xffffffff end of list marker after the attribute.
     */
    private static void mftRecord(byte[] buffer, int recordOffset, int attributeLength, boolean terminate) {
        System.arraycopy("FILE".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, buffer, recordOffset, 4);
        LittleEndian.setInt16(buffer, recordOffset + 0x04, 0x30);   // fix-up offset
        LittleEndian.setInt16(buffer, recordOffset + 0x06, 3);      // fix-up count
        LittleEndian.setInt16(buffer, recordOffset + 0x14, FIRST_ATTRIBUTE_OFFSET);
        LittleEndian.setInt16(buffer, recordOffset + 0x16, 0x01);   // in use
        LittleEndian.setInt32(buffer, recordOffset + 0x18, 0x78);   // used entry size
        LittleEndian.setInt32(buffer, recordOffset + 0x1c, RECORD_SIZE);

        int attribute = recordOffset + FIRST_ATTRIBUTE_OFFSET;
        LittleEndian.setInt32(buffer, attribute + 0x00, 0x30);      // $FILE_NAME
        LittleEndian.setInt32(buffer, attribute + 0x04, attributeLength);
        buffer[attribute + 0x08] = 0;                               // resident
        LittleEndian.setInt32(buffer, attribute + 0x10, 0x44);      // data length
        LittleEndian.setInt16(buffer, attribute + 0x14, 0x18);      // data offset
        LittleEndian.setInt48(buffer, attribute + 0x18, 5);         // parent reference
        buffer[attribute + 0x58] = 1;                               // name length
        buffer[attribute + 0x59] = 1;                               // WIN32 namespace
        LittleEndian.setInt16(buffer, attribute + 0x5a, 'a');

        if (terminate) {
            LittleEndian.setInt32(buffer, attribute + 0x40, 0xFFFFFFFF);
        }
    }

    @Test
    public void testWellFormedRecordIsWalked() throws Exception {
        // Arrange
        byte[] buffer = new byte[RECORD_SIZE];
        mftRecord(buffer, 0, 0x40, true);

        // Act
        List<NTFSAttribute> attributes = new FileRecord(null, RECORD_SIZE, false, 1, buffer, 0)
            .readStoredAttributes();

        // Assert
        assertThat(attributes, hasSize(1));
        assertThat(attributes.get(0).getAttributeType(), is(NTFSAttribute.Types.FILE_NAME));
    }

    /**
     * An attribute whose recorded length runs past the end of the record must not be followed. Records live in a
     * shared buffer, so without a bound the walk continues into the neighbouring record.
     */
    @Test
    public void testAttributeRunningPastTheEndOfTheRecordIsNotFollowed() throws Exception {
        // Arrange: two records back to back. The first has an attribute claiming to be far longer than the record,
        // and no end of list marker, so an unbounded walk lands in the second record.
        byte[] buffer = new byte[2 * RECORD_SIZE];
        mftRecord(buffer, 0, 0x40000, false);
        mftRecord(buffer, RECORD_SIZE, 0x40, true);

        // Act
        List<NTFSAttribute> attributes = new FileRecord(null, RECORD_SIZE, false, 1, buffer, 0)
            .readStoredAttributes();

        // Assert: the oversized attribute is rejected rather than followed into the next record
        assertThat(attributes, is(empty()));
    }

    /**
     * Builds a record whose only attribute starts so close to the end that the type field fits but the header does
     * not. The used and allocated sizes are left at zero, as they are on a record recovered from unallocated space,
     * so the walk has nothing but the buffer to bound itself with.
     *
     * @param attributeOffset the offset to start the attribute at.
     * @param nonResident     whether to flag the attribute as non-resident, which has a longer header.
     * @return the record buffer.
     */
    private static byte[] recordWithAttributeAtTheEnd(int attributeOffset, boolean nonResident) {
        byte[] buffer = new byte[RECORD_SIZE];
        System.arraycopy("FILE".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, buffer, 0, 4);
        LittleEndian.setInt16(buffer, 0x04, 0x30);   // fix-up offset
        LittleEndian.setInt16(buffer, 0x06, 1);      // no fix-ups
        LittleEndian.setInt16(buffer, 0x14, attributeOffset);
        LittleEndian.setInt16(buffer, 0x16, 0x01);   // in use

        LittleEndian.setInt32(buffer, attributeOffset, 0x30);   // $FILE_NAME, not the end of list marker

        // The residency flag is itself past the end of the record in the tightest case, which is the point
        if (attributeOffset + 0x08 < buffer.length) {
            buffer[attributeOffset + 0x08] = (byte) (nonResident ? 1 : 0);
        }

        return buffer;
    }

    /**
     * An attribute whose four byte type field is the last thing in the record has no room for the rest of its
     * header. Reading it anyway runs off the end of the buffer, since building an attribute reads out to 0x18 for a
     * resident one.
     */
    @Test
    public void testAttributeWithNoRoomForItsHeaderIsNotRead() throws Exception {
        // Arrange: four bytes of room left, which is enough for the type but not for the header
        byte[] buffer = recordWithAttributeAtTheEnd(RECORD_SIZE - 4, false);

        // Act
        List<NTFSAttribute> attributes = new FileRecord(null, RECORD_SIZE, false, 1, buffer, 0)
            .readStoredAttributes();

        // Assert
        assertThat(attributes, is(empty()));
    }

    /**
     * A non-resident header is 0x40 bytes, so there is a range of offsets where a resident attribute would fit but
     * a non-resident one does not.
     */
    @Test
    public void testNonResidentAttributeWithNoRoomForItsHeaderIsNotRead() throws Exception {
        // Arrange: 0x20 bytes of room, enough for a resident header but not for a non-resident one
        byte[] buffer = recordWithAttributeAtTheEnd(RECORD_SIZE - 0x20, true);

        // Act
        List<NTFSAttribute> attributes = new FileRecord(null, RECORD_SIZE, false, 1, buffer, 0)
            .readStoredAttributes();

        // Assert
        assertThat(attributes, is(empty()));
    }

    /**
     * A record with no end of list marker at all must stop at the end of the record rather than running off the
     * end of the buffer.
     */
    @Test
    public void testUnterminatedRecordStopsAtTheRecordBoundary() throws Exception {
        // Arrange
        byte[] buffer = new byte[RECORD_SIZE];
        mftRecord(buffer, 0, 0x40, false);

        // Act
        List<NTFSAttribute> attributes = new FileRecord(null, RECORD_SIZE, false, 1, buffer, 0)
            .readStoredAttributes();

        // Assert: it read what it could and stopped without throwing
        assertThat(attributes, hasSize(1));
    }

    /**
     * Starts a record with no fix-ups, the given used size and a full allocated size.
     */
    private static byte[] emptyRecord(int usedSize) {
        byte[] buffer = new byte[RECORD_SIZE];
        System.arraycopy("FILE".getBytes(java.nio.charset.StandardCharsets.US_ASCII), 0, buffer, 0, 4);
        LittleEndian.setInt16(buffer, 0x04, 0x30);   // fix-up offset
        LittleEndian.setInt16(buffer, 0x06, 1);      // no fix-ups
        LittleEndian.setInt16(buffer, 0x14, FIRST_ATTRIBUTE_OFFSET);
        LittleEndian.setInt32(buffer, 0x18, usedSize);
        LittleEndian.setInt32(buffer, 0x1c, RECORD_SIZE);
        return buffer;
    }

    private static void residentAttribute(byte[] buffer, int offset, int type, int length) {
        LittleEndian.setInt32(buffer, offset, type);
        LittleEndian.setInt32(buffer, offset + 0x04, length);
        buffer[offset + 0x08] = 0;
        LittleEndian.setInt32(buffer, offset + 0x10, length - 0x18);
        LittleEndian.setInt16(buffer, offset + 0x14, 0x18);
    }

    private static void fileNameAttribute(byte[] buffer, int offset, int length, String name) {
        residentAttribute(buffer, offset, 0x30, length);
        LittleEndian.setInt32(buffer, offset + 0x10, 0x42 + 2 * name.length());
        LittleEndian.setInt48(buffer, offset + 0x18, 5);
        buffer[offset + 0x58] = (byte) name.length();
        buffer[offset + 0x59] = 1;
        for (int i = 0; i < name.length(); i++) {
            LittleEndian.setInt16(buffer, offset + 0x5a + 2 * i, name.charAt(i));
        }
    }

    private static void nonResidentAttribute(byte[] buffer, int offset, int type, long size, byte[] runs) {
        LittleEndian.setInt32(buffer, offset, type);
        LittleEndian.setInt32(buffer, offset + 0x04, 0x48);
        buffer[offset + 0x08] = 1;
        LittleEndian.setInt16(buffer, offset + 0x0a, 0x40);
        LittleEndian.setInt64(buffer, offset + 0x18, 8);           // last VCN
        LittleEndian.setInt16(buffer, offset + 0x20, 0x40);        // data runs offset
        LittleEndian.setInt64(buffer, offset + 0x28, 9 * 4096);    // allocated size
        LittleEndian.setInt64(buffer, offset + 0x30, size);
        LittleEndian.setInt64(buffer, offset + 0x38, size);
        System.arraycopy(runs, 0, buffer, offset + 0x40, runs.length);
    }

    /**
     * The shape of record 179 in the X-Ways sample $MFT: a deleted record whose used size, 344, stops inside its
     * second $FILE_NAME, with its $DATA after that. The chain past the used size is intact and ends on the marker.
     */
    @Test
    public void testAttributesPastTheUsedSizeAreRead() throws Exception {
        // Arrange
        byte[] buffer = emptyRecord(344);
        residentAttribute(buffer, 56, 0x10, 96);
        fileNameAttribute(buffer, 152, 120, "SHORTN~1.TXT");
        fileNameAttribute(buffer, 272, 200, "a long file name that runs on past the used size.txt");
        nonResidentAttribute(buffer, 472, 0x80, 34304, new byte[] { 0x21, 0x09, 0x1e, 0x0e, 0x00 });
        LittleEndian.setInt32(buffer, 544, 0xFFFFFFFF);

        // Act
        FileRecord record = new FileRecord(null, 4096, false, 179, buffer, 0);
        NTFSAttribute data = record.findAttributeByType(NTFSAttribute.Types.DATA);

        // Assert
        assertThat(record.readStoredAttributes(), hasSize(4));
        assertThat(record.getFileNameAttributes(), hasSize(2));
        assertThat(data, is(notNullValue()));
        assertThat(record.getAttributeTotalSize(NTFSAttribute.Types.DATA, null), is(34304L));
    }

    /**
     * A non-resident $INDEX_ROOT can't be read: the attributes before it are kept, and nothing throws.
     */
    @Test
    public void testAnUnreadableAttributeKeepsTheOnesBeforeIt() throws Exception {
        // Arrange
        byte[] buffer = emptyRecord(RECORD_SIZE);
        residentAttribute(buffer, 56, 0x10, 96);
        fileNameAttribute(buffer, 152, 120, "file.txt");
        nonResidentAttribute(buffer, 272, 0x90, 0x1000, new byte[] { 0x11, 0x01, 0x10, 0x00 });
        LittleEndian.setInt32(buffer, 344, 0xFFFFFFFF);

        // Act
        FileRecord record = new FileRecord(null, 4096, false, 7, buffer, 0);
        List<NTFSAttribute> attributes = record.getAllAttributes();

        // Assert
        assertThat(attributes, is(notNullValue()));
        assertThat(attributes, hasSize(2));
        assertThat(attributes.get(1).getAttributeType(), is(NTFSAttribute.Types.FILE_NAME));
        assertThat(record.findAttributeByType(NTFSAttribute.Types.INDEX_ROOT), is(nullValue()));
    }

    /**
     * A first attribute whose run list can't be decoded leaves an empty list, not a null one.
     */
    @Test
    public void testABadFirstRunListGivesAnEmptyList() throws Exception {
        // Arrange: a run header claiming a 15-byte length field
        byte[] buffer = emptyRecord(RECORD_SIZE);
        nonResidentAttribute(buffer, 56, 0x80, 0x1000, new byte[] { 0x1f, 0x01, 0x10, 0x00 });
        LittleEndian.setInt32(buffer, 56 + 0x48, 0xFFFFFFFF);

        // Act
        FileRecord record = new FileRecord(null, 4096, false, 7, buffer, 0);

        // Assert
        assertThat(record.getAllAttributes(), is(empty()));
        assertThat(record.findAttributeByType(NTFSAttribute.Types.DATA), is(nullValue()));
    }

    /**
     * Builds a record holding one $FILE_NAME within its used size, with a damaged end marker and an older $FILE_NAME
     * in the slack past it.
     */
    private static byte[] recordWithAttributeInTheSlack(boolean inUse) {
        byte[] buffer = emptyRecord(272);
        LittleEndian.setInt16(buffer, 0x16, inUse ? 0x01 : 0x00);
        residentAttribute(buffer, 56, 0x10, 96);
        fileNameAttribute(buffer, 152, 120, "live.txt");
        fileNameAttribute(buffer, 272, 120, "stale.txt");
        LittleEndian.setInt32(buffer, 392, 0xFFFFFFFF);
        return buffer;
    }

    @Test
    public void testSlackPastTheUsedSizeOfARecordInUseIsNotRead() throws Exception {
        // Arrange
        byte[] buffer = recordWithAttributeInTheSlack(true);

        // Act
        FileRecord record = new FileRecord(null, 4096, false, 7, buffer, 0);

        // Assert
        assertThat(record.readStoredAttributes(), hasSize(2));
        assertThat(record.getFileNameAttributes(), hasSize(1));
    }

    @Test
    public void testSlackPastTheUsedSizeOfADeletedRecordIsRead() throws Exception {
        // Arrange
        byte[] buffer = recordWithAttributeInTheSlack(false);

        // Act
        FileRecord record = new FileRecord(null, 4096, false, 7, buffer, 0);

        // Assert
        assertThat(record.readStoredAttributes(), hasSize(3));
    }

    /**
     * An $ATTRIBUTE_LIST past an attribute that can't be read is never seen, so the attributes may be incomplete.
     */
    @Test
    public void testAnUnreadableAttributeMarksTheAttributesIncomplete() throws Exception {
        // Arrange
        byte[] buffer = emptyRecord(RECORD_SIZE);
        residentAttribute(buffer, 56, 0x10, 96);
        nonResidentAttribute(buffer, 152, 0x90, 0x1000, new byte[] { 0x11, 0x01, 0x10, 0x00 });
        LittleEndian.setInt32(buffer, 224, 0xFFFFFFFF);

        // Act
        FileRecord record = new FileRecord(null, 4096, false, 7, buffer, 0);

        // Assert
        assertThat(record.isAttributeListIncomplete(), is(true));
    }

    @Test
    public void testAWellFormedRecordIsNotIncomplete() throws Exception {
        // Arrange
        byte[] buffer = new byte[RECORD_SIZE];
        mftRecord(buffer, 0, 0x40, true);

        // Act
        FileRecord record = new FileRecord(null, RECORD_SIZE, false, 1, buffer, 0);

        // Assert
        assertThat(record.isAttributeListIncomplete(), is(false));
    }
}
