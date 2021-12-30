package org.jnode.fs.xfs.btree;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.MyExtentInformation;
import org.jnode.fs.xfs.MyXfsBaseAccessor;
import org.jnode.fs.xfs.MyXfsFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MyBPlusTreeExtentList extends MyXfsBaseAccessor {

    private final static Long MAGIC = AsciiToHex("BMA3");
    private final long level;
    private final long numrecs;
    private final long right;
    private final long left;
    private final long blockNo;
    private final long lsn;
    private final String uuid;
    private final long owner;
    private final List<MyExtentInformation> extents;
    private final long crc;

    public MyBPlusTreeExtentList(FSBlockDeviceAPI devApi, long superBlockStart, MyXfsFileSystem fs) throws IOException {
        super(devApi, superBlockStart, fs);
        this.level = read(4, 2);
        this.numrecs = read(6, 2);
        this.right = read(8, 8);
        this.left = read(16, 8);
        this.blockNo = read(24, 8);
        this.lsn = read(32, 8);
        this.uuid = readUuid(40, 16);
        this.owner = read(56, 8);
        this.crc = read(64, 4);
        this.extents = getExtentInfo();
    }
    private List<MyExtentInformation> getExtentInfo() throws IOException {
        long offset = getOffset() + 72;
        final List<MyExtentInformation> list = new ArrayList<>((int)numrecs);
        for (int i=0;i<numrecs;i++) {
            final MyExtentInformation info = new MyExtentInformation(devApi, offset,fs);
            list.add(info);
            offset += 0x10;
        }
        return list;
    }

    public long getLevel() {
        return level;
    }

    public long getNumrecs() {
        return numrecs;
    }

    public long getRight() {
        return right;
    }

    public long getLeft() {
        return left;
    }

    public long getBlockNo() {
        return blockNo;
    }

    public long getLsn() {
        return lsn;
    }

    public String getUuid() {
        return uuid;
    }

    public long getOwner() {
        return owner;
    }

    public List<MyExtentInformation> getExtents() {
        return extents;
    }

    public long getCrc() {
        return crc;
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(MAGIC);
    }
}
