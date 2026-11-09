package org.jnode.fs.ntfs.logfile;

import org.jnode.fs.ntfs.NTFSStructure;

/**
 * The NTFS restart area held in the client data of a checkpoint record (record type 2). It is not laid out like an
 * update record: it gives the LSN the checkpoint starts at and where the tables recovery needs were logged.
 */
public class CheckpointRecord extends NTFSStructure {

    /**
     * The size of the fields read here.
     */
    public static final int SIZE = 0x40;

    /**
     * Creates a new instance.
     *
     * @param buffer the buffer.
     * @param offset the offset of the client data.
     */
    public CheckpointRecord(byte[] buffer, int offset) {
        super(buffer, offset);
    }

    public long getMajorVersion() {
        return getUInt32(0x00);
    }

    public long getMinorVersion() {
        return getUInt32(0x04);
    }

    /**
     * Gets the LSN that the checkpoint starts at.
     *
     * @return the LSN.
     */
    public long getStartOfCheckpointLsn() {
        return getInt64(0x08);
    }

    public long getOpenAttributeTableLsn() {
        return getInt64(0x10);
    }

    public long getAttributeNamesLsn() {
        return getInt64(0x18);
    }

    public long getDirtyPageTableLsn() {
        return getInt64(0x20);
    }

    public long getTransactionTableLsn() {
        return getInt64(0x28);
    }

    public long getOpenAttributeTableLength() {
        return getUInt32(0x30);
    }

    public long getAttributeNamesLength() {
        return getUInt32(0x34);
    }

    public long getDirtyPageTableLength() {
        return getUInt32(0x38);
    }

    public long getTransactionTableLength() {
        return getUInt32(0x3c);
    }

    /**
     * Gets a debug string for this instance.
     *
     * @return the debug string.
     */
    public String toDebugString() {
        return "Checkpoint:[\n"
            + "version: " + getMajorVersion() + "." + getMinorVersion() + "\n"
            + "start-of-checkpoint-lsn: " + getStartOfCheckpointLsn() + "\n"
            + "open-attribute-table-lsn: " + getOpenAttributeTableLsn() + "\n"
            + "attribute-names-lsn: " + getAttributeNamesLsn() + "\n"
            + "dirty-page-table-lsn: " + getDirtyPageTableLsn() + "\n"
            + "transaction-table-lsn: " + getTransactionTableLsn() + "\n"
            + "open-attribute-table-length: " + getOpenAttributeTableLength() + "\n"
            + "attribute-names-length: " + getAttributeNamesLength() + "\n"
            + "dirty-page-table-length: " + getDirtyPageTableLength() + "\n"
            + "transaction-table-length: " + getTransactionTableLength() + "]";
    }
}
