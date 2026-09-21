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
     * The same for the 32-bit fields.
     */
    @Test
    public void testGetUInt32AcrossPages_whenTheFieldIsOnTheNextPage() {
        // Arrange: the target LCN at 0x50 lands on the next page
        int recordOffset = 2 * PAGE_SIZE - 0x50;
        byte[] buffer = logPages(recordOffset);
        LittleEndian.setInt32(buffer, 2 * PAGE_SIZE + LOG_PAGE_DATA_OFFSET, 0xDEADBEEF);

        LogRecord record = new LogRecord(buffer, recordOffset, PAGE_SIZE, LOG_PAGE_DATA_OFFSET);

        // Assert
        assertThat(record.getTargetLcn(), is(0xDEADBEEFL));
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
}
