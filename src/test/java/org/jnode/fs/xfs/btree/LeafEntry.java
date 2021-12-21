package org.jnode.fs.xfs.btree;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.MyXfsBaseAccessor;
import org.jnode.fs.xfs.MyXfsFileSystem;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class LeafEntry  extends MyXfsBaseAccessor {

    private final long hashval;
    private final long address;

    public LeafEntry(FSBlockDeviceAPI devApi, long superBlockStart, MyXfsFileSystem fs) throws IOException {
        super(devApi, superBlockStart, fs);
        hashval = read(0,4);
        address = read(4,4);
    }

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    public long getHashval() {
        return hashval;
    }

    public long getAddress() {
        return address;
    }

    @Override
    public String toString() {
        return "LeafEntry{hashval=" + Long.toHexString(hashval) +
                ", address=" + Long.toHexString(address) + '}';
    }
}
