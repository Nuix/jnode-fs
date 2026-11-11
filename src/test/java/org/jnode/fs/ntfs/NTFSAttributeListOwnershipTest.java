package org.jnode.fs.ntfs;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.jnode.fs.FileSystemTestUtils.toByteArray;

/**
 * Tests that an $ATTRIBUTE_LIST is only followed into records that still belong to the file.
 */
public class NTFSAttributeListOwnershipTest {

    private static final int RECORD_SIZE = 1024;
    private static final long BASE = 10;
    private static final long EXTENSION = 20;
    private static final int EXTENSION_SEQUENCE = 5;

    private final Map<Long, FileRecord> records = new HashMap<>();

    /**
     * Builds a record holding the given attributes.
     */
    private static FileRecord record(long reference, int sequence, long base, boolean inUse, byte[]... attributes)
        throws Exception {
        byte[] buffer = new byte[RECORD_SIZE];
        System.arraycopy("FILE".getBytes(StandardCharsets.US_ASCII), 0, buffer, 0, 4);
        LittleEndian.setInt16(buffer, 0x04, 0x30);
        LittleEndian.setInt16(buffer, 0x06, 1);
        LittleEndian.setInt16(buffer, 0x10, sequence);
        LittleEndian.setInt16(buffer, 0x14, 0x38);
        LittleEndian.setInt16(buffer, 0x16, inUse ? 1 : 0);
        LittleEndian.setInt32(buffer, 0x1c, RECORD_SIZE);
        LittleEndian.setInt48(buffer, 0x20, base);

        int offset = 0x38;
        for (byte[] attribute : attributes) {
            System.arraycopy(attribute, 0, buffer, offset, attribute.length);
            offset += attribute.length;
        }
        LittleEndian.setInt32(buffer, offset, 0xFFFFFFFF);
        LittleEndian.setInt32(buffer, 0x18, offset + 8);

        return new FileRecord(null, 4096, false, reference, buffer, 0);
    }

    private static byte[] resident(int type, int id, byte[] value) {
        int length = (0x18 + value.length + 7) & ~7;
        byte[] attribute = new byte[length];
        LittleEndian.setInt32(attribute, 0x00, type);
        LittleEndian.setInt32(attribute, 0x04, length);
        LittleEndian.setInt16(attribute, 0x0e, id);
        LittleEndian.setInt32(attribute, 0x10, value.length);
        LittleEndian.setInt16(attribute, 0x14, 0x18);
        System.arraycopy(value, 0, attribute, 0x18, value.length);
        return attribute;
    }

    private static byte[] fileName(int id, String name) {
        byte[] value = new byte[0x42 + 2 * name.length()];
        LittleEndian.setInt48(value, 0x00, 5);
        value[0x40] = (byte) name.length();
        value[0x41] = 1;
        for (int i = 0; i < name.length(); i++) {
            LittleEndian.setInt16(value, 0x42 + 2 * i, name.charAt(i));
        }
        return resident(0x30, id, value);
    }

    private static byte[] compressedData(int id, long firstVcn, int compressionUnit) {
        byte[] attribute = NTFSTestRecords.nonResidentAttribute(firstVcn, firstVcn + 15, 0x0001, compressionUnit,
            toByteArray("21 07 B4 08 01 09 00"));
        LittleEndian.setInt16(attribute, 0x0e, id);
        return attribute;
    }

    private static byte[] entry(int type, long startVcn, long reference, int sequence, int id) {
        byte[] entry = new byte[0x20];
        LittleEndian.setInt32(entry, 0x00, type);
        LittleEndian.setInt16(entry, 0x04, entry.length);
        entry[0x07] = 0x1a;
        LittleEndian.setInt64(entry, 0x08, startVcn);
        LittleEndian.setInt48(entry, 0x10, reference);
        LittleEndian.setInt16(entry, 0x16, sequence);
        LittleEndian.setInt16(entry, 0x18, id);
        return entry;
    }

    private static byte[] attributeList(byte[]... entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] entry : entries) {
            out.write(entry, 0, entry.length);
        }
        return resident(0x20, 1, out.toByteArray());
    }

    /**
     * A base record whose list puts its name in the extension record.
     */
    private FileRecord baseNamingTheExtension(boolean inUse) throws Exception {
        return baseNamingTheExtension(inUse, EXTENSION_SEQUENCE);
    }

    private FileRecord baseNamingTheExtension(boolean inUse, int listedSequence) throws Exception {
        return record(BASE, 1, 0, inUse, attributeList(
            entry(0x20, 0, BASE, 1, 1),
            entry(0x30, 0, EXTENSION, listedSequence, 2)));
    }

    private List<NTFSAttribute> attributes(FileRecord base) {
        return base.getAllAttributes(records::get);
    }

    @Test
    public void testItsOwnExtensionRecordIsFollowed() throws Exception {
        // Arrange
        records.put(EXTENSION, record(EXTENSION, EXTENSION_SEQUENCE, BASE, true, fileName(2, "mine.txt")));
        FileRecord base = baseNamingTheExtension(true);

        // Act + assert
        assertThat(attributes(base).size(), is(2));
        assertThat(base.isAttributeListIncomplete(), is(false));
    }

    @Test
    public void testAnExtensionRecordReusedAsABaseRecordIsSkipped() throws Exception {
        // Arrange: record 20 is now another file's base record, with base reference 0
        records.put(EXTENSION, record(EXTENSION, EXTENSION_SEQUENCE, 0, true, fileName(2, "theirs.txt")));
        FileRecord base = baseNamingTheExtension(true);

        // Act
        List<NTFSAttribute> attributes = attributes(base);

        // Assert
        assertThat(attributes.size(), is(1));
        assertThat(base.findAttributeByType(NTFSAttribute.Types.FILE_NAME), is(nullValue()));
        assertThat(base.isAttributeListIncomplete(), is(true));
    }

    @Test
    public void testAnExtensionRecordWithAnotherSequenceIsSkipped() throws Exception {
        // Arrange: record 20 points back at this base, but was freed and reused since the list was written
        records.put(EXTENSION, record(EXTENSION, EXTENSION_SEQUENCE + 2, BASE, true, fileName(2, "theirs.txt")));
        FileRecord base = baseNamingTheExtension(true);

        // Act + assert
        assertThat(attributes(base).size(), is(1));
        assertThat(base.isAttributeListIncomplete(), is(true));
    }

    @Test
    public void testADeletedFilesFreedExtensionRecordIsFollowed() throws Exception {
        // Arrange: deleting the file freed both records, which moved the extension record's sequence on by one
        records.put(EXTENSION, record(EXTENSION, EXTENSION_SEQUENCE + 1, BASE, false, fileName(2, "mine.txt")));
        FileRecord base = baseNamingTheExtension(false);

        // Act + assert
        assertThat(attributes(base).size(), is(2));
        assertThat(base.isAttributeListIncomplete(), is(false));
    }

    @Test
    public void testAFreedExtensionRecordOfAFileInUseIsSkipped() throws Exception {
        // Arrange: the file is in use, so a freed record one sequence on is no longer its extension
        records.put(EXTENSION, record(EXTENSION, EXTENSION_SEQUENCE + 1, BASE, false, fileName(2, "old.txt")));
        FileRecord base = baseNamingTheExtension(true);

        // Act + assert
        assertThat(attributes(base).size(), is(1));
        assertThat(base.isAttributeListIncomplete(), is(true));
    }

    @Test
    public void testAFreedExtensionRecordWhoseSequenceWrappedIsFollowed() throws Exception {
        // Arrange: the list recorded 0xFFFF, and freeing the record wrapped its sequence past 0 to 1
        records.put(EXTENSION, record(EXTENSION, 1, BASE, false, fileName(2, "mine.txt")));
        FileRecord base = baseNamingTheExtension(false, 0xFFFF);

        // Act + assert
        assertThat(attributes(base).size(), is(2));
        assertThat(base.isAttributeListIncomplete(), is(false));
    }

    /**
     * When the reused record held the VCN-0 extent, the later extent's own decoder comes first, and its stored unit
     * of 0 takes the 16-cluster default.
     */
    @Test
    public void testSkippingTheFirstExtentLeavesTheLaterOneWithTheDefaultUnit() throws Exception {
        // Arrange
        long reused = 30;
        records.put(reused, record(reused, 7, 0, true, compressedData(3, 0, 4)));
        records.put(EXTENSION, record(EXTENSION, EXTENSION_SEQUENCE, BASE, true, compressedData(3, 16, 0)));
        FileRecord base = record(BASE, 1, 0, true, attributeList(
            entry(0x20, 0, BASE, 1, 1),
            entry(0x80, 0, reused, 7, 3),
            entry(0x80, 16, EXTENSION, EXTENSION_SEQUENCE, 3)));

        // Act
        attributes(base);
        NTFSNonResidentAttribute data =
            (NTFSNonResidentAttribute) base.findAttributeByType(NTFSAttribute.Types.DATA);

        // Assert
        assertThat(data, is(notNullValue()));
        assertThat(data.getStartVCN(), is(16L));
        assertThat(data.getDataRuns().get(0).toString(),
            is("[compressed-run vcn:0-15 [[data-run vcn:0-6 cluster:2228]]]"));
        assertThat(base.isAttributeListIncomplete(), is(true));
    }
}
