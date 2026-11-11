package org.jnode.fs.ntfs.logfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.jnode.fs.ntfs.logfile.LogFileBuilder.DATA_OFFSET;
import static org.jnode.fs.ntfs.logfile.LogFileBuilder.PAGE_SIZE;

/**
 * Tests the $LogFile page walk against hand-built logs.
 */
public class LogFileWalkTest {

    /**
     * 64 pages with a lap of twice the log, as Windows sizes it.
     */
    private static final int PAGES = 64;
    private static final int SEQ_BITS = 48;

    private static final int[] SMALL_RECORDS = { 0x40, 0x80, 0x100, 0x38, 0x200, 0x60 };

    /**
     * Where {@link #SMALL_RECORDS} end when written from the data offset.
     */
    private static final int SMALL_RECORDS_END = 0x5b8;

    /**
     * A record written at {@link #SMALL_RECORDS_END} with this length runs 0x1e8 bytes into the next page.
     */
    private static final int CROSSING_LENGTH = 0xc00;
    private static final int AFTER_CONTINUATION = DATA_OFFSET + 0x1e8;

    @Test
    public void testGarbageLengthsDoNotLoseTheFollowingPages() throws Exception {
        // Arrange: three non-records after page 8's real records, with lengths seen on real logs
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> expected = new ArrayList<Long>();
        List<Long> following = new ArrayList<Long>();
        for (int page = 4; page <= 10; page++) {
            List<Long> lsns = builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS);
            expected.addAll(lsns);
            if (page > 8) {
                following.addAll(lsns);
            }
        }
        builder.garbage(8, SMALL_RECORDS_END, 160, 0xFFFFFFFFL, 0xFFFFFFFFL);
        builder.garbage(8, SMALL_RECORDS_END + 0x40, 161, 0x1CADAB20L, 0xFFFFFFFFL);
        builder.garbage(8, SMALL_RECORDS_END + 0x80, 162, 0x7FFFFFF8L, 0xFFFFFFFFL);

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        assertRecordsAtHome(builder, records, following);
        assertRecordsAtHome(builder, records, expected);
        assertThat(records.size(), is(expected.size()));
        assertOnlyRecordTypes(records);
    }

    @Test
    public void testAPageStartingMidRecordAfterALapBreak() throws Exception {
        // Arrange: pass 2 has reached page 9, pass 1 is still in pages 10-63. Page 4 starts with the end of a record
        // that crossed from page 63, whose bytes at the data offset read as an LSN and a 0xFFFFFFFF length.
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> expected = new ArrayList<Long>();
        for (int page = 10; page < PAGES; page++) {
            expected.addAll(builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS));
        }
        builder.garbage(4, DATA_OFFSET, 0x10, 0xFFFFFFFFL, 1);
        expected.addAll(builder.fillPage(4, 4, 2, 0x200, SMALL_RECORDS));
        List<Long> nextPage = builder.fillPage(5, 5, 2, DATA_OFFSET, SMALL_RECORDS);
        expected.addAll(nextPage);
        for (int page = 6; page <= 9; page++) {
            expected.addAll(builder.fillPage(page, page, 2, DATA_OFFSET, SMALL_RECORDS));
        }

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        assertRecordsAtHome(builder, records, nextPage);
        assertRecordsAtHome(builder, records, expected);
        assertThat(records.size(), is(expected.size()));
    }

    @Test
    public void testARecordCrossingTheWrapContinuesInTheFirstCircularPage() throws Exception {
        // Arrange: pass 1 fills pages 40-63, and its last record runs on into page 4, where pass 2 continues
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> expected = new ArrayList<Long>();
        for (int page = 40; page < PAGES; page++) {
            expected.addAll(builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS));
        }
        long crossing = builder.lsn(1, (PAGES - 1) * PAGE_SIZE + SMALL_RECORDS_END);
        builder.record(PAGES - 1, crossing, CROSSING_LENGTH, 4);
        expected.add(crossing);
        List<Long> afterTheWrap = builder.fillPage(4, 4, 2, AFTER_CONTINUATION, SMALL_RECORDS);
        expected.addAll(afterTheWrap);
        for (int page = 5; page < 40; page++) {
            expected.addAll(builder.fillPage(page, page, 2, DATA_OFFSET, SMALL_RECORDS));
        }

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        assertThat(records.get(crossing), is(notNullValue()));
        assertThat(records.get(crossing).getRecordBytes(),
            is(LogFileBuilder.recordBytes(crossing, CROSSING_LENGTH, true)));
        assertRecordsAtHome(builder, records, afterTheWrap);
        assertRecordsAtHome(builder, records, expected);
        assertThat(records.size(), is(expected.size()));
    }

    @Test
    public void testTheOldestPageIsRead() throws Exception {
        // Arrange
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> oldest = builder.fillPage(4, 4, 1, DATA_OFFSET, SMALL_RECORDS);
        for (int page = 5; page <= 10; page++) {
            builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS);
        }

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        assertRecordsAtHome(builder, records, oldest);
    }

    @Test
    public void testATailCopyIsReportedOnceAtItsHomeOffset() throws Exception {
        // Arrange: pass 2 has reached page 20, but only its copy in page 2 has been written; page 20 itself still
        // holds pass 1
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> older = new ArrayList<Long>();
        for (int page = 20; page < PAGES; page++) {
            older.addAll(builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS));
        }
        for (int page = 4; page < 20; page++) {
            builder.fillPage(page, page, 2, DATA_OFFSET, SMALL_RECORDS);
        }
        List<Long> newest = builder.fillPage(2, 20, 2, DATA_OFFSET, 0x40, 0x80);

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        for (long lsn : newest) {
            LogRecord record = records.get(lsn);
            assertThat("record " + lsn, record, is(notNullValue()));
            assertThat(record.getHomeOffset(), is(builder.homeOffset(lsn)));
            assertThat(record.getOffset() / PAGE_SIZE, is(2));
        }
        assertRecordsAtHome(builder, records, older);
    }

    @Test
    public void testACrossingRecordWhoseContinuationIsFromANewerPassIsDropped() throws Exception {
        // Arrange: a pass 1 record crosses from page 40 into page 41, but page 41 was rewritten in pass 2
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        for (int page = 4; page < 22; page++) {
            builder.fillPage(page, page, 2, DATA_OFFSET, SMALL_RECORDS);
        }
        for (int page = 22; page < PAGES; page++) {
            if (page != 40 && page != 41) {
                builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS);
            }
        }
        List<Long> before = builder.fillPage(40, 40, 1, DATA_OFFSET, SMALL_RECORDS);
        long crossing = builder.lsn(1, 40 * PAGE_SIZE + SMALL_RECORDS_END);
        builder.record(40, crossing, CROSSING_LENGTH, 41);
        builder.fillPage(41, 41, 2, AFTER_CONTINUATION, SMALL_RECORDS);

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        assertThat(records.get(crossing), is(nullValue()));
        assertRecordsAtHome(builder, records, before);
    }

    @Test
    public void testABufferCopyIsReportedAtItsHomeOffset() throws Exception {
        // Arrange: the velociraptor shape. Page 6 is a buffer copy of page 54 from a newer pass than page 54 holds.
        LogFileBuilder builder = new LogFileBuilder(PAGES, 44);
        for (int page = 34; page < PAGES; page++) {
            builder.fillPage(page, page, 0, DATA_OFFSET, SMALL_RECORDS);
        }
        List<Long> copied = builder.fillPage(6, 54, 1, DATA_OFFSET, 0x40, 0x40);

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        assertThat(copied.get(1), is(1076246L));
        LogRecord record = records.get(1076246L);
        assertThat(record, is(notNullValue()));
        assertThat(record.getHomeOffset(), is(221360L));
        assertThat(record.getOffset(), is(24752));
    }

    @Test
    public void testAContinuationThatLooksLikeACopyDoesNotHideACircularPage() throws Exception {
        // Arrange: an older layout, where pages 4-33 are circular. A record crosses from page 9 into page 10, and its
        // continuation holds bytes that read as a record whose LSN places it on page 5.
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> expected = new ArrayList<Long>();
        for (int page = 4; page <= 9; page++) {
            expected.addAll(builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS));
        }
        long crossing = builder.lsn(1, 9 * PAGE_SIZE + SMALL_RECORDS_END);
        builder.record(9, crossing, CROSSING_LENGTH, 10);
        expected.add(crossing);
        long lookalike = builder.lsn(1, 5 * PAGE_SIZE + 0x80);
        builder.garbage(10, 0x80, lookalike, 0x40, 1);
        List<Long> afterTheContinuation = builder.fillPage(10, 10, 1, AFTER_CONTINUATION, SMALL_RECORDS);
        expected.addAll(afterTheContinuation);

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        assertRecordsAtHome(builder, records, afterTheContinuation);
        assertThat(records.get(lookalike), is(nullValue()));
        assertRecordsAtHome(builder, records, expected);
        assertThat(records.size(), is(expected.size()));
    }

    @Test
    public void testAGarbageHeaderDoesNotReplaceARealRecordWithTheSameLsn() throws Exception {
        // Arrange
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> real = new ArrayList<Long>();
        for (int page = 4; page <= 10; page++) {
            real.addAll(builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS));
        }
        long victim = real.get(2 * SMALL_RECORDS.length);
        builder.garbage(9, SMALL_RECORDS_END, victim, 0x7FFFFFF8L, 0xFFFFFFFFL);

        // Act
        Map<Long, LogRecord> records = new LogFile(builder.build()).getLsnLogRecordMap();

        // Assert
        assertThat(records.get(victim).getOffset(), is((int) builder.homeOffset(victim)));
        assertThat(records.get(victim).getRecordType(), is(1L));
    }

    @Test
    public void testABlankLogHasNoRecords() throws Exception {
        // Arrange: all 0xFF, as ntfs-3g and mkntfs leave it
        byte[] buffer = new byte[PAGES * PAGE_SIZE];
        Arrays.fill(buffer, (byte) 0xFF);

        // Act
        LogFile logFile = new LogFile(buffer);

        // Assert
        assertThat(logFile.isInitialised(), is(false));
        assertThat(logFile.getLogRecords().size(), is(0));
    }

    @Test
    public void testATornPageLosesOnlyThatPage() throws Exception {
        // Arrange: page 7's third sector didn't make it to disk, so its fix-up doesn't match
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> kept = new ArrayList<Long>();
        List<Long> torn = new ArrayList<Long>();
        for (int page = 4; page <= 10; page++) {
            List<Long> lsns = builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS);
            (page == 7 ? torn : kept).addAll(lsns);
        }
        byte[] buffer = builder.build();
        tearSector(buffer, 7 * PAGE_SIZE, 3);

        // Act
        Map<Long, LogRecord> records = new LogFile(buffer).getLsnLogRecordMap();

        // Assert
        assertRecordsAtHome(builder, records, kept);
        assertThat(records.size(), is(kept.size()));
    }

    @Test
    public void testAnOversizedUpdateSequenceArrayLosesOnlyItsOwnPage() throws Exception {
        // Arrange: page 7 claims 64 fix-ups, which would run its fix-up on into pages 8-10
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> kept = new ArrayList<Long>();
        for (int page = 4; page <= 10; page++) {
            List<Long> lsns = builder.fillPage(page, page, 1, DATA_OFFSET, SMALL_RECORDS);
            if (page != 7) {
                kept.addAll(lsns);
            }
        }
        byte[] buffer = builder.build();
        buffer[7 * PAGE_SIZE + 6] = 0x40;

        // Act
        Map<Long, LogRecord> records = new LogFile(buffer).getLsnLogRecordMap();

        // Assert
        assertRecordsAtHome(builder, records, kept);
        assertThat(records.size(), is(kept.size()));
    }

    @Test
    public void testATornRestartPageFallsBackToTheOtherOne() throws Exception {
        // Arrange
        LogFileBuilder builder = new LogFileBuilder(PAGES, SEQ_BITS);
        List<Long> expected = builder.fillPage(4, 4, 1, DATA_OFFSET, SMALL_RECORDS);
        byte[] buffer = builder.build();
        tearSector(buffer, 0, 1);

        // Act
        LogFile logFile = new LogFile(buffer);

        // Assert
        assertThat(logFile.isInitialised(), is(true));
        assertRecordsAtHome(builder, logFile.getLsnLogRecordMap(), expected);
    }

    /**
     * Changes the update sequence number at the end of a sector so that the page's fix-up no longer matches.
     */
    private static void tearSector(byte[] buffer, int pageOffset, int sector) {
        buffer[pageOffset + sector * 512 - 2] ^= 0x7F;
    }

    private static void assertRecordsAtHome(LogFileBuilder builder, Map<Long, LogRecord> records, List<Long> lsns) {
        for (long lsn : lsns) {
            LogRecord record = records.get(lsn);
            assertThat("record " + lsn, record, is(notNullValue()));
            assertThat("record " + lsn, (long) record.getOffset(), is(builder.homeOffset(lsn)));
            assertThat("record " + lsn, record.getHomeOffset(), is(builder.homeOffset(lsn)));
        }
    }

    private static void assertOnlyRecordTypes(Map<Long, LogRecord> records) {
        List<Long> types = new ArrayList<Long>();
        for (LogRecord record : records.values()) {
            types.add(record.getRecordType());
        }
        assertThat(types, everyItem(isOneOf(1L, 2L)));
    }
}
