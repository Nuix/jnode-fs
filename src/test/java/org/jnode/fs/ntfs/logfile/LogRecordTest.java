package org.jnode.fs.ntfs.logfile;

import java.util.Arrays;

import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests for {@link LogRecord}.
 */
public class LogRecordTest {

    private static final int PAGE_SIZE = 0x1000;
    private static final int LOG_PAGE_DATA_OFFSET = 0x40;

    /**
     * Builds a buffer of log pages filled with a recognisable pattern, holding a record that is flagged as
     * crossing a page boundary.
     *
     * @param recordOffset the offset to place the record at.
     * @return the buffer.
     */
    private static byte[] logPages(int recordOffset) {
        byte[] buffer = new byte[4 * PAGE_SIZE];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (i & 0xFF);
        }

        LittleEndian.setInt16(buffer, recordOffset + 0x28, LogRecord.FLAG_CROSSES_PAGE);
        return buffer;
    }

    /**
     * A record whose data runs off the end of its page continues in the data area of the next page, past that
     * page's header.
     *
     * <p>The offsets used here are absolute positions in the buffer. Treating the page number as a byte offset
     * made this read from a few bytes into the buffer instead.</p>
     */
    @Test
    public void testGetDataAcrossPages() {
        // Arrange: the record starts 4000 bytes into page 1, so only 96 bytes of it are on that page
        int recordOffset = PAGE_SIZE + 4000;
        byte[] buffer = logPages(recordOffset);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);
        assertThat("the record must be flagged as crossing a page", record.getCrossesPage(), is(true));

        // Act
        byte[] actual = new byte[200];
        record.getDataAcrossPages(0, actual, 0, actual.length);

        // Assert: 96 bytes from the tail of page 1, then 104 from page 2 starting after its header
        byte[] expected = new byte[200];
        System.arraycopy(buffer, recordOffset, expected, 0, 96);
        System.arraycopy(buffer, 2 * PAGE_SIZE + LOG_PAGE_DATA_OFFSET, expected, 96, 104);

        assertThat(actual, is(expected));
    }

    /**
     * Data that happens to fit within the page it starts on comes back contiguously, even though the record is
     * flagged as crossing.
     */
    @Test
    public void testGetDataAcrossPages_whenItFitsInOnePage() {
        // Arrange
        int recordOffset = PAGE_SIZE + 100;
        byte[] buffer = logPages(recordOffset);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);

        // Act
        byte[] actual = new byte[64];
        record.getDataAcrossPages(0, actual, 0, actual.length);

        // Assert
        assertThat(actual, is(Arrays.copyOfRange(buffer, recordOffset, recordOffset + 64)));
    }

    /**
     * The offset being read can itself be past the end of the record's own page, which is the normal case for the
     * redo and undo payloads: they start 0x30 bytes plus the stored offset into a record that is only flagged as
     * crossing because it runs off the end of its page.
     */
    @Test
    public void testGetDataAcrossPages_whenTheOffsetStartsOnTheNextPage() {
        // Arrange: the record starts 4000 bytes into page 1, so only 96 bytes of it are on that page
        int recordOffset = PAGE_SIZE + 4000;
        byte[] buffer = logPages(recordOffset);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);

        // Act: byte 96 of the record is the first byte of the next page's data area, past that page's header
        byte[] actual = new byte[64];
        record.getDataAcrossPages(96, actual, 0, actual.length);

        // Assert
        int expectedStart = 2 * PAGE_SIZE + LOG_PAGE_DATA_OFFSET;
        assertThat(actual, is(Arrays.copyOfRange(buffer, expectedStart, expectedStart + 64)));
    }

    /**
     * Further into the continuation the mapping stays one to one with the next page's data area.
     */
    @Test
    public void testGetDataAcrossPages_whenTheOffsetIsWellIntoTheNextPage() {
        // Arrange
        int recordOffset = PAGE_SIZE + 4000;
        byte[] buffer = logPages(recordOffset);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);

        // Act
        byte[] actual = new byte[32];
        record.getDataAcrossPages(96 + 500, actual, 0, actual.length);

        // Assert
        int expectedStart = 2 * PAGE_SIZE + LOG_PAGE_DATA_OFFSET + 500;
        assertThat(actual, is(Arrays.copyOfRange(buffer, expectedStart, expectedStart + 32)));
    }

    /**
     * A long enough read runs through a whole continuation page and into the one after it, skipping each page
     * header on the way.
     */
    @Test
    public void testGetDataAcrossPages_spanningThreePages() {
        // Arrange
        int recordOffset = PAGE_SIZE + 4000;
        byte[] buffer = logPages(recordOffset);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);

        int onFirstPage = 96;
        int perContinuationPage = PAGE_SIZE - LOG_PAGE_DATA_OFFSET;

        // Act
        byte[] actual = new byte[onFirstPage + perContinuationPage + 128];
        record.getDataAcrossPages(0, actual, 0, actual.length);

        // Assert
        byte[] expected = new byte[actual.length];
        System.arraycopy(buffer, recordOffset, expected, 0, onFirstPage);
        System.arraycopy(buffer, 2 * PAGE_SIZE + LOG_PAGE_DATA_OFFSET, expected, onFirstPage, perContinuationPage);
        System.arraycopy(buffer, 3 * PAGE_SIZE + LOG_PAGE_DATA_OFFSET, expected,
            onFirstPage + perContinuationPage, 128);

        assertThat(actual, is(expected));
    }

    /**
     * The header fields are mapped the same way, so a field that lands past the page boundary has to come from the
     * next page's data area rather than from a fixed distance further along the buffer.
     */
    @Test
    public void testGetUInt16AcrossPages_whenTheFieldIsOnTheNextPage() {
        // Arrange: the record starts 0x38 bytes before the end of its page, so the undo offset at 0x38 is the first
        // header field that lands on the next page
        int recordOffset = 2 * PAGE_SIZE - 0x38;
        byte[] buffer = logPages(recordOffset);
        LittleEndian.setInt16(buffer, recordOffset + 0x36, 0x1234);                  // redo length, still in page
        LittleEndian.setInt16(buffer, 2 * PAGE_SIZE + LOG_PAGE_DATA_OFFSET, 0xBEEF); // undo offset, next page

        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);
        assertThat(record.getCrossesPage(), is(true));

        // Assert
        assertThat(record.getRedoLength(), is(0x1234));
        assertThat(record.getUndoOffset(), is(0xBEEF));
    }

    /**
     * The same for the 64-bit fields.
     */
    @Test
    public void testGetInt64AcrossPages_whenTheFieldIsOnTheNextPage() {
        // Arrange: the target LCN at 0x50 lands on the next page
        int recordOffset = 2 * PAGE_SIZE - 0x50;
        byte[] buffer = logPages(recordOffset);
        LittleEndian.setInt64(buffer, 2 * PAGE_SIZE + LOG_PAGE_DATA_OFFSET, 0x1DEADBEEFL);

        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);

        // Assert
        assertThat(record.getTargetLcn(), is(0x1DEADBEEFL));
    }

    /**
     * A record that does not cross a page is read straight out, relative to the record.
     */
    @Test
    public void testGetDataAcrossPages_whenTheRecordDoesNotCross() {
        // Arrange
        int recordOffset = PAGE_SIZE + 100;
        byte[] buffer = logPages(recordOffset);
        LittleEndian.setInt16(buffer, recordOffset + 0x28, 0);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);
        assertThat(record.getCrossesPage(), is(false));

        // Act
        byte[] actual = new byte[64];
        record.getDataAcrossPages(8, actual, 0, actual.length);

        // Assert
        assertThat(actual, is(Arrays.copyOfRange(buffer, recordOffset + 8, recordOffset + 8 + 64)));
    }

    /**
     * A record on the final page must not read past the end of the buffer.
     *
     * <p>{@link LogRecord#getDataAcrossPages} bounds each copy by what is left in the page, but not by what is
     * left in the buffer. On the last page {@code nextPageDataStart} wraps to the start of the normal area - but
     * the guard that triggers the wrap only checks that the *next* page is past the end, so when the buffer is
     * no longer than the normal area itself the wrap target is past the end too, and the following
     * {@code System.arraycopy} runs off the array.</p>
     *
     * <p>Seen against a real $LogFile as
     * {@code arraycopy: last source index 2098141 out of bounds for byte[2097152]}.</p>
     */
    @Test
    public void testGetDataAcrossPages_whenTheRecordIsOnTheLastPage() {
        // Arrange: a crossing record near the end of the final page of the buffer. The buffer is exactly the
        // normal area, so the wrap has nowhere valid to land.
        byte[] buffer = new byte[LogFile.NORMAL_AREA_START * PAGE_SIZE];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (i & 0xFF);
        }
        int recordOffset = buffer.length - 128;
        LittleEndian.setInt16(buffer, recordOffset + 0x28, LogRecord.FLAG_CROSSES_PAGE);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);
        assertThat("the record must be flagged as crossing a page", record.getCrossesPage(), is(true));

        // Act: ask for more than the tail of the last page holds, which forces the wrap
        byte[] actual = new byte[512];
        record.getDataAcrossPages(0, actual, 0, actual.length);

        // Assert: the first 128 bytes still come from the record itself. Where the rest is taken from is the fix
        // author's call - wrapping to the normal area or stopping short are both defensible - but reading outside
        // the buffer is not.
        assertThat(Arrays.copyOfRange(actual, 0, 128),
                   is(Arrays.copyOfRange(buffer, recordOffset, recordOffset + 128)));
    }

    /**
     * A record whose offset lies outside the buffer must not blow up when it is checked for validity.
     *
     * <p>{@code LogFile.parseRecords} works out a record offset as a {@code long} and narrows it with an
     * {@code (int)} cast before constructing a {@link LogRecord}, without checking it against the buffer. A
     * corrupt or unexpected page therefore yields a record positioned outside - or, once the cast overflows,
     * before - the buffer, and {@link LogRecord#isValid()} reads there.</p>
     *
     * <p>Seen against a real $LogFile as
     * {@code Index -1996437928 out of bounds for length 67108864} from {@code LittleEndian.getInt64}.</p>
     */
    @Test
    public void testIsValid_whenTheRecordOffsetIsOutsideTheBuffer() {
        // Arrange
        byte[] buffer = logPages(PAGE_SIZE);

        // Act + assert: a record past the end of the buffer is simply not valid
        LogRecord past = new LogRecord(buffer, buffer.length + LOG_PAGE_DATA_OFFSET, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);
        assertThat("a record beyond the buffer cannot be valid", past.isValid(), is(false));

        // ... and neither is one whose offset has overflowed to a negative value
        LogRecord negative = new LogRecord(buffer, -1996437928, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);
        assertThat("a record at a negative offset cannot be valid", negative.isValid(), is(false));
    }

    /**
     * A record on the last page of the log can have only the fixed part of its header before the end of the buffer,
     * with the rest wrapping around to the normal area. The across-pages accessors read that part, so the record is
     * valid as long as the fixed part is in the buffer.
     */
    @Test
    public void testIsValid_whenOnlyTheTailOfTheHeaderWrapsPastTheBuffer() {
        // Arrange: 0x40 bytes before the end, so the 0x30 byte fixed part fits and the rest of the 0x58 header wraps
        byte[] buffer = new byte[8 * PAGE_SIZE];
        int recordOffset = buffer.length - 0x40;
        LittleEndian.setInt64(buffer, recordOffset, 0x1234);
        LittleEndian.setInt16(buffer, recordOffset + 0x28, LogRecord.FLAG_CROSSES_PAGE);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);

        // Act + assert
        assertThat(record.getLsn(), is(0x1234L));
        assertThat("the fixed part of the record is in the buffer", record.isValid(), is(true));
    }

    /**
     * Windows 8 and later keep page copies in pages 4-33, so a record crossing the last page continues in page 34.
     */
    @Test
    public void testGetDataAcrossPages_wrapsToTheGivenFirstCircularPage() {
        // Arrange
        byte[] buffer = new byte[40 * PAGE_SIZE];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) (i * 7);
        }
        int recordOffset = buffer.length - 0x40;
        LittleEndian.setInt16(buffer, recordOffset + 0x28, LogRecord.FLAG_CROSSES_PAGE);
        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET,
            LogFile.BUFFER_AREA_END);

        // Act
        byte[] actual = new byte[0x80];
        record.getDataAcrossPages(0, actual, 0, actual.length);

        // Assert
        int continuation = LogFile.BUFFER_AREA_END * PAGE_SIZE + LOG_PAGE_DATA_OFFSET;
        assertThat(Arrays.copyOfRange(actual, 0x40, 0x80),
                   is(Arrays.copyOfRange(buffer, continuation, continuation + 0x40)));
    }

    /**
     * Only the restart area is copied out of a checkpoint record, however long its client data claims to be.
     */
    @Test
    public void testGetCheckpoint_whenTheClientDataLengthIsHuge() {
        // Arrange
        byte[] buffer = new byte[2 * PAGE_SIZE];
        int offset = PAGE_SIZE + LOG_PAGE_DATA_OFFSET;
        LittleEndian.setInt64(buffer, offset, 0x1000);
        LittleEndian.setInt32(buffer, offset + 0x18, 0x7FFFFFF0);
        LittleEndian.setInt32(buffer, offset + 0x20, LogRecord.RECORD_TYPE_CHECKPOINT);
        LogRecord record = new LogRecord(buffer, offset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);

        // Act
        CheckpointRecord checkpoint = record.getCheckpoint();

        // Assert
        assertThat(checkpoint, is(notNullValue()));
    }

    /**
     * Builds a page holding one update record at the data offset that doesn't cross the page.
     */
    private static LogRecord updateRecord(int clientDataLength, int lcnsToFollow) {
        byte[] buffer = new byte[2 * PAGE_SIZE];
        int offset = PAGE_SIZE + LOG_PAGE_DATA_OFFSET;
        LittleEndian.setInt64(buffer, offset, 0x1000);
        LittleEndian.setInt32(buffer, offset + 0x18, clientDataLength);
        LittleEndian.setInt32(buffer, offset + 0x20, LogRecord.RECORD_TYPE_UPDATE);
        LittleEndian.setInt16(buffer, offset + 0x3e, lcnsToFollow);
        return new LogRecord(buffer, offset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);
    }

    @Test
    public void testNoTargetLcnWhenNoneFollow() {
        // Arrange: the bytes where an LCN would be are the start of the redo data
        LogRecord record = updateRecord(0x28, 0);
        LittleEndian.setInt64(record.getBuffer(), record.getOffset() + 0x50, 0x1234);

        // Act + assert
        assertThat(record.hasTargetLcn(), is(false));
        assertThat(record.getTargetLcns().length, is(0));
    }

    @Test
    public void testTargetLcnsAndVcnAreRead64Bit() {
        // Arrange
        LogRecord record = updateRecord(0x30, 2);
        LittleEndian.setInt64(record.getBuffer(), record.getOffset() + 0x48, 0x123456789AL);
        LittleEndian.setInt64(record.getBuffer(), record.getOffset() + 0x50, 0x100000001L);
        LittleEndian.setInt64(record.getBuffer(), record.getOffset() + 0x58, 0x200000002L);

        // Act + assert
        assertThat(record.getTargetVcn(), is(0x123456789AL));
        assertThat(record.hasTargetLcn(), is(true));
        assertThat(record.getTargetLcn(), is(0x100000001L));
        assertThat(record.getTargetLcns(), is(new long[] { 0x100000001L, 0x200000002L }));
    }

    @Test
    public void testTargetLcnsStopAtTheEndOfTheRecord() {
        // Arrange: three LCNs claimed, but the client data only has room for one
        LogRecord record = updateRecord(0x28, 3);

        // Act + assert
        assertThat(record.getTargetLcns().length, is(1));
    }

    @Test
    public void testRedoDataOutsideTheRecordIsNotCopied() {
        // Arrange: 0x28 bytes of client data, with redo data claimed at 0x28 for 0x278 bytes
        LogRecord record = updateRecord(0x28, 0);
        byte[] buffer = record.getBuffer();
        LittleEndian.setInt16(buffer, record.getOffset() + 0x34, 0x28);
        LittleEndian.setInt16(buffer, record.getOffset() + 0x36, 0x278);
        Arrays.fill(buffer, record.getOffset() + 0x58, buffer.length, (byte) 0xAA);

        // Act
        byte[] redo = new byte[record.getRedoLength()];
        record.getRedoData(redo);

        // Assert
        assertThat(record.isRedoDataInRecord(), is(false));
        assertThat(redo, is(new byte[0x278]));
    }

    @Test
    public void testRedoDataInsideTheRecordIsCopied() {
        // Arrange
        LogRecord record = updateRecord(0x30, 0);
        byte[] buffer = record.getBuffer();
        LittleEndian.setInt16(buffer, record.getOffset() + 0x34, 0x28);
        LittleEndian.setInt16(buffer, record.getOffset() + 0x36, 8);
        LittleEndian.setInt64(buffer, record.getOffset() + 0x58, 0x0807060504030201L);

        // Act
        byte[] redo = new byte[record.getRedoLength()];
        record.getRedoData(redo);

        // Assert
        assertThat(record.isRedoDataInRecord(), is(true));
        assertThat(redo, is(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }));
    }

    @Test
    public void testRedoDataOutsideTheRecordIsEmpty() {
        // Arrange: as above, redo data claimed past the client data
        LogRecord record = updateRecord(0x28, 0);
        byte[] buffer = record.getBuffer();
        LittleEndian.setInt16(buffer, record.getOffset() + 0x34, 0x28);
        LittleEndian.setInt16(buffer, record.getOffset() + 0x36, 0x278);

        // Act + assert
        assertThat(record.getRedoData().length, is(0));
    }

    @Test
    public void testRedoAndUndoDataInsideTheRecordAreReturned() {
        // Arrange
        LogRecord record = updateRecord(0x38, 0);
        byte[] buffer = record.getBuffer();
        LittleEndian.setInt16(buffer, record.getOffset() + 0x34, 0x28);
        LittleEndian.setInt16(buffer, record.getOffset() + 0x36, 8);
        LittleEndian.setInt16(buffer, record.getOffset() + 0x38, 0x30);
        LittleEndian.setInt16(buffer, record.getOffset() + 0x3a, 8);
        LittleEndian.setInt64(buffer, record.getOffset() + 0x58, 0x0807060504030201L);
        LittleEndian.setInt64(buffer, record.getOffset() + 0x60, 0x1817161514131211L);

        // Act + assert
        assertThat(record.getRedoData(), is(new byte[] { 1, 2, 3, 4, 5, 6, 7, 8 }));
        assertThat(record.getUndoData(), is(new byte[] { 0x11, 0x12, 0x13, 0x14, 0x15, 0x16, 0x17, 0x18 }));
    }

    @Test
    public void testACheckpointRecordHasNoRedoOrUndoData() {
        // Arrange: the bytes at the redo and undo fields are part of the restart area
        LogRecord record = updateRecord(0x70, 0);
        byte[] buffer = record.getBuffer();
        LittleEndian.setInt32(buffer, record.getOffset() + 0x20, LogRecord.RECORD_TYPE_CHECKPOINT);
        LittleEndian.setInt16(buffer, record.getOffset() + 0x34, 0x08);
        LittleEndian.setInt16(buffer, record.getOffset() + 0x36, 0x10);

        // Act + assert
        assertThat(record.getRedoData().length, is(0));
        assertThat(record.getUndoData().length, is(0));
    }

    @Test
    public void testCheckpointRecordIsDecodedAsARestartArea() {
        // Arrange: a type 2 record with 112 bytes of client data, as Windows writes them
        LogRecord record = updateRecord(0x70, 0);
        byte[] buffer = record.getBuffer();
        int clientData = record.getOffset() + 0x30;
        LittleEndian.setInt32(buffer, record.getOffset() + 0x20, LogRecord.RECORD_TYPE_CHECKPOINT);
        LittleEndian.setInt32(buffer, clientData, 1);
        LittleEndian.setInt64(buffer, clientData + 0x08, 1075373);
        LittleEndian.setInt64(buffer, clientData + 0x10, 1075384);
        LittleEndian.setInt64(buffer, clientData + 0x18, 1075518);
        LittleEndian.setInt64(buffer, clientData + 0x20, 1075551);
        LittleEndian.setInt32(buffer, clientData + 0x30, 984);
        LittleEndian.setInt32(buffer, clientData + 0x34, 174);
        LittleEndian.setInt32(buffer, clientData + 0x38, 1304);

        // Act
        CheckpointRecord checkpoint = record.getCheckpoint();

        // Assert
        assertThat(checkpoint.getMajorVersion(), is(1L));
        assertThat(checkpoint.getStartOfCheckpointLsn(), is(1075373L));
        assertThat(checkpoint.getOpenAttributeTableLsn(), is(1075384L));
        assertThat(checkpoint.getAttributeNamesLsn(), is(1075518L));
        assertThat(checkpoint.getDirtyPageTableLsn(), is(1075551L));
        assertThat(checkpoint.getTransactionTableLsn(), is(0L));
        assertThat(checkpoint.getOpenAttributeTableLength(), is(984L));
        assertThat(checkpoint.getAttributeNamesLength(), is(174L));
        assertThat(checkpoint.getDirtyPageTableLength(), is(1304L));
        assertThat(checkpoint.getTransactionTableLength(), is(0L));

        // ... and it has none of an update record's payloads
        assertThat(record.isUpdateRecord(), is(false));
        assertThat(record.isRedoDataInRecord(), is(false));
        assertThat(record.isUndoDataInRecord(), is(false));
        assertThat(record.hasTargetLcn(), is(false));
    }

    @Test
    public void testUpdateRecordHasNoCheckpoint() {
        assertThat(updateRecord(0x70, 0).getCheckpoint(), is(nullValue()));
    }
}
