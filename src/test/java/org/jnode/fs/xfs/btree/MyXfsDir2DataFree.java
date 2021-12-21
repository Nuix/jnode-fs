package org.jnode.fs.xfs.btree;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.MyXfsBaseAccessor;
import org.jnode.fs.xfs.MyXfsFileSystem;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MyXfsDir2DataFree extends MyXfsBaseAccessor {

    public static final int XFS_DIR2_DATA_FD_COUNT = 3;

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    public MyXfsDir2DataFree(FSBlockDeviceAPI devApi, long superBlockStart, MyXfsFileSystem fs) {
        super(devApi, superBlockStart, fs);
    }

    public long getDataOffset() throws IOException {
        return read(0,2);
    }

    public long getDataLength() throws IOException {
        return read(2,2);
    }

    @Override
    public String toString() {
        try {
            return "offset: " + Long.toHexString(getDataOffset()) + ", length: " + Long.toHexString(getDataLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
        return "ERROR-MyXfsDir2DataFree";
    }
}
