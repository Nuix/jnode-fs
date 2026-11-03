package org.jnode.fs.ntfs.logfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.MasterFileTable;
import org.jnode.fs.ntfs.NTFSFileSystem;
import org.jnode.fs.ntfs.NTFSFileSystemType;
import org.jnode.fs.service.FileSystemService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Parses the $LogFile of a real volume.
 *
 * <p>{@link LogRecord} is otherwise covered only against hand-built buffers, and {@link LogFile} - which walks the
 * pages, follows records from one to the next and copes with the log wrapping - not at all. The journal on
 * {@code complex-compression.dd} holds 28,363 records, 614 of which run past the end of their page, so it
 * exercises both.</p>
 *
 * <p>A record that crosses a page continues in the <em>data area</em> of the next page, after that page's
 * {@link RecordPageHeader}. Mapping the offset without allowing for that header lands on the header itself, and
 * the payload comes back starting with its {@code RCRD} magic: 39 of the records here did exactly that before it
 * was fixed.</p>
 */
public class NTFSLogFileTest {

    private static final String IMAGE = "org/jnode/fs/ntfs/complex-compression.dd";

    /** The magic every log record page starts with, which must never appear at the head of a payload. */
    private static final byte[] PAGE_MAGIC = {'R', 'C', 'R', 'D'};

    private static TestImageDevice device;
    private static LogFile logFile;
    private static List<LogRecord> records;

    @BeforeClass
    public static void setUpClass() throws Exception {
        FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
        device = FileSystemTestUtils.openImage(IMAGE);
        NTFSFileSystem fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);

        logFile = new LogFile(fs.getNTFSVolume().getMFT().getRecord(MasterFileTable.SystemFiles.LOGFILE));
        records = new ArrayList<>(logFile.getLogRecords());
    }

    @AfterClass
    public static void tearDownClass() {
        records = null;
        logFile = null;
        if (device != null) {
            device.close();
        }
    }

    @Test
    public void testTheJournalParses() {
        assertThat("the volume was cleanly unmounted", logFile.isCleanlyShutdown(), is(true));
        assertThat("records", records.size(), is(28363));
        assertThat("the LSN map should agree with the record list",
            logFile.getLsnLogRecordMap().size(), is(records.size()));

        for (LogRecord record : records) {
            assertThat("every parsed record should be valid", record.isValid(), is(true));
            assertThat("every record should have an LSN", record.getLsn(), is(greaterThan(0L)));
        }
    }

    /**
     * The payload of a record that crosses a page must come from the next page's data area, never from its header.
     */
    @Test
    public void testPageCrossingRedoDataSkipsTheNextPageHeader() {
        int crossing = 0;
        int withRedoData = 0;

        for (LogRecord record : records) {
            if (!record.getCrossesPage()) {
                continue;
            }

            crossing++;
            int length = record.getRedoLength();
            if (length < PAGE_MAGIC.length || length > 0x10000) {
                continue;
            }

            withRedoData++;
            byte[] redo = new byte[length];
            record.getRedoData(redo);

            assertThat("the redo data of record " + record.getLsn() + " starts with the page magic, so it was read "
                + "from the page header rather than from the data area after it",
                startsWithPageMagic(redo), is(false));
        }

        assertThat("records crossing a page", crossing, is(614));
        assertThat("of those, ones with redo data", withRedoData, is(553));
    }

    /**
     * Checks whether a payload begins with the log record page magic.
     *
     * @param data the payload.
     * @return {@code true} if it starts with the magic.
     */
    private static boolean startsWithPageMagic(byte[] data) {
        for (int i = 0; i < PAGE_MAGIC.length; i++) {
            if (data[i] != PAGE_MAGIC[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * The operation codes are read through the same page mapping as the payload. A handful at the stale tail of the
     * journal are not recognised, which is expected; the rest must decode.
     */
    @Test
    public void testOperationCodesDecode() {
        int unrecognised = 0;

        for (LogRecord record : records) {
            if (OperationCode.fromCode(record.getRedoOperation()) == null
                || OperationCode.fromCode(record.getUndoOperation()) == null) {
                unrecognised++;
            }
        }

        assertThat("records with an unrecognised operation code", unrecognised, is(15));
    }

    /**
     * A sanity check on the collection as a whole, so that a parser change that silently drops records shows up.
     */
    @Test
    public void testCheckpointRecordsArePresent() {
        Collection<LogRecord> all = logFile.getLogRecords();
        int checkpoints = 0;

        for (LogRecord record : all) {
            if (record.getRecordType() == LogRecord.RECORD_TYPE_CHECKPOINT) {
                checkpoints++;
            }
        }

        assertThat("checkpoint records", checkpoints, is(greaterThan(0)));
    }
}
