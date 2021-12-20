package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class MyAGFreeListHeader extends MyXfsBaseAccessor {

    final static long MAGIC_NUMBER = AsciiToHex("AGFL");
    static final long MAGIC_NUMBER2 = AsciiToHex("XAFL");

    public MyAGFreeListHeader(FSBlockDeviceAPI devApi, long superBlockStart,MyXfsFileSystem fs) {
        super(devApi, superBlockStart,fs);
    }

    @Override
    protected List<Long> validSignatures() {
        return Arrays.asList(MAGIC_NUMBER, MAGIC_NUMBER2);
    }

    public String getXfsDbInspectionString() throws IOException {
        String str = "";
        str += "magicnum = 0x" + Long.toHexString(getSignature()) + "\n";
        str += "seqno = " + getAGNumber() + "\n";
        str += "uuid = " + getUuid() + "\n";
        str += "lsn = " + getSequenceNumberLastAGFreeList() + "\n";
        str += "crc = 0x" + Long.toHexString(getChecksum()) + " (correct)\n";
        return str;
    }

    /**
     * Sequence number
     * Contains the allocation group number of the corresponding sector
     */
    public long getAGNumber() throws IOException {
        return read(4, 4);
    }

    /**
     * Block type identifier
     * Contains an UUID that should correspond to sb_uuid or sb_meta_uuid
     */
    public String getUuid() throws IOException {
        return readUuid(8, 16);
    }

    /**
     * Log sequence number
     */
    public long getSequenceNumberLastAGFreeList() throws IOException {
        return read(24, 8);
    }

    /**
     * Checksum
     */
    public long getChecksum() throws IOException {
        return read(32, 4);
    }
}
