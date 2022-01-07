package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.XfsValidSignature;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class LeafInfo extends XfsObject {

    public static final long LEAF_DIR_MAGIC = 0x3df1;
    public static final long NODE_DIR_MAGIC = 0x3dfF;
    private final long forward;
    private final long backward;
    private final long crc;
    private final long blockNumber;
    private final long logSequenceNumber;
    private final String uuid;
    private final long owner;
    private final long count;
    private final long stale;
    private final XfsFileSystem fileSystem;

    public LeafInfo(byte [] data, long offset, XfsFileSystem fs) throws IOException {
        super(data, (int)offset);
        try {
            if (!isValidSignature()) {
                throw new XfsValidSignature(getAsciiSignature(), validSignatures(), (long)offset, this.getClass());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        fileSystem = fs;
        forward = read(0,4);
        backward = read(4,4);
        crc = read(12,4);
        blockNumber = read(16,8);
        logSequenceNumber = read(24,8);
        owner = read(48,8);
        uuid = readUuid(32,16);
        count = read(56,2);
        stale = read(58,2);
        // 4 byte padding at the end
    }

    @Override
    public long getMagicSignature() throws IOException {
        return read(8,2);
    }

    protected List<Long> validSignatures() { return Arrays.asList(LEAF_DIR_MAGIC,NODE_DIR_MAGIC); }

    public long getForward() {
        return forward;
    }

    public long getBackward() {
        return backward;
    }

    public long getCrc() {
        return crc;
    }

    public long getBlockNumber() {
        return blockNumber;
    }

    public long getLogSequenceNumber() {
        return logSequenceNumber;
    }

    public String getUuid() {
        return uuid;
    }

    public long getOwner() {
        return owner;
    }

    public long getCount() {
        return count;
    }

    public long getStale() {
        return stale;
    }

    @Override
    public String toString() {
        return "LeafInfo{forward=" + forward +
                ", backward=" + backward +
                ", crc=0x" + Long.toHexString(crc) +
                ", blockNumber=" + blockNumber +
                ", logSequenceNumber=0x" + Long.toHexString(logSequenceNumber) +
                ", uuid='" + uuid + '\'' +
                ", owner=" + owner +
                ", count=" + count +
                ", stale=" + stale +
                '}';
    }
}
