package org.jnode.fs.btrfs;

import org.jnode.util.LittleEndian;

/**
 * A btrfs key — 17 bytes, the ordering key of every B-tree:
 *
 * <pre>
 * struct btrfs_disk_key {
 *     __le64 objectid;
 *     u8 type;
 *     __le64 offset;
 * } __attribute__ ((__packed__));
 * </pre>
 *
 * @author David Baird
 */
public class BtrfsDiskKey implements Comparable<BtrfsDiskKey> {

    private final long objectId;
    private final int type;
    private final long offset;

    public BtrfsDiskKey(byte[] buf, int pos) {
        objectId = LittleEndian.getInt64(buf, pos);
        type = buf[pos + 8] & 0xFF;
        offset = LittleEndian.getInt64(buf, pos + 9);
    }

    /** A synthetic key, e.g. the lower bound of a range search. */
    public BtrfsDiskKey(long objectId, int type, long offset) {
        this.objectId = objectId;
        this.type = type & 0xFF;
        this.offset = offset;
    }

    /**
     * btrfs key order: objectid, then type, then offset. objectid and offset are u64 and type is u8,
     * so all three compare <em>unsigned</em> — the whole tree is sorted this way.
     */
    @Override
    public int compareTo(BtrfsDiskKey o) {
        int c = Long.compareUnsigned(objectId, o.objectId);
        if (c != 0) {
            return c;
        }
        if (type != o.type) {
            return Integer.compare(type, o.type); // both already masked to 0..255
        }
        return Long.compareUnsigned(offset, o.offset);
    }

    public long getObjectId() {
        return objectId;
    }

    public int getType() {
        return type;
    }

    public long getOffset() {
        return offset;
    }

    @Override
    public String toString() {
        return "(" + objectId + " type=" + type + " off=" + offset + ")";
    }
}
