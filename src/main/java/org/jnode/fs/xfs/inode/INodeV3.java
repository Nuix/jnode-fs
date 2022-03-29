package org.jnode.fs.xfs.inode;

import org.jnode.fs.xfs.XfsFileSystem;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * An XFS v3 inode ('xfs_dinode_core'). Structure definition in {@link INode}.
 */
public class INodeV3 extends INodeV2 {

    /**
     * The offset to the v3 inode data.
     */
    private static final int V3_DATA_OFFSET = 176;

    /**
     * The {@link UUID} for a v3 inode.
     */
    private final UUID uuid;

    /**
     * Creates a new v3 inode.
     *
     * @param inodeNumber the number.
     * @param data        the data.
     * @param offset      the offset to the inode in the data.
     * @param fs          the {@link XfsFileSystem}.
     */
    INodeV3(long inodeNumber, byte[] data, int offset, XfsFileSystem fs) {
        super(inodeNumber, data, offset, fs);

        long v3InodeNumber = getInt64(152);
        if (v3InodeNumber != inodeNumber) {
            throw new IllegalStateException("Stored inode (" + v3InodeNumber +
                    ") does not match passed in number:" + inodeNumber);
        }

        long upperValue = getInt64(160);
        long lowerValue = getInt64(168);
        uuid = new UUID(upperValue, lowerValue);
    }

    /**
     * Gets the CRC for the inode.
     *
     * @return the CRC for the inode.
     */
    public long getCrc() {
        return getUInt32(100);
    }

    /**
     * Gets the change count for the inode.
     *
     * @return the change count for the inode.
     */
    public long getChangeCount() {
        return getInt64(104);
    }

    /**
     * Gets the log sequence number.
     *
     * @return the log sequence number.
     */
    public long getLogSequenceNumber() {
        return getInt64(112);
    }

    /**
     * Gets the raw flags2 value.
     *
     * @return the raw flags2 value.
     */
    public long getRawFlags2() {
        return getInt64(120);
    }

    /**
     * Gets the {@link List} of {@link Flag2} for the inode.
     *
     * @return the {@link List} of {@link Flag2} for the inode.
     */
    public List<Flag2> getFlags2() {
        return Flag2.fromValue(getRawFlags2());
    }

    /**
     * Gets the copy-on-write extent size hint.
     *
     * @return the copy-on-write extent size hint.
     */
    public long getCowExtSize() {
        return getUInt32(128);
    }

    /**
     * Gets the time the inode was created - seconds value.
     *
     * @return the created time - seconds value.
     */
    public long getCreatedTimeSec() {
        return getUInt32(144);
    }

    /**
     * Gets the time the inode was created - nanoseconds value.
     *
     * @return the created time - nanoseconds value.
     */
    public long getCreatedTimeNsec() {
        return getUInt32(148);
    }

    /**
     * Gets the inode creation time in milliseconds. TODO: move elsewhere.
     *
     * @return the inode creation time.
     */
    public long getCreated() {
        return (getCreatedTimeSec() * 1000) + (getCreatedTimeNsec() / 1_000_000);
    }

    /**
     * Gets the stored inode number if this is a v3 inode.
     *
     * @return the number.
     */
    public long getV3INodeNumber() {
        return getInt64(152);
    }

    /**
     * Gets the inode file system UUID.
     *
     * @return the UUID.
     */
    public UUID getUuid() {
        return uuid;
    }

    @Override
    public int getDataOffset() {
        return V3_DATA_OFFSET;
    }

    /**
     * Extended flags associated with a v3 inode.
     */
    public enum Flag2 {

        /**
         * For a file, enable DAX to increase performance on
         * persistent-memory storage. If set on a directory, files
         * created in the directory will inherit this flag.
         */
        DAX(0x01),

        /**
         * This inode shares (or has shared) data blocks with another
         * inode.
         */
        REFLINK(0x02),

        /**
         * For files, this is the extent size hint for copy on write
         * operations; see di_cowextsize for details. For
         * directories, the value in di_cowextsize will be copied
         * to all newly created files and directories.
         */
        COWEXTSIZE(0x04);

        private final long bitValue;

        Flag2(int bitValue) {
            this.bitValue = bitValue;
        }

        public boolean isSet(long value) {
            return (bitValue & value) == bitValue;
        }

        static List<Flag2> fromValue(long value) {
            return Arrays.stream(values())
                    .filter(f -> f.isSet(value))
                    .collect(Collectors.toList());
        }
    }
}
