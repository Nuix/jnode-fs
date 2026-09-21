package org.jnode.fs.ntfs.logfile;

import org.jnode.fs.ntfs.NTFSStructure;

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
     * Creates a new log file record.
     *  @param buffer the buffer.
     * @param offset the offset in the buffer to create the record at.
     * @param pageSize the page size.
     * @param logPageDataOffset the offset in the page to the log record data.
     */
    public LogRecord(byte[] buffer, int offset, int pageSize, int logPageDataOffset) {
        super(buffer, offset);

        this.pageSize = pageSize;
        this.logPageDataOffset = logPageDataOffset;
    }

    /**
     * Checks if the record appears to be valid.
     *
     * @return {@code true} if valid.
     */
    public boolean isValid() {
        return getLsn() != 0;
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
     * @return the absolute position in the buffer.
     */
    private int resolveAcrossPages(int offset) {
        int position = getOffset();
        int remaining = offset;

        while (true) {
            int spaceLeftInPage = pageSize - position % pageSize;

            if (remaining < spaceLeftInPage) {
                return position + remaining;
            }

            remaining -= spaceLeftInPage;
            position = nextPageDataStart(position);
        }
    }

    /**
     * Gets the absolute position of the data area of the page after the one holding a given position.
     *
     * @param position the absolute position in the buffer.
     * @return the absolute position of the next page's data area.
     */
    private int nextPageDataStart(int position) {
        int nextPage = position - position % pageSize + pageSize;

        if (nextPage >= getBuffer().length) {
            // Wrap back around to the start of the 'normal' area
            nextPage = LogFile.NORMAL_AREA_START * pageSize;
        }

        return nextPage + logPageDataOffset;
    }

    /**
     * Gets an unsigned 8-bit integer which may or may not be past the log file page boundary.
     *
     * @param offset the offset to the field in this structure.
     * @return the value.
     */
    private int getUInt8AcrossPages(int offset) {
        return getBuffer()[resolveAcrossPages(offset)] & 0xFF;
    }

    /**
     * Gets an unsigned 16-bit integer which may or may not cross the log file page boundary.
     *
     * @param offset the offset to the field in this structure.
     * @return the value.
     */
    protected int getUInt16AcrossPages(int offset) {
        if (!getCrossesPage()) {
            return getUInt16(offset);
        }

        // Assembled a byte at a time so that a field straddling the boundary picks up each half from the page it
        // actually lives on.
        return getUInt8AcrossPages(offset) | getUInt8AcrossPages(offset + 1) << 8;
    }

    /**
     * Gets an unsigned 32-bit integer which may or may not cross the log file page boundary.
     *
     * @param offset the offset to the field in this structure.
     * @return the value.
     */
    protected long getUInt32AcrossPages(int offset) {
        if (!getCrossesPage()) {
            return getUInt32(offset);
        }

        return getUInt16AcrossPages(offset) | (long) getUInt16AcrossPages(offset + 2) << 16;
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
        if (!getCrossesPage()) {
            getData(offset, dst, dstOffset, length);
            return;
        }

        // The positions worked out here are absolute in the buffer, not relative to this record, so the copies are
        // made against the buffer directly rather than through getData().
        final byte[] buffer = getBuffer();
        int position = resolveAcrossPages(offset);

        while (length > 0) {
            int readLength = Math.min(length, pageSize - position % pageSize);

            System.arraycopy(buffer, position, dst, dstOffset, readLength);

            length -= readLength;
            dstOffset += readLength;

            // Anything left continues in the data area of the next page, past its header
            position = nextPageDataStart(position);
        }
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
        return getUInt32AcrossPages(0x48);
    }

    /**
     * Gets the target LCN.
     *
     * @return the target LCN.
     */
    public long getTargetLcn() {
        return getUInt32AcrossPages(0x50);
    }

    /**
     * Gets the redo data for the record.
     *
     * @param buffer the buffer to write into.
     */
    public void getRedoData(byte[] buffer) {
        getDataAcrossPages(0x30 + getRedoOffset(), buffer, 0, getRedoLength());
    }

    /**
     * Gets the undo data for the record.
     *
     * @param buffer the buffer to write into.
     */
    public void getUndoData(byte[] buffer) {
        getDataAcrossPages(0x30 + getUndoOffset(), buffer, 0, getUndoLength());
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
