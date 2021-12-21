package org.jnode.fs.xfs.btree;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.MyXfsBaseAccessor;
import org.jnode.fs.xfs.MyXfsFileSystem;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MyXfsDir3BlkHdr extends MyXfsBaseAccessor {

    public static final long MAGIC = AsciiToHex("XDD3");

    public MyXfsDir3BlkHdr(FSBlockDeviceAPI devApi, long superBlockStart, MyXfsFileSystem fs) {
        super(devApi, superBlockStart, fs);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(MAGIC);
    }

    public long getCrc() throws IOException {
        return read(4,4);
    }

    public long getBlockNumber() throws IOException {
        return read(8,8);
    }

    public long getLogSequenceNumber() throws IOException {
        return read(16,8);
    }

    public String getUuid() throws IOException {
        return readUuid(24,16);
    }

    public long getOwner() throws IOException {
        return read(40,8);
    }

    @Override
    public String toString() {
        try {
            return "MyXfsDir3BlkHdr{Crc: " + Long.toHexString(getCrc())
                    + ",bno: " + getBlockNumber()
                    + ",lsn: " + Long.toHexString(getLogSequenceNumber())
                    + ",uuid: " + getUuid()
                    + ",owner: " + getOwner() + '}';
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "ERROR-MyXfsDir3BlkHdr";
    }
}
