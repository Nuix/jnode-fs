package org.jnode.fs.ntfs.logfile;

import org.jnode.fs.ntfs.NTFSStructure;
import org.jnode.util.LittleEndian;

/**
 * $LogFile log record.
 *
 * @author Luke Quinane
 */
public class LogRecord extends NTFSStructure {

    /**
     * The length of the fixed part of the record.
     */
    public static int HEADER_SIZE = 0x58;

    /**
     * The position inside the structure from which {@link #getClientDataLength()} is calculated.
     */
    public static int LENGTH_CALCULATION_OFFSET = 0x30;

    /**
     * The record type value for a check point record.
     */
    public static int RECORD_TYPE_CHECKPOINT = 0x2;

    /**
     * The record type value for an update record.
     */
    public static final int RECORD_TYPE_UPDATE = 0x1;

    /**
     * The position of the list of target LCNs in an update record.
     */
    private static final int LCN_LIST_OFFSET = 0x50;

    /**
     * The flag that indicates the record crosses a page boundary.
     */
    public static int FLAG_CROSSES_PAGE = 0x1;

    /**
     * The 'LCNs to follow' value that indicates that there is a subsequent record.
     */
    public static int LCN_FOLLOWING_RECORD = 0x1;

    /**
     * The page size for log pages.
     */
    private final int pageSize;

    /**
     * The offset in the page to the log record data.
     */
    private final int logPageDataOffset;

    /**
     * The offset in the $LogFile that the record's LSN encodes.
     */
    private final long homeOffset;

    /**
     * The buffer positions of the pages the record continues in, in order, or {@code null} to take the physically
     * next pages.
     */
    private final int[] continuationPages;

    /**
     * The page a record crossing the last page continues in, when {@link #continuationPages} is {@code null}.
     */
    private final int firstCircularPage;

    /**
     * Creates a new log file record which continues in the physically next pages, wrapping from the last page to
     * page 4 as logs written before Windows 8 do.
     *
     * @param buffer the buffer.
     * @param offset the offset in the buffer to create the record at.
     * @param pageSize the page size.
     * @param logPageDataOffset the offset in the page to the log record data.
     */
    public LogRecord(byte[] buffer, int offset, int pageSize, int logPageDataOffset) {
        this(buffer, offset, pageSize, logPageDataOffset, LogFile.NORMAL_AREA_START);
    }

    /**
     * Creates a new log file record which continues in the physically next pages.
     *
     * @param buffer the buffer.
     * @param offset the offset in the buffer to create the record at.
     * @param pageSize the page size.
     * @param logPageDataOffset the offset in the page to the log record data.
     * @param firstCircularPage the page to wrap to from the last page: 4 for logs written before Windows 8, 34 for
     *     those written by Windows 8 and later, where pages 4-33 hold page copies.
     */
    public LogRecord(byte[] buffer, int offset, int pageSize, int logPageDataOffset, int firstCircularPage) {
        this(buffer, offset, pageSize, logPageDataOffset, offset, null, firstCircularPage);
    }

    /**
     * Creates a new log file record.
     *
     * @param buffer the buffer.
     * @param offset the offset in the buffer to create the record at.
     * @param pageSize the page size.
     * @param logPageDataOffset the offset in the page to the log record data.
     * @param homeOffset the offset in the $LogFile that the record's LSN encodes.
     * @param continuationPages the buffer positions of the pages the record continues in.
     */
    LogRecord(byte[] buffer, int offset, int pageSize, int logPageDataOffset, long homeOffset,
              int[] continuationPages) {
        this(buffer, offset, pageSize, logPageDataOffset, homeOffset, continuationPages, LogFile.NORMAL_AREA_START);
    }

    private LogRecord(byte[] buffer, int offset, int pageSize, int logPageDataOffset, long homeOffset,
                      int[] continuationPages, int firstCircularPage) {
        super(buffer, offset);

        this.pageSize = pageSize;
        this.logPageDataOffset = logPageDataOffset;
        this.homeOffset = homeOffset;
        this.continuationPages = continuationPages;
        this.firstCircularPage = firstCircularPage;
    }

    /**
     * Gets the offset in the $LogFile that the record's LSN encodes.
     *
     * <p>This differs from {@link #getOffset()} for a record read from a page copy in pages 2-33: {@code getOffset()}
     * is where its bytes are, this is where the log places it.</p>
     *
     * @return the offset.
     */
    public long getHomeOffset() {
        return homeOffset;
    }

    /**
     * Checks if the record appears to be valid.
     *
     * @return {@code true} if valid.
     */
    public boolean isValid() {
        return isInBuffer() && getLsn() != 0;
    }

    /**
     * Checks whether the part of the record that is read in place lies inside the buffer.
     *
     * <p>Only the fields up to {@link #LENGTH_CALCULATION_OFFSET} have to be contiguous; the rest of the header is
     * read through the across-pages accessors.</p>
     *
     * @return {@code true} if the fixed part of the record can be read.
     */
    public boolean isInBuffer() {
        int offset = getOffset();
        return offset >= 0 && offset + LENGTH_CALCULATION_OFFSET <= getBuffer().length;
    }

    /**
     * Gets the log file sequence number for this record.
     *
     * @return the LSN.
     */
    public long getLsn() {
        return getInt64(0x00);
    }

    /**
     * Gets the client previous log file sequence number.
     *
     * @return the LSN.
     */
    public long getClientPreviousLsn() {
        return getInt64(0x08);
    }

    /**
     * Gets the client undo next log file sequence number.
     *
     * @return the LSN.
     */
    public long getClientUndoNextLsn() {
        return getInt64(0x10);
    }

    /**
     * Gets the client data length.
     *
     * @return the length.
     */
    public long getClientDataLength() {
        return getUInt32(0x18);
    }

    /**
     * Gets the sequence number or client index.
     *
     * @return the value.
     */
    public int getClientId() {
        return getUInt16(0x1c);
    }

    /**
     * Gets the record type.
     *
     * @return the value.
     */
    public long getRecordType() {
        return getUInt32(0x20);
    }

    /**
     * Gets the transaction ID.
     *
     * @return the transaction ID.
     */
    public long getTransactionId() {
        return getUInt32(0x24);
    }

    /**
     * Gets the log record flags.
     *
     * @return the log record flags.
     */
    public int getFlags() {
        return getUInt16(0x28);
    }

    /**
     * Maps an offset within this record to its absolute position in the buffer.
     *
     * <p>A record that runs past the end of its page continues in the data area of the next page, past that page's
     * header, so once the offset passes the page boundary the mapping is no longer a simple addition.</p>
     *
     * @param offset the offset to the field in this structure.
     * @return the absolute position in the buffer, or -1 if it is past the last page available, and the number of the
     *     page it is on, counting the record's own page as 0.
     */
    private int[] resolveAcrossPages(int offset) {
        int position = getOffset();
        int page = 0;
        int remaining = offset;

        while (position >= 0) {
            int spaceLeftInPage = pageSize - position % pageSize;

            if (remaining < spaceLeftInPage) {
                return new int[] { position + remaining, page };
            }

            remaining -= spaceLeftInPage;
            page++;
            position = nextPageDataStart(position, page);
        }

        return new int[] { -1, page };
    }

    /**
     * Gets the absolute position of the data area of a page the record continues in.
     *
     * @param position the absolute position in the buffer of the previous page's data.
     * @param page the number of the continuation page, from 1.
     * @return the absolute position of that page's data area, or -1 if there is none.
     */
    private int nextPageDataStart(int position, int page) {
        if (continuationPages != null) {
            return page <= continuationPages.length ? continuationPages[page - 1] + logPageDataOffset : -1;
        }

        int nextPage = position - position % pageSize + pageSize;

        if (nextPage >= getBuffer().length) {
            // Wrap back around to the start of the circular area
            nextPage = firstCircularPage * pageSize;
        }

        int start = nextPage + logPageDataOffset;

        // The wrap only helps if the normal area is actually in the buffer. On one no longer than the normal area
        // itself - a truncated or synthetic $LogFile - it lands past the end, so there is nowhere to continue.
        return start < getBuffer().length ? start : -1;
    }

    /**
     * Reads a field which may or may not cross the log file page boundary.
     *
     * <p>Bytes the buffer does not reach are left as zero rather than throwing; a record that runs off the end of
     * the log is rejected by {@link #isValid()} before its fields are of any interest.</p>
     *
     * @param offset the offset to the field in this structure.
     * @param length the length of the field.
     * @return the field.
     */
    private byte[] readAcrossPages(int offset, int length) {
        byte[] field = new byte[length];
        getDataAcrossPages(offset, field, 0, length);
        return field;
    }

    /**
     * Gets an unsigned 16-bit integer which may or may not cross the log file page boundary.
     *
     * @param offset the offset to the field in this structure.
     * @return the value.
     */
    protected int getUInt16AcrossPages(int offset) {
        if (!spansPages()) {
            return getUInt16(offset);
        }

        return LittleEndian.getUInt16(readAcrossPages(offset, 2), 0);
    }

    /**
     * Gets an unsigned 32-bit integer which may or may not cross the log file page boundary.
     *
     * @param offset the offset to the field in this structure.
     * @return the value.
     */
    protected long getUInt32AcrossPages(int offset) {
        if (!spansPages()) {
            return getUInt32(offset);
        }

        return LittleEndian.getUInt32(readAcrossPages(offset, 4), 0);
    }

    /**
     * Gets a signed 64-bit integer which may or may not cross the log file page boundary.
     *
     * @param offset the offset to the field in this structure.
     * @return the value.
     */
    protected long getInt64AcrossPages(int offset) {
        if (!spansPages()) {
            return getInt64(offset);
        }

        return LittleEndian.getInt64(readAcrossPages(offset, 8), 0);
    }

    /**
     * Copy (byte-array) data from a given offset which may or may not cross the log file page boundary.
     *
     * @param offset the offset to read from in this structure.
     * @param dst the destination to write to.
     * @param dstOffset the offset to write from.
     * @param length the length.
     */
    public final void getDataAcrossPages(int offset, byte[] dst, int dstOffset, int length) {
        if (!spansPages()) {
            getData(offset, dst, dstOffset, length);
            return;
        }

        // The positions worked out here are absolute in the buffer, not relative to this record, so the copies are
        // made against the buffer directly rather than through getData().
        final byte[] buffer = getBuffer();
        int[] resolved = resolveAcrossPages(offset);
        int position = resolved[0];
        int page = resolved[1];

        while (length > 0) {
            if (position < 0 || position >= buffer.length) {
                // Nowhere valid left to read from, so the rest of the destination is left as it was
                break;
            }

            int readLength = Math.min(length, Math.min(pageSize - position % pageSize, buffer.length - position));

            System.arraycopy(buffer, position, dst, dstOffset, readLength);

            length -= readLength;
            dstOffset += readLength;

            // Anything left continues in the data area of the next page, past its header
            page++;
            position = nextPageDataStart(position, page);
        }
    }

    /**
     * Gets the record's bytes, header and client data, joined across the pages it spans without their page headers.
     *
     * @return the bytes.
     */
    public byte[] getRecordBytes() {
        byte[] bytes = new byte[(int) (LENGTH_CALCULATION_OFFSET + getClientDataLength())];
        getDataAcrossPages(0, bytes, 0, bytes.length);
        return bytes;
    }

    /**
     * Indicates whether this log record crosses a page boundary.
     *
     * @return {@code true} if it crosses a page boundary.
     */
    public boolean getCrossesPage() {
        return (getFlags() & FLAG_CROSSES_PAGE) == FLAG_CROSSES_PAGE;
    }

    /**
     * Checks whether reads must follow the record onto later pages: either it is flagged as crossing, or its length
     * runs past the end of its page.
     */
    private boolean spansPages() {
        return getCrossesPage()
            || getOffset() % pageSize + LENGTH_CALCULATION_OFFSET + getClientDataLength() > pageSize;
    }

    /**
     * Gets the redo operation.
     *
     * @return the redo operation.
     */
    public int getRedoOperation() {
        return getUInt16AcrossPages(0x30);
    }

    /**
     * Gets the undo operation.
     *
     * @return the undo operation.
     */
    public int getUndoOperation() {
        return getUInt16AcrossPages(0x32);
    }

    /**
     * Gets the redo offset.
     *
     * @return the redo offset.
     */
    public int getRedoOffset() {
        return getUInt16AcrossPages(0x34);
    }

    /**
     * Gets the redo length.
     *
     * @return the redo length.
     */
    public int getRedoLength() {
        return getUInt16AcrossPages(0x36);
    }

    /**
     * Gets the undo offset.
     *
     * @return the undo offset.
     */
    public int getUndoOffset() {
        return getUInt16AcrossPages(0x38);
    }

    /**
     * Gets the undo length.
     *
     * @return the undo length.
     */
    public int getUndoLength() {
        return getUInt16AcrossPages(0x3a);
    }

    /**
     * Gets the target attribute.
     *
     * @return the attribute.
     */
    public int getTargetAttribute() {
        return getUInt16AcrossPages(0x3c);
    }

    /**
     * Gets the number of LCN list entries to follow.
     *
     * @return the number.
     */
    public int getLcnsToFollow() {
        return getUInt16AcrossPages(0x3e);
    }

    /**
     * Gets the record offset.
     *
     * @return the offset.
     */
    public int getRecordOffset() {
        return getUInt16AcrossPages(0x40);
    }

    /**
     * Gets the attribute offset.
     *
     * @return the offset.
     */
    public int getAttributeOffset() {
        return getUInt16AcrossPages(0x42);
    }

    /**
     * Gets the MFT cluster index.
     *
     * @return the value.
     */
    public int getMftClusterIndex() {
        return getUInt16AcrossPages(0x44);
    }

    /**
     * Gets the target VCN.
     *
     * @return the target VCN.
     */
    public long getTargetVcn() {
        return getInt64AcrossPages(0x48);
    }

    /**
     * Gets the first target LCN. This is only an LCN when {@link #hasTargetLcn()}; otherwise the bytes belong to the
     * redo data.
     *
     * @return the target LCN.
     */
    public long getTargetLcn() {
        return getInt64AcrossPages(LCN_LIST_OFFSET);
    }

    /**
     * Checks whether the record has at least one target LCN: it is an update record, its LCNs to follow is above
     * zero, and the first LCN lies inside the record.
     *
     * @return {@code true} if there is a target LCN.
     */
    public boolean hasTargetLcn() {
        return getTargetLcns().length > 0;
    }

    /**
     * Gets the target LCNs: as many as LCNs to follow gives, up to the end of the record.
     *
     * @return the LCNs; empty for a checkpoint record or when none follow.
     */
    public long[] getTargetLcns() {
        if (!isUpdateRecord()) {
            return new long[0];
        }

        long fit = (LENGTH_CALCULATION_OFFSET + getClientDataLength() - LCN_LIST_OFFSET) / 8;
        int count = (int) Math.max(0, Math.min(getLcnsToFollow(), fit));
        long[] lcns = new long[count];
        for (int i = 0; i < count; i++) {
            lcns[i] = getInt64AcrossPages(LCN_LIST_OFFSET + 8 * i);
        }
        return lcns;
    }

    /**
     * Checks whether this is an update record, the only type with redo and undo fields.
     *
     * @return {@code true} for record type 1.
     */
    public boolean isUpdateRecord() {
        return getRecordType() == RECORD_TYPE_UPDATE;
    }

    /**
     * Gets the NTFS restart area held by a checkpoint record.
     *
     * @return the restart area, or {@code null} if this isn't a checkpoint record or its client data is too short.
     */
    public CheckpointRecord getCheckpoint() {
        if (getRecordType() != RECORD_TYPE_CHECKPOINT || getClientDataLength() < CheckpointRecord.SIZE) {
            return null;
        }

        byte[] bytes = new byte[LENGTH_CALCULATION_OFFSET + CheckpointRecord.SIZE];
        getDataAcrossPages(0, bytes, 0, bytes.length);
        return new CheckpointRecord(bytes, LENGTH_CALCULATION_OFFSET);
    }

    /**
     * Checks whether the redo data lies inside this update record's client data.
     *
     * @return {@code true} if it does.
     */
    public boolean isRedoDataInRecord() {
        return isUpdateRecord() && (long) getRedoOffset() + getRedoLength() <= getClientDataLength();
    }

    /**
     * Checks whether the undo data lies inside this update record's client data.
     *
     * @return {@code true} if it does.
     */
    public boolean isUndoDataInRecord() {
        return isUpdateRecord() && (long) getUndoOffset() + getUndoLength() <= getClientDataLength();
    }

    /**
     * Gets the redo data for the record.
     *
     * @return the redo data; empty when it doesn't lie inside the record (see {@link #isRedoDataInRecord()}),
     *     including for a checkpoint record.
     */
    public byte[] getRedoData() {
        if (!isRedoDataInRecord()) {
            return new byte[0];
        }

        byte[] data = new byte[getRedoLength()];
        getDataAcrossPages(0x30 + getRedoOffset(), data, 0, data.length);
        return data;
    }

    /**
     * Gets the undo data for the record.
     *
     * @return the undo data; empty when it doesn't lie inside the record (see {@link #isUndoDataInRecord()}),
     *     including for a checkpoint record.
     */
    public byte[] getUndoData() {
        if (!isUndoDataInRecord()) {
            return new byte[0];
        }

        byte[] data = new byte[getUndoLength()];
        getDataAcrossPages(0x30 + getUndoOffset(), data, 0, data.length);
        return data;
    }

    /**
     * Copies the redo data for the record into a buffer. Nothing is copied when it doesn't lie inside the record, so
     * a buffer sized from {@link #getRedoLength()} is left as it was: use {@link #getRedoData()} to tell.
     *
     * @param buffer the buffer to write into.
     */
    public void getRedoData(byte[] buffer) {
        if (!isRedoDataInRecord()) {
            log.debug("Redo data of $LogFile record {} lies outside the record", getLsn());
            return;
        }

        getDataAcrossPages(0x30 + getRedoOffset(), buffer, 0, Math.min(buffer.length, getRedoLength()));
    }

    /**
     * Copies the undo data for the record into a buffer. Nothing is copied when it doesn't lie inside the record, so
     * a buffer sized from {@link #getUndoLength()} is left as it was: use {@link #getUndoData()} to tell.
     *
     * @param buffer the buffer to write into.
     */
    public void getUndoData(byte[] buffer) {
        if (!isUndoDataInRecord()) {
            log.debug("Undo data of $LogFile record {} lies outside the record", getLsn());
            return;
        }

        getDataAcrossPages(0x30 + getUndoOffset(), buffer, 0, Math.min(buffer.length, getUndoLength()));
    }

    @Override
    public String toString() {
        String type = "";
        if (getRecordType() == RECORD_TYPE_CHECKPOINT) {
            type = "checkpoint";
        } else {
            OperationCode undoCode = OperationCode.fromCode(getUndoOperation());
            if (undoCode != null) {
                type += undoCode.name();
            } else {
                type += "unknown: " + getUndoOperation();
            }

            type += " --- ";

            OperationCode redoCode = OperationCode.fromCode(getRedoOperation());
            if (redoCode != null) {
                type += redoCode.name();
            } else {
                type += "unknown: " + getRedoOperation();
            }
        }
        return String.format("log-record:[%d - %d %s]", getLsn(), getTransactionId(), type);
    }

    /**
     * Gets a debug string for this instance.
     *
     * @return the debug string.
     */
    public String toDebugString() {
        StringBuilder builder = new StringBuilder("Log Record:[\n");
        if (!isUpdateRecord()) {
            builder.append("lsn: " + getLsn() + "\n");
            builder.append("prev-lsn: " + getClientPreviousLsn() + "\n");
            builder.append("undo-lsn: " + getClientUndoNextLsn() + "\n");
            builder.append("data-length: " + getClientDataLength() + "\n");
            builder.append("client-id: " + getClientId() + "\n");
            builder.append("record-type: " + getRecordType() + "\n");
            builder.append("transaction-id: " + getTransactionId() + "\n");
            builder.append("flags: " + getFlags() + "\n");
            CheckpointRecord checkpoint = getCheckpoint();
            builder.append(checkpoint != null ? checkpoint.toDebugString() : "no checkpoint");
            builder.append("]");
            return builder.toString();
        }

        builder.append("lsn: " + getLsn() + "\n");
        builder.append("prev-lsn: " + getClientPreviousLsn() + "\n");
        builder.append("undo-lsn: " + getClientUndoNextLsn() + "\n");
        builder.append("data-length: " + getClientDataLength() + "\n");
        builder.append("client-id: " + getClientId() + "\n");
        builder.append("record-type: " + getRecordType() + "\n");
        builder.append("transaction-id: " + getTransactionId() + "\n");
        builder.append("flags: " + getFlags() + "\n");
        builder.append("redo: " + getRedoOperation() + "\n");
        builder.append("undo: " + getUndoOperation() + "\n");
        builder.append("redo-offset: " + getRedoOffset() + "\n");
        builder.append("redo-length: " + getRedoLength() + "\n");
        builder.append("undo-offset: " + getUndoOffset() + "\n");
        builder.append("undo-length: " + getUndoLength() + "\n");
        builder.append("target-attribute: " + getTargetAttribute() + "\n");
        builder.append("lcns-to-follow: " + getLcnsToFollow() + "\n");
        builder.append("record-offset: " + getRecordOffset() + "\n");
        builder.append("attribute-offset: " + getAttributeOffset() + "\n");
        builder.append("MFT-cluster-index: " + getMftClusterIndex() + "\n");
        builder.append("target-vcn: " + getTargetVcn() + "\n");
        builder.append("target-lcn: " + getTargetLcn() + "]");
        return builder.toString();
    }
}
