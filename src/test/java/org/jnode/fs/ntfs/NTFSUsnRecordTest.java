package org.jnode.fs.ntfs;

import java.util.Arrays;

import org.jnode.fs.ntfs.usnjrnl.UsnJournal;
import org.jnode.fs.ntfs.usnjrnl.UsnRecordV2;
import org.jnode.fs.ntfs.usnjrnl.UsnRecordV3;
import org.jnode.fs.ntfs.usnjrnl.UsnRecordV4;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jnode.fs.FileSystemTestUtils.*;

/**
 * Tests for the USN change journal record structures.
 *
 * @see <a href="https://learn.microsoft.com/en-us/windows/win32/api/winioctl/ns-winioctl-usn_record_v3">USN_RECORD_V3</a>
 */
public class NTFSUsnRecordTest {

    /**
     * A USN_RECORD_V3 with a distinct value in every field.
     *
     * <pre>
     * 0x00 record length              88
     * 0x04 major version              3
     * 0x06 minor version              0
     * 0x08 file reference             FILE_ID_128, 16 bytes
     * 0x18 parent file reference      FILE_ID_128, 16 bytes
     * 0x28 USN                        0x0000000300000002 (deliberately > 32-bit)
     * 0x30 timestamp                  FILETIME for 1 second past the Unix epoch
     * 0x38 reason                     0x80000200 (FILE_DELETE | CLOSE)
     * 0x3c source info                0x00000002 (USN_SOURCE_AUXILIARY_DATA)
     * 0x40 security id                0x00001234
     * 0x44 file attribute flags       0x00000020 (ARCHIVE)
     * 0x48 name length                12 bytes
     * 0x4a name offset                0x4c
     * 0x4c name                       "ab.txt" UTF-16LE
     * </pre>
     */
    private static final String USN_RECORD_V3 =
        "58 00 00 00 03 00 00 00 " +
        "01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10 " +
        "11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 20 " +
        "02 00 00 00 03 00 00 00 " +
        "80 16 D7 D5 DE B1 9D 01 " +
        "00 02 00 80 " +
        "02 00 00 00 " +
        "34 12 00 00 " +
        "20 00 00 00 " +
        "0C 00 " +
        "4C 00 " +
        "61 00 62 00 2E 00 74 00 78 00 74 00";

    @Test
    public void testUsnRecordV3() {
        // Arrange
        byte[] buffer = toByteArray(USN_RECORD_V3);

        // Act
        UsnRecordV3 record = new UsnRecordV3(buffer, 0);

        // Assert
        assertThat(record.getSize(), is(88L));
        assertThat(record.getMajorVersion(), is(3));
        assertThat(record.getMinorVersion(), is(0));

        assertThat(record.getMftReference().getId(), is(toByteArray(
            "01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10")));
        assertThat(record.getParentMtfReference().getId(), is(toByteArray(
            "11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 20")));

        // 0x0000000300000002 - reading this at the wrong offset picks up the tail of the parent reference
        assertThat(record.getUsn(), is(0x0000000300000002L));
        assertThat(record.getTimestamp(), is(1000L));
        assertThat(record.getReason(), is(0x80000200L));
        assertThat(record.getSourceInfo(), is(2));
        assertThat(record.getSecurityId(), is(0x1234));
        assertThat(record.getFileAttributes(), is(0x20));
        assertThat(record.getFileNameSize(), is(12));
        assertThat(record.getFileName(), is("ab.txt"));
    }

    /**
     * The record is not always at the start of the buffer it is read from, so the file identifiers have to be
     * rebased onto the structure's own offset.
     */
    @Test
    public void testUsnRecordV3_atNonZeroOffset() {
        // Arrange: 64 bytes of filler in front of the record, so reading the identifiers from the front of the
        // buffer instead of from the record would return the filler.
        byte[] record = toByteArray(USN_RECORD_V3);
        byte[] buffer = new byte[64 + record.length];
        Arrays.fill(buffer, 0, 64, (byte) 0xAA);
        System.arraycopy(record, 0, buffer, 64, record.length);

        // Act
        UsnRecordV3 usnRecord = new UsnRecordV3(buffer, 64);

        // Assert
        assertThat(usnRecord.getMftReference().getId(), is(toByteArray(
            "01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10")));
        assertThat(usnRecord.getParentMtfReference().getId(), is(toByteArray(
            "11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 20")));
        assertThat(usnRecord.getUsn(), is(0x0000000300000002L));
        assertThat(usnRecord.getFileName(), is("ab.txt"));
    }

    /**
     * A USN_RECORD_V2. The two file references are 8 bytes here rather than the 16 of a V3 record.
     *
     * <pre>
     * 0x00 record length              80
     * 0x04 major version              2
     * 0x06 minor version              0
     * 0x08 file reference             8 bytes
     * 0x10 parent file reference      8 bytes
     * 0x18 USN                        0x0000000300000002
     * 0x20 timestamp                  FILETIME for 1 second past the Unix epoch
     * 0x28 reason                     0x80000200 (FILE_DELETE | CLOSE)
     * 0x2c source info                0x00000000
     * 0x30 security id                0x00001234
     * 0x34 file attribute flags       0x00000020 (ARCHIVE)
     * 0x38 name length                12 bytes
     * 0x3a name offset                0x3c
     * 0x3c name                       "ab.txt" UTF-16LE
     * </pre>
     */
    private static final String USN_RECORD_V2 =
        "48 00 00 00 02 00 00 00 " +
        "01 02 03 04 05 06 07 08 " +
        "11 12 13 14 15 16 17 18 " +
        "02 00 00 00 03 00 00 00 " +
        "80 16 D7 D5 DE B1 9D 01 " +
        "00 02 00 80 " +
        "00 00 00 00 " +
        "34 12 00 00 " +
        "20 00 00 00 " +
        "0C 00 " +
        "3C 00 " +
        "61 00 62 00 2E 00 74 00 78 00 74 00";

    @Test
    public void testUsnRecordV2() {
        // Arrange
        byte[] buffer = toByteArray(USN_RECORD_V2);

        // Act
        UsnRecordV2 record = new UsnRecordV2(buffer, 0);

        // Assert
        assertThat(record.getSize(), is(72L));
        assertThat(record.getMajorVersion(), is(2));
        assertThat(record.getMinorVersion(), is(0));
        // V2 file references are 8 bytes, of which the accessors expose the low 48 bits: the MFT entry index. The
        // top 16 bits are the sequence number and are masked off.
        assertThat(record.getMftReference(), is(0x060504030201L));
        assertThat(record.getParentMtfReference(), is(0x161514131211L));
        assertThat(record.getUsn(), is(0x0000000300000002L));
        assertThat(record.getTimestamp(), is(1000L));
        assertThat(record.getReason(), is(0x80000200L));

        // The source info is zero. Read one byte early it picks up the 0x80 top byte of the reason and returns 128,
        // which is what every record with the CLOSE flag set used to do.
        assertThat(record.getSourceInfo(), is(0));

        assertThat(record.getSecurityId(), is(0x1234));
        assertThat(record.getFileAttributes(), is(0x20));
        assertThat(record.getFileNameSize(), is(12));
        assertThat(record.getFileName(), is("ab.txt"));
    }

    /**
     * A USN_RECORD_V4. Like V3 it uses 128-bit file identifiers, but it carries extents instead of a name.
     *
     * <pre>
     * 0x00 record length              64
     * 0x04 major version              4
     * 0x06 minor version              0
     * 0x08 file reference             FILE_ID_128, 16 bytes
     * 0x18 parent file reference      FILE_ID_128, 16 bytes
     * 0x28 USN                        0x0000000300000002
     * 0x30 reason                     0x80000200
     * 0x34 source info                0x00000002
     * 0x38 remaining extents          0
     * 0x3c number of extents          1
     * 0x3e extent size                0x10
     * </pre>
     */
    private static final String USN_RECORD_V4 =
        "40 00 00 00 04 00 00 00 " +
        "01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10 " +
        "11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 20 " +
        "02 00 00 00 03 00 00 00 " +
        "00 02 00 80 " +
        "02 00 00 00 " +
        "00 00 00 00 " +
        "01 00 " +
        "10 00";

    @Test
    public void testUsnRecordV4() {
        // Arrange
        byte[] buffer = toByteArray(USN_RECORD_V4);

        // Act
        UsnRecordV4 record = new UsnRecordV4(buffer, 0);

        // Assert
        assertThat(record.getSize(), is(64L));
        assertThat(record.getMajorVersion(), is(4));
        assertThat(record.getMinorVersion(), is(0));
        assertThat(record.getMftReference().getId(), is(toByteArray(
            "01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F 10")));
        assertThat(record.getParentMtfReference().getId(), is(toByteArray(
            "11 12 13 14 15 16 17 18 19 1A 1B 1C 1D 1E 1F 20")));

        // Read at the old offsets these picked up the tail of the parent reference and the low half of the USN
        assertThat(record.getUsn(), is(0x0000000300000002L));
        assertThat(record.getReason(), is(0x80000200L));
        assertThat(record.getSourceInfo(), is(2));
    }

    @Test
    public void testFileAttributeLookup() {
        // ENCRYPTED used to be registered as the mask 0x3fff, so it matched almost any input and corrupted the
        // remainder into a bogus "unknown-x..." entry.
        assertThat(UsnJournal.FileAttribute.lookupAttributes(0x20), contains("archive"));
        assertThat(UsnJournal.FileAttribute.lookupAttributes(0x4000), contains("encrypted"));
        assertThat(UsnJournal.FileAttribute.lookupAttributes(0x21), contains("read-only", "archive"));
        assertThat(UsnJournal.FileAttribute.lookupAttributes(0x10000), contains("virtual"));
        assertThat(UsnJournal.FileAttribute.lookupAttributes(0x8000), contains("unknown-x8000"));
    }

    @Test
    public void testReasonLookup() {
        assertThat(UsnJournal.Reason.lookupReasons(0x1), contains("data-overwrite"));
        assertThat(UsnJournal.Reason.lookupReasons(0x2), contains("data-extend"));
        assertThat(UsnJournal.Reason.lookupReasons(0x4), contains("data-truncation"));
        assertThat(UsnJournal.Reason.lookupReasons(0x10), contains("named-data-overwrite"));
        assertThat(UsnJournal.Reason.lookupReasons(0x20), contains("named-data-extend"));
        assertThat(UsnJournal.Reason.lookupReasons(0x40), contains("named-data-truncation"));
        assertThat(UsnJournal.Reason.lookupReasons(0x400000), contains("transacted-change"));
        assertThat(UsnJournal.Reason.lookupReasons(0x800000), contains("integrity-change"));

        // Values taken from the first three records of the usnjrnl.dat sample in the nuix-core test corpus.
        assertThat(UsnJournal.Reason.lookupReasons(0x80000200L),
            contains("fs-entry-deleted", "fs-entry-closed"));
        assertThat(UsnJournal.Reason.lookupReasons(0x100), contains("fs-entry-created"));
        assertThat(UsnJournal.Reason.lookupReasons(0x102), contains("data-extend", "fs-entry-created"));
    }
}
