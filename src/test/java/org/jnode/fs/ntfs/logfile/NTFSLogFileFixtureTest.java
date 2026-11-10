package org.jnode.fs.ntfs.logfile;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jnode.fs.FileSystemTestUtils;
import org.junit.Assume;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isOneOf;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Walks real $LogFile streams and compares the records with the expected list from a model of the walk. The fixtures
 * and that list are described in {@code SOURCE.md} beside them.
 */
public class NTFSLogFileFixtureTest {

    private static final String DIRECTORY = "org/jnode/fs/ntfs/logfile/";

    /**
     * The log page size of every fixture here.
     */
    private static final int LOG_PAGE_SIZE = 0x1000;

    /**
     * The DFIR-ORC test volume. v2.0.1 lost the 46 records with LSNs 1,075,384-1,077,223 after the
     * garbage length 0xF29FF5C4 at page 50 + 704.
     */
    @Test
    public void testDfirOrc() throws Exception {
        Map<Long, LogRecord> records = walk("dfir-orc");

        int inRange = 0;
        for (long lsn : records.keySet()) {
            if (lsn >= 1075384 && lsn <= 1077223) {
                inRange++;
            }
        }
        assertThat(inRange, is(46));
        assertThat(records.size(), is(1173));
    }

    /**
     * Windows 11 volume, from SecurityRonin. 216 of its records are only in the buffer pages 2-33, which v2.0.1 either
     * skipped or read in place.
     */
    @Test
    public void testWindows11BufferPages() throws Exception {
        Map<Long, LogRecord> records = walk("win11-reparse");

        int fromBuffer = 0;
        for (LogRecord record : records.values()) {
            if (record.getOffset() != record.getHomeOffset()) {
                assertThat(record.getOffset() / LOG_PAGE_SIZE < LogFile.BUFFER_AREA_END, is(true));
                fromBuffer++;
            }
        }
        assertThat(fromBuffer, is(216));
        assertThat(records.size(), is(479));
    }

    /**
     * The LogFileParser sample: an older layout with only two tail pages.
     */
    @Test
    public void testSampleTinyVolume() throws Exception {
        assertThat(walk("sample-tiny").size(), is(791));
    }

    /**
     * The log mkntfs leaves: all 0xFF. v2.0.1 threw "Fix-up error" because it applied the fix-up before checking the
     * magic.
     */
    @Test
    public void testBlankLog() throws Exception {
        LogFile logFile = new LogFile(FileSystemTestUtils.readImage(DIRECTORY + "tiny-blank.LogFile"));

        assertThat(logFile.isInitialised(), is(false));
        assertThat(logFile.getLogRecords().size(), is(0));
    }

    /**
     * Checkpoint records hold a restart area, not update-record fields, and some update records claim redo data that
     * runs past their client data. Neither may yield a payload.
     */
    @Test
    public void testDfirOrcCheckpointsAndPayloadBounds() throws Exception {
        Map<Long, LogRecord> records = walk("dfir-orc");

        int checkpoints = 0;
        int outsideTheRecord = 0;
        for (LogRecord record : records.values()) {
            if (record.getRecordType() == LogRecord.RECORD_TYPE_CHECKPOINT) {
                checkpoints++;
                CheckpointRecord checkpoint = record.getCheckpoint();
                assertThat(checkpoint, is(notNullValue()));
                assertThat(checkpoint.getMajorVersion(), is(1L));
                assertThat(checkpoint.getStartOfCheckpointLsn() <= record.getLsn(), is(true));
                assertThat(record.isRedoDataInRecord() || record.isUndoDataInRecord(), is(false));
            } else if (!record.isRedoDataInRecord() || !record.isUndoDataInRecord()) {
                outsideTheRecord++;
            }
        }

        assertThat(checkpoints, is(52));
        assertThat(outsideTheRecord, is(7));
    }

    /**
     * Records the walk drops can still be named by another record's previous or undo-next LSN.
     */
    @Test
    public void testDfirOrcChainsDumpWithoutMissingRecords() throws Exception {
        LogFile logFile = new LogFile(FileSystemTestUtils.readImage(DIRECTORY + "dfir-orc.LogFile"));

        for (long lsn : logFile.getLsnLogRecordMap().keySet()) {
            assertThat(logFile.dumpLogChain(lsn), is(notNullValue()));
        }
        assertThat(logFile.dumpLogChain(1), is("[]"));
    }

    /**
     * Restoring v1.0.1's {@code (int)} cast on the record length reads 108,460 records here, 2,182 fewer than v2.0.1's
     * 110,641. The log is 18 MB, so it isn't committed: it is
     * {@code Windows/NTFSArtifacts/LogFile/Win2012R2/StolenSzechuan/$LogFile} from
     * https://github.com/AndrewRathbun/DFIRArtifactMuseum (MIT). Pass its path in
     * {@code -Djnode.test.logfile.szechuan=...} to run this.
     */
    @Test
    public void testStolenSzechuan() throws Exception {
        String path = System.getProperty("jnode.test.logfile.szechuan");
        Assume.assumeTrue("set jnode.test.logfile.szechuan to run", path != null && new File(path).isFile());

        Map<Long, LogRecord> records = new LogFile(Files.readAllBytes(new File(path).toPath())).getLsnLogRecordMap();

        assertThat(records.size(), is(114483));
    }

    /**
     * Walks a fixture and checks that its records are exactly the expected list, each at its expected offset, and that
     * a crossing record's bytes are joined without the next page's header.
     */
    private static Map<Long, LogRecord> walk(String name) throws IOException {
        byte[] buffer = FileSystemTestUtils.readImage(DIRECTORY + name + ".LogFile");
        Map<Long, LogRecord> records = new LogFile(buffer).getLsnLogRecordMap();
        Map<Long, Long> expected = readExpected(DIRECTORY + name + ".LogFile.lsn");

        for (Map.Entry<Long, Long> entry : expected.entrySet()) {
            LogRecord record = records.get(entry.getKey());
            assertThat("record " + entry.getKey(), record, is(notNullValue()));
            assertThat("record " + entry.getKey(), record.getHomeOffset(), is(entry.getValue()));
        }
        assertThat("records", records.size(), is(expected.size()));

        for (LogRecord record : records.values()) {
            assertThat(record.getRecordType(), isOneOf(1L, 2L));

            byte[] bytes = record.getRecordBytes();
            assertThat(bytes.length, is((int) (LogRecord.LENGTH_CALCULATION_OFFSET + record.getClientDataLength())));
            assertThat("record " + record.getLsn() + " contains a page header",
                new String(bytes, StandardCharsets.ISO_8859_1).contains("RCRD"), is(false));
        }

        return records;
    }

    private static Map<Long, Long> readExpected(String path) throws IOException {
        Map<Long, Long> expected = new LinkedHashMap<Long, Long>();
        String text = new String(FileSystemTestUtils.readImage(path), StandardCharsets.US_ASCII);
        for (String line : text.split("\n")) {
            if (!line.isEmpty()) {
                String[] fields = line.split("\t");
                expected.put(Long.parseLong(fields[0]), Long.parseLong(fields[1]));
            }
        }
        return expected;
    }
}
