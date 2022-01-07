package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;
import java.util.Collections;
import java.util.List;


public class LeafEntry  extends XfsObject {

    private final long hashval;
    private final long address;

    public LeafEntry(byte [] data , long offset, XfsFileSystem fs) throws IOException {
        super(data, (int)offset);
        hashval = read(0,4);
        address = read(4,4);
    }

    @Override
    public long getMagicSignature() throws IOException {
        return 0L;
    }

    @Override
    protected List<Long> validSignatures() { return Collections.singletonList(0L); }

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
