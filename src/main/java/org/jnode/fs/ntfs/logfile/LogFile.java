package org.jnode.fs.ntfs.logfile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jnode.fs.ntfs.FileRecord;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.util.LittleEndian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * $LogFile
 *
 * <p>Records are found by scanning every record page on its own, not by following record lengths from page to page.
 * A record's LSN encodes the file offset it was written at: {@code (lsn << seqBits) >>> (seqBits - 3)}. A position
 * is accepted as a record only when the LSN there encodes that position, the record type is 1 or 2 and the length is
 * less than the log. One garbage length can then only affect the rest of its own page.</p>
 *
 * <p>Logs written by Windows 8 and later keep a buffer of page copies in pages 2-33; older logs keep two tail copies
 * in pages 2-3. A copy holds records whose LSNs encode a page in the circular area. They are reported at that home
 * offset (see {@link LogRecord#getHomeOffset()}), and a record found both in a copy and at home is reported once. A
 * record that crosses into the next page continues in a copy of the next home page written in the same pass through
 * the log; when no such copy exists the record is incomplete, so it is dropped.</p>
 *
 * @author Luke Quinane
 */
public class LogFile {

    /**
     * My logger
     */
    protected static final Logger log = LoggerFactory.getLogger(LogFile.class);

    /**
     * The start of the normal area (in pages) on logs that only keep two tail copies.
     */
    public static final int NORMAL_AREA_START = 4;

    /**
     * The first page past the page-copy buffer on logs written by Windows 8 and later.
     */
    static final int BUFFER_AREA_END = 34;

    /**
     * The first page that can hold record pages; pages 0 and 1 are the restart pages.
     */
    private static final int FIRST_RECORD_PAGE = 2;

    /**
     * The list of open log clients.
     */
    private final List<LogClientRecord> logClients = new ArrayList<LogClientRecord>();

    /**
     * The record page headers, indexed by page number. {@code null} for a page without the RCRD magic.
     */
    private final RecordPageHeader[] pageHeaders;

    /**
     * The map of LSN to log record, in LSN order.
     */
    private Map<Long, LogRecord> lsnLogRecordMap;

    /**
     * The restart page header.
     */
    private final RestartPageHeader restartPageHeader;

    /**
     * The restart area.
     */
    private final RestartArea restartArea;

    /**
     * The $LogFile page size.
     */
    private final int logPageSize;

    /**
     * The $LogFile size.
     */
    private final long logFileLength;

    /**
     * The buffer that holds the $LogFile data.
     */
    private final byte[] logFileBuffer;

    /**
     * Indicates whether the $LogFile contents need to be checked when reading MFT data.
     */
    private boolean cleanlyShutdown = true;

    /**
     * Creates a new instance.
     *
     * @param fileRecord the file record to read the $LogFile data from.
     * @throws IOException if an error occurs.
     */
    public LogFile(FileRecord fileRecord) throws IOException {
        this(readLogFile(fileRecord));
    }

    /**
     * Creates a new instance from the contents of a $LogFile.
     *
     * <p>Fix-ups are applied to the buffer in place.</p>
     *
     * @param logFileBuffer the $LogFile data.
     * @throws IOException if an error occurs.
     */
    public LogFile(byte[] logFileBuffer) throws IOException {
        this.logFileBuffer = logFileBuffer;
        logFileLength = logFileBuffer.length;

        // Read in the restart area info
        restartPageHeader = getNewestRestartPageHeader(logFileBuffer);
        int restartAreaOffset = restartPageHeader.getOffset() + restartPageHeader.getRestartOffset();
        logPageSize = restartPageHeader.getLogPageSize();
        restartArea = new RestartArea(logFileBuffer, restartAreaOffset);

        if ((restartArea.getFlags() & RestartArea.VOLUME_CLEANLY_UNMOUNTED) != RestartArea.VOLUME_CLEANLY_UNMOUNTED) {
            log.info("Volume not cleanly unmounted");
            cleanlyShutdown = false;

        } else {
            log.info("Volume marked as cleanly unmounted");
        }

        // Read in any open log client records
        int logClientCount = restartArea.getLogClients();
        if (logClientCount != RestartArea.LOGFILE_NO_CLIENT) {
            log.info(String.format("Found %d open log clients", logClientCount));

            int logClientOffset = restartAreaOffset + restartArea.getClientArrayOffset();
            LogClientRecord logClientRecord = new LogClientRecord(logFileBuffer, logClientOffset);
            logClients.add(logClientRecord);

            for (int i = 1; i <= logClientCount; i++) {
                logClientOffset = restartAreaOffset + logClientRecord.getNextClientOffset();
                logClientRecord = new LogClientRecord(logFileBuffer, logClientOffset);
                logClients.add(logClientRecord);
            }
        }

        pageHeaders = readPageHeaders();
    }

    /**
     * Reads the $DATA of the $LogFile file record.
     *
     * @param fileRecord the file record.
     * @return the data.
     * @throws IOException if an error occurs.
     */
    private static byte[] readLogFile(FileRecord fileRecord) throws IOException {
        int length = (int) fileRecord.getAttributeTotalSize(NTFSAttribute.Types.DATA, null);
        byte[] buffer = new byte[length];
        fileRecord.readData(0, buffer, 0, length);
        return buffer;
    }

    /**
     * Reads the header of every record page, which also applies each page's fix-up.
     *
     * @return the headers, indexed by page number.
     * @throws IOException if an error occurs.
     */
    private RecordPageHeader[] readPageHeaders() throws IOException {
        RecordPageHeader[] headers = new RecordPageHeader[(int) (logFileLength / logPageSize)];

        for (int page = FIRST_RECORD_PAGE; page < headers.length; page++) {
            int offset = page * logPageSize;
            if (LittleEndian.getInt32(logFileBuffer, offset) == RecordPageHeader.Magic.RCRD) {
                headers[page] = new RecordPageHeader(logFileBuffer, offset);
            }
        }

        return headers;
    }

    /**
     * Parses the log records.
     */
    private void parseRecords() {
        if (lsnLogRecordMap != null) {
            // Already parsed
            return;
        }

        lsnLogRecordMap = new TreeMap<Long, LogRecord>();

        long seqBits = restartArea.getSequenceNumberBits();
        if (seqBits < 3 || seqBits > 63) {
            log.warn("$LogFile restart area has an invalid sequence number bits value: " + seqBits);
            return;
        }

        PageScan scan = new PageScan((int) seqBits);
        scan.scanPages();

        int dropped = 0;
        for (Map.Entry<Long, Placement> entry : scan.placements.entrySet()) {
            LogRecord record = scan.toLogRecord(entry.getKey(), entry.getValue());
            if (record == null) {
                dropped++;
            } else {
                lsnLogRecordMap.put(entry.getKey(), record);
            }
        }

        if (dropped > 0) {
            log.info(String.format("Dropped %d $LogFile records whose continuation was overwritten", dropped));
        }
    }

    /**
     * Where a record was found.
     */
    private static final class Placement {
        /**
         * The position of the record's bytes in the buffer.
         */
        final int position;

        /**
         * The offset its LSN encodes.
         */
        final long homeOffset;

        Placement(int position, long homeOffset) {
            this.position = position;
            this.homeOffset = homeOffset;
        }

        boolean isAtHome() {
            return position == homeOffset;
        }
    }

    /**
     * One pass over the record pages.
     */
    private final class PageScan {

        private final int seqBits;

        /**
         * The LSN increment from one lap of the circular area to the next.
         */
        private final long lapLength;

        private final int pageCount;

        private final int dataOffset;

        /**
         * The page the circular area wraps back to: 34 when pages 4-33 are a buffer, otherwise 4.
         */
        private final int firstCircularPage;

        final Map<Long, Placement> placements = new HashMap<Long, Placement>();

        /**
         * The pages holding copies of each home page, other than the home page itself.
         */
        private final Map<Integer, List<Integer>> copiesByHomePage = new HashMap<Integer, List<Integer>>();

        PageScan(int seqBits) {
            this.seqBits = seqBits;
            lapLength = 1L << (64 - seqBits);
            pageCount = pageHeaders.length;
            dataOffset = restartArea.getLogPageDataOffset();
            firstCircularPage = findFirstCircularPage();
        }

        /**
         * Gets the offset into the log file that an LSN encodes.
         */
        long homeOffset(long lsn) {
            return (lsn << seqBits) >>> (seqBits - 3);
        }

        /**
         * Decides whether pages 4-33 are a buffer of page copies. A copy's first record is away from its home, while
         * a circular-area page's first record is at home. The restart area version doesn't tell: cleanly unmounted
         * Windows 8+ volumes write v1.1 and still use the buffer.
         */
        private int findFirstCircularPage() {
            int atHome = 0;
            int away = 0;

            for (int page = NORMAL_AREA_START; page < Math.min(BUFFER_AREA_END, pageCount); page++) {
                if (pageHeaders[page] == null) {
                    continue;
                }

                int pageStart = page * logPageSize;
                long lastLsn = pageHeaders[page].getLastLsnOrFileOffset();

                for (int pos = dataOffset; pos + LogRecord.LENGTH_CALCULATION_OFFSET <= logPageSize; pos += 8) {
                    long lsn = LittleEndian.getInt64(logFileBuffer, pageStart + pos);
                    long home = homeOffset(lsn);

                    if (lsn > 0 && lsn <= lastLsn && home % logPageSize == pos
                        && isRecordType(LittleEndian.getUInt32(logFileBuffer, pageStart + pos + 0x20))) {
                        if (home / logPageSize == page) {
                            atHome++;
                        } else {
                            away++;
                        }
                        break;
                    }
                }
            }

            return away > atHome ? BUFFER_AREA_END : NORMAL_AREA_START;
        }

        void scanPages() {
            for (int page = FIRST_RECORD_PAGE; page < pageCount; page++) {
                if (pageHeaders[page] != null) {
                    scanPage(page);
                }
            }
        }

        /**
         * Scans one page for records at every 8-byte position from the data offset.
         *
         * <p>In the circular area a record must sit exactly at the offset its LSN encodes. In pages 2-33 the first
         * record may be a copy from elsewhere, as long as its in-page position matches; the rest of the page must then
         * belong to the same home page.</p>
         */
        private void scanPage(int page) {
            int pageStart = page * logPageSize;
            long lastLsn = pageHeaders[page].getLastLsnOrFileOffset();
            boolean mayBeCopy = page < NORMAL_AREA_START || (page < BUFFER_AREA_END && !hasRecordAtHome(page));
            int homePage = -1;
            int pos = dataOffset;

            while (pos + LogRecord.LENGTH_CALCULATION_OFFSET <= logPageSize) {
                int position = pageStart + pos;
                long lsn = LittleEndian.getInt64(logFileBuffer, position);

                if (lsn > 0 && lsn <= lastLsn) {
                    long home = homeOffset(lsn);
                    boolean placed;
                    if (homePage < 0) {
                        placed = home == position
                            || (mayBeCopy && home % logPageSize == pos
                            && home >= (long) NORMAL_AREA_START * logPageSize && home < logFileLength);
                    } else {
                        placed = home / logPageSize == homePage && home % logPageSize == pos;
                    }

                    long recordType = LittleEndian.getUInt32(logFileBuffer, position + 0x20);
                    long clientDataLength = LittleEndian.getUInt32(logFileBuffer, position + 0x18);

                    if (placed && isRecordType(recordType) && clientDataLength < logFileLength) {
                        homePage = (int) (home / logPageSize);

                        Placement previous = placements.get(lsn);
                        if (previous == null || (!previous.isAtHome() && position == home)) {
                            placements.put(lsn, new Placement(position, home));
                        }

                        long end = pos + LogRecord.LENGTH_CALCULATION_OFFSET + clientDataLength;
                        if (end > logPageSize) {
                            // The record runs on to the next page, so nothing else starts on this one
                            break;
                        }

                        pos = (int) ((end + 7) & ~7L);
                        continue;
                    }
                }

                pos += 8;
            }

            if (homePage >= 0 && homePage != page) {
                List<Integer> copies = copiesByHomePage.get(homePage);
                if (copies == null) {
                    copies = new ArrayList<Integer>();
                    copiesByHomePage.put(homePage, copies);
                }
                copies.add(page);
            }
        }

        /**
         * Checks whether a page holds a record at the offset its LSN encodes. A copy never does, since its records'
         * home is another page, so such a page in 4-33 is part of the circular area. Before its first record it can
         * hold the end of a record from the previous page, whose bytes must not be taken for a copy.
         */
        private boolean hasRecordAtHome(int page) {
            int pageStart = page * logPageSize;
            long lastLsn = pageHeaders[page].getLastLsnOrFileOffset();

            for (int pos = dataOffset; pos + LogRecord.LENGTH_CALCULATION_OFFSET <= logPageSize; pos += 8) {
                int position = pageStart + pos;
                long lsn = LittleEndian.getInt64(logFileBuffer, position);
                if (lsn > 0 && lsn <= lastLsn && homeOffset(lsn) == position
                    && isRecordType(LittleEndian.getUInt32(logFileBuffer, position + 0x20))
                    && LittleEndian.getUInt32(logFileBuffer, position + 0x18) < logFileLength) {
                    return true;
                }
            }

            return false;
        }

        /**
         * Builds the log record for a placement, resolving the pages a crossing record continues in.
         *
         * @return the record, or {@code null} if a continuation page was overwritten in a later lap.
         */
        LogRecord toLogRecord(long lsn, Placement placement) {
            int posInPage = placement.position % logPageSize;
            long clientDataLength = LittleEndian.getUInt32(logFileBuffer, placement.position + 0x18);
            long remaining = posInPage + LogRecord.LENGTH_CALCULATION_OFFSET + clientDataLength - logPageSize;

            List<Integer> continuation = new ArrayList<Integer>();
            int homePage = (int) (placement.homeOffset / logPageSize);

            while (remaining > 0) {
                homePage++;
                if (homePage >= pageCount) {
                    homePage = firstCircularPage;
                }

                int copy = findSameLapCopy(homePage, lsn);
                if (copy < 0) {
                    log.debug(String.format("$LogFile record %d continues in page %d, which is from another lap",
                        lsn, homePage));
                    return null;
                }

                continuation.add(copy * logPageSize);
                remaining -= logPageSize - dataOffset;
            }

            int[] continuationPages = new int[continuation.size()];
            for (int i = 0; i < continuationPages.length; i++) {
                continuationPages[i] = continuation.get(i);
            }

            return new LogRecord(logFileBuffer, placement.position, logPageSize, dataOffset, placement.homeOffset,
                continuationPages);
        }

        /**
         * Finds the newest copy of a home page written in the same pass as a record. One pass through the circular
         * area advances the LSN by exactly one lap, so such a copy has a last LSN from the record's LSN to less than
         * a lap past it, including across the wrap. A copy rewritten in the next pass is more than a lap past it.
         *
         * @return the page number, or -1 if there is none.
         */
        private int findSameLapCopy(int homePage, long lsn) {
            List<Integer> candidates = new ArrayList<Integer>();
            candidates.add(homePage);
            List<Integer> copies = copiesByHomePage.get(homePage);
            if (copies != null) {
                candidates.addAll(copies);
            }

            int best = -1;
            long bestLastLsn = Long.MIN_VALUE;
            for (int page : candidates) {
                if (pageHeaders[page] == null) {
                    continue;
                }

                long lastLsn = pageHeaders[page].getLastLsnOrFileOffset();
                if (lastLsn >= lsn && lastLsn - lsn < lapLength && lastLsn > bestLastLsn) {
                    best = page;
                    bestLastLsn = lastLsn;
                }
            }

            return best;
        }
    }

    /**
     * Checks for the two record types: a client record (1) or a checkpoint (2).
     */
    private static boolean isRecordType(long recordType) {
        return recordType == 1 || recordType == LogRecord.RECORD_TYPE_CHECKPOINT;
    }

    /**
     * Gets the restart page header that corresponds to the restart page with the highest current LSN.
     *
     * @param buffer the buffer to read from.
     * @return the header.
     * @throws IOException if an error occurs.
     */
    private RestartPageHeader getNewestRestartPageHeader(byte[] buffer) throws IOException {
        RestartPageHeader restartPageHeader1 = new RestartPageHeader(buffer, 0);
        if (!restartPageHeader1.isValid()) {
            throw new IllegalStateException("Restart header has invalid magic: " + restartPageHeader1.getMagic());
        } else if (restartPageHeader1.getMagic() == RestartPageHeader.Magic.CHKD) {
            log.warn("First $LogFile restart header has check disk magic");
        }

        RestartPageHeader restartPageHeader2 = new RestartPageHeader(buffer, restartPageHeader1.getLogPageSize());
        if (!restartPageHeader2.isValid()) {
            throw new IllegalStateException("Second restart header has invalid magic: " + restartPageHeader2.getMagic());
        }  else if (restartPageHeader2.getMagic() == RestartPageHeader.Magic.CHKD) {
            log.warn("Second $LogFile restart header has check disk magic");
        }

        int restartAreaOffset1 = restartPageHeader1.getRestartOffset();
        int restartAreaOffset2 = restartPageHeader2.getRestartOffset();
        RestartArea restartArea1 = new RestartArea(buffer, restartAreaOffset1);
        RestartArea restartArea2 = new RestartArea(buffer, restartPageHeader1.getLogPageSize() + restartAreaOffset2);

        // Pick the restart page with the highest current LSN
        if (restartArea1.getCurrentLsn() >= restartArea2.getCurrentLsn()) {
            return restartPageHeader1;
        } else {
            return restartPageHeader2;
        }
    }

    /**
     * Checks whether the log file seems to be cleanly shutdown.
     *
     * @return {@code true} if cleanly shutdown.
     */
    public boolean isCleanlyShutdown() {
        return cleanlyShutdown;
    }

    /**
     * Gets the log file records for this log file, in LSN order.
     *
     * @return the records.
     */
    public Collection<LogRecord> getLogRecords() {
        parseRecords();
        return lsnLogRecordMap.values();
    }

    /**
     * Gets a mapping of LSN to log record, in LSN order.
     *
     * @return the map.
     */
    public Map<Long, LogRecord> getLsnLogRecordMap() {
        parseRecords();
        return Collections.unmodifiableMap(lsnLogRecordMap);
    }

    /**
     * Dumps out a chain of log records.
     *
     * @param lsn the LSN to start from.
     * @return the dumped out chain.
     */
    public String dumpLogChain(long lsn) {
        parseRecords();
        List<LogRecord> records = new ArrayList<LogRecord>();
        LogRecord midRecord = lsnLogRecordMap.get(lsn);
        records.add(midRecord);

        LogRecord current = midRecord;
        while (current.getClientPreviousLsn() != 0) {
            current = lsnLogRecordMap.get(current.getClientPreviousLsn());
            records.add(0, current);
        }

        int midIndex = records.size() - 1;
        current = midRecord;
        while (current.getClientUndoNextLsn() != 0) {
            current = lsnLogRecordMap.get(current.getClientUndoNextLsn());
            records.add(current);
        }

        StringBuilder builder = new StringBuilder();
        builder.append("[");
        for (int i = 0; i < records.size(); i++) {
            LogRecord record = records.get(i);

            if (i < midIndex) {
                builder.append("<");
            } else if (i == midIndex) {
                builder.append("=");
            } else {
                builder.append(">");
            }

            builder.append(record);
            builder.append("\n");
        }
        builder.append("]");
        return builder.toString();
    }
}
