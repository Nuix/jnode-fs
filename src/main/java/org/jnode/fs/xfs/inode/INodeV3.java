package org.jnode.fs.xfs.inode;

import java.util.List;
import java.util.UUID;

import org.jnode.fs.FSEntryCreated;
import org.jnode.fs.xfs.XfsEntry;
import org.jnode.fs.xfs.XfsFileSystem;

/**
 * An XFS v3 inode ('xfs_dinode_core'). Structure definition in {@link INode}.
 */
public class INodeV3 extends INodeV2 implements FSEntryCreated {
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
     * Gets the {@link List} of {@link InodeV3Flags} for the inode.
     *
     * @return the {@link List} of {@link InodeV3Flags} for the inode.
     */
    public List<InodeV3Flags> getV3Flags() {
        return InodeV3Flags.fromValue(getRawFlags2());
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

    @Override
    public long getCreated() {
        return XfsEntry.getMilliseconds(getCreatedTimeSec(), getCreatedTimeNsec());
    }
}
