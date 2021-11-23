package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;

public class MyAGFreeListHeader extends MyXfsBaseAccessor {

    final static long MAGIC_NUMBER = AsciiToHex("AGFL");

    public MyAGFreeListHeader(FSBlockDeviceAPI devApi, long superBlockStart) {
        super(devApi, superBlockStart);
    }

    public long getSignature() throws IOException {
        return read(0,4);
    }

    @Override
    public boolean isValidSignature() throws IOException {
        return getSignature() == MAGIC_NUMBER;
    }

    /**
     * Sequence number
     *     Contains the allocation group number of the corresponding sector
     */
    public long getAGNumber() throws IOException {
        return read(4,4);
    }

    /**
     * Block type identifier
     *     Contains an UUID that should correspond to sb_uuid or sb_meta_uuid
     */
    public long getUuid() throws IOException {
        return read(8,16);
    }
    /**
     * Log sequence number
     */
    public long getSequenceNumberLastAGFreeList() throws IOException {
        return read(24,8);
    }

    /**
     * Checksum
     */
    public long getChecksum() throws IOException {
        return read(32,4);
    }
}
