package org.jnode.fs.ntfs.logfile;

import java.util.ArrayList;
import java.util.List;
import org.jnode.util.LittleEndian;

/**
 * Builds a synthetic $LogFile: two restart pages and record pages with fix-ups applied.
 *
 * <p>Records are written at the in-page position their LSN encodes. A page's last LSN is the highest LSN of any record
 * written on it, including a record that only continues there, unless set explicitly.</p>
 */
class LogFileBuilder {

    static final int PAGE_SIZE = 0x1000;
    static final int DATA_OFFSET = 0x40;

    private static final int SECTOR_SIZE = 512;
    private static final int UPDATE_SEQUENCE_NUMBER = 0x0001;

    private final int seqBits;
    private final byte[] buffer;
    private final long[] pageLastLsn;
    private final int[] pageEnd;
    private final boolean[] isRecordPage;

    LogFileBuilder(int pageCount, int seqBits) {
        this.seqBits = seqBits;
        buffer = new byte[pageCount * PAGE_SIZE];
        pageLastLsn = new long[pageCount];
        pageEnd = new int[pageCount];
        isRecordPage = new boolean[pageCount];
    }

    /**
     * Gets the LSN for a record written at an offset in a given pass through the log.
     */
    long lsn(long pass, long offset) {
        return (pass << (64 - seqBits)) | (offset >>> 3);
    }

    /**
     * Gets the offset in the log that an LSN encodes.
     */
    long homeOffset(long lsn) {
        return (lsn << seqBits) >>> (seqBits - 3);
    }

    /**
     * Fills a page with back-to-back records from the data offset.
     *
     * @param physicalPage the page to write to.
     * @param homePage the page the records' LSNs encode; differs from the physical page for a copy.
     * @param pass the pass through the log.
     * @param startPosition the in-page position of the first record.
     * @param clientDataLengths the client data length of each record; none may cross the page.
     * @return the LSNs written.
     */
    List<Long> fillPage(int physicalPage, int homePage, long pass, int startPosition, int... clientDataLengths) {
        List<Long> lsns = new ArrayList<Long>();
        int position = startPosition;

        for (int length : clientDataLengths) {
            long lsn = lsn(pass, (long) homePage * PAGE_SIZE + position);
            record(physicalPage, lsn, length);
            lsns.add(lsn);
            position += (LogRecord.LENGTH_CALCULATION_OFFSET + length + 7) & ~7;
        }

        return lsns;
    }

    /**
     * Writes a record whose client data continues in the given pages when it does not fit in its own.
     */
    void record(int physicalPage, long lsn, int clientDataLength, int... continuationPages) {
        int position = (int) (homeOffset(lsn) % PAGE_SIZE);
        byte[] bytes = recordBytes(lsn, clientDataLength, continuationPages.length > 0);

        int page = physicalPage;
        int pos = position;
        int written = 0;
        int next = 0;
        while (written < bytes.length) {
            if (pos >= PAGE_SIZE) {
                page = continuationPages[next++];
                pos = DATA_OFFSET;
            }

            int chunk = Math.min(bytes.length - written, PAGE_SIZE - pos);
            System.arraycopy(bytes, written, buffer, page * PAGE_SIZE + pos, chunk);
            touch(page, lsn, pos + chunk);
            written += chunk;
            pos += chunk;
        }
    }

    /**
     * Gets the bytes a record is written with: its header, then client data in a pattern derived from its LSN.
     */
    static byte[] recordBytes(long lsn, int clientDataLength, boolean crosses) {
        byte[] bytes = new byte[LogRecord.LENGTH_CALCULATION_OFFSET + clientDataLength];
        LittleEndian.setInt64(bytes, 0x00, lsn);
        LittleEndian.setInt32(bytes, 0x18, clientDataLength);
        LittleEndian.setInt32(bytes, 0x20, 1);
        LittleEndian.setInt16(bytes, 0x28, crosses ? LogRecord.FLAG_CROSSES_PAGE : 0);
        for (int i = LogRecord.LENGTH_CALCULATION_OFFSET; i < bytes.length; i++) {
            bytes[i] = (byte) (lsn + i);
        }
        return bytes;
    }

    /**
     * Writes something that looks like a record header but isn't one.
     */
    void garbage(int page, int position, long lsn, long clientDataLength, long recordType) {
        int offset = page * PAGE_SIZE + position;
        LittleEndian.setInt64(buffer, offset, lsn);
        LittleEndian.setInt32(buffer, offset + 0x18, (int) clientDataLength);
        LittleEndian.setInt32(buffer, offset + 0x20, (int) recordType);
        touch(page, 0, position + LogRecord.LENGTH_CALCULATION_OFFSET);
    }

    /**
     * Fills part of a page with a byte value, as left by an overwritten record.
     */
    void fill(int page, int position, int length, byte value) {
        int offset = page * PAGE_SIZE + position;
        for (int i = 0; i < length; i++) {
            buffer[offset + i] = value;
        }
        touch(page, 0, position + length);
    }

    private void touch(int page, long lsn, int end) {
        isRecordPage[page] = true;
        pageLastLsn[page] = Math.max(pageLastLsn[page], lsn);
        pageEnd[page] = Math.max(pageEnd[page], end);
    }

    byte[] build() {
        long currentLsn = 0;
        for (int page = 0; page < pageLastLsn.length; page++) {
            currentLsn = Math.max(currentLsn, pageLastLsn[page]);
        }

        writeRestartPage(0, currentLsn);
        writeRestartPage(PAGE_SIZE, currentLsn);

        for (int page = 2; page < isRecordPage.length; page++) {
            if (isRecordPage[page]) {
                writeRecordPageHeader(page);
            }
        }

        return buffer.clone();
    }

    private void writeRestartPage(int offset, long currentLsn) {
        LittleEndian.setInt32(buffer, offset, RestartPageHeader.Magic.RSTR);
        LittleEndian.setInt16(buffer, offset + 0x04, 0x1e);
        LittleEndian.setInt16(buffer, offset + 0x06, PAGE_SIZE / SECTOR_SIZE + 1);
        LittleEndian.setInt32(buffer, offset + 0x10, PAGE_SIZE);
        LittleEndian.setInt32(buffer, offset + 0x14, PAGE_SIZE);
        LittleEndian.setInt16(buffer, offset + 0x18, 0x40);
        LittleEndian.setInt16(buffer, offset + 0x1a, 1);
        LittleEndian.setInt16(buffer, offset + 0x1c, 1);

        int area = offset + 0x40;
        LittleEndian.setInt64(buffer, area, currentLsn);
        LittleEndian.setInt16(buffer, area + 0x08, RestartArea.LOGFILE_NO_CLIENT);
        LittleEndian.setInt16(buffer, area + 0x0e, RestartArea.VOLUME_CLEANLY_UNMOUNTED);
        LittleEndian.setInt32(buffer, area + 0x10, seqBits);
        LittleEndian.setInt64(buffer, area + 0x18, buffer.length);
        LittleEndian.setInt16(buffer, area + 0x24, LogRecord.LENGTH_CALCULATION_OFFSET);
        LittleEndian.setInt16(buffer, area + 0x26, DATA_OFFSET);

        protect(offset, 0x1e);
    }

    private void writeRecordPageHeader(int page) {
        int offset = page * PAGE_SIZE;
        LittleEndian.setInt32(buffer, offset, RecordPageHeader.Magic.RCRD);
        LittleEndian.setInt16(buffer, offset + 0x04, RecordPageHeader.HEADER_SIZE);
        LittleEndian.setInt16(buffer, offset + 0x06, PAGE_SIZE / SECTOR_SIZE + 1);
        LittleEndian.setInt64(buffer, offset + 0x08, pageLastLsn[page]);
        LittleEndian.setInt16(buffer, offset + 0x14, 1);
        LittleEndian.setInt16(buffer, offset + 0x16, 1);
        LittleEndian.setInt16(buffer, offset + 0x18, Math.min(pageEnd[page], PAGE_SIZE - 8));
        LittleEndian.setInt64(buffer, offset + 0x20, pageLastLsn[page]);

        protect(offset, RecordPageHeader.HEADER_SIZE);
    }

    /**
     * Moves the last two bytes of each sector into the update sequence array and stamps the sequence number there.
     */
    private void protect(int offset, int updateSequenceOffset) {
        LittleEndian.setInt16(buffer, offset + updateSequenceOffset, UPDATE_SEQUENCE_NUMBER);
        for (int i = 1; i <= PAGE_SIZE / SECTOR_SIZE; i++) {
            int sectorEnd = offset + i * SECTOR_SIZE - 2;
            LittleEndian.setInt16(buffer, offset + updateSequenceOffset + 2 * i,
                LittleEndian.getUInt16(buffer, sectorEnd));
            LittleEndian.setInt16(buffer, sectorEnd, UPDATE_SEQUENCE_NUMBER);
        }
    }
}
