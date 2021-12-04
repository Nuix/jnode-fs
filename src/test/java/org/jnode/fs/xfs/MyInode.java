package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MyInode extends MyXfsBaseAccessor {

    enum INodeFormat {
        LOCAL,FILE,BTREE
    }

    /**
     * The magic number ('IN').
     */
    public static final long MAGIC = AsciiToHex("IN");

    public MyInode(FSBlockDeviceAPI devApi, long offset) {
        super(devApi, offset);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(MAGIC);
    }

    @Override
    public long getSignature() throws IOException {
        return read(0, 2);
    }

    public long getMode() throws IOException {
        return read(2, 2);
    }

    public long getVersion() throws IOException {
        return read(4, 1);
    }

    public long getFormat() throws IOException {
        return read(5, 1);
    }

    public long getLinkCount() throws IOException {
        if (getVersion() == 1) {
            return read(6, 2);
        } else {
            return read(16, 4);
        }
    }

    public long getUid() throws IOException {
        return read(8, 4);
    }

    public long getGid() throws IOException {
        return read(12, 4);
    }

    public long getProjId() throws IOException {
        return read(20, 2);
    }

    public long getProjId2() throws IOException {
        return read(22, 8);
    }

    public long getFlushCount() throws IOException {
        return read(30, 2);
    }

    public long getLastAccess() throws IOException {
        return read(32, 4);
    }

    public long getLastAccessFraction() throws IOException {
        return read(36, 4);
    }

    public long getLastUpdate() throws IOException {
        return read(40, 4);
    }

    public long getLastUpdateFraction() throws IOException {
        return read(44, 4);
    }

    public long getLastINodeUpdate() throws IOException {
        return read(48, 4);
    }

    public long getLastINodeUpdateFraction() throws IOException {
        return read(52, 4);
    }

    public long getSize() throws IOException {
        return read(56, 8);
    }

    public long getBlockCount() throws IOException {
        return read(64, 8);
    }

    public long getExtentSize() throws IOException {
        return read(72, 4);
    }

    public long getExtentCount() throws IOException {
        return read(76, 4);
    }

    public long getExtentAttributeCount() throws IOException {
        return read(80, 2);
    }

    public long getAttributesForkOffset() throws IOException {
        return read(82, 1);
    }

    public long getAttributesFormat() throws IOException {
        return read(82, 1);
    }

    public long getFlags() throws IOException {
        return read(90, 2);
    }

    /* version 5 filesystem (inode version 3) fields start here */
    public long getCrc() throws IOException {
        return read(100, 4);
    }

    public long getCreationTime() throws IOException {
        return read(144, 4);
    }

    public long getCreationTimeFraction() throws IOException {
        return read(148, 4);
    }

    public long getINodeNumber() throws IOException {
        return read(152, 8);
    }

    public String getUuId() throws IOException {
        return readUuid(160, 16);
    }

    public int getINodeSizeForOffset() throws IOException {
        return getVersion() == 3 ? 176 : 96;
    }

    public MyInodeHeader getDirectoryHeader() throws IOException {
        return new MyInodeHeader(devApi, getOffset() + getINodeSizeForOffset());
    }

    public List<MyShortFormDirectory> getDirectories() throws IOException {
        final MyInodeHeader header = getDirectoryHeader();
        final long count = header.getCount();
        final long i8Count = header.getI8Count();
        final boolean is8Bit = i8Count > 0;
        final long l = count > 0 ? count : i8Count;
        long offset = header.getFirstEntryAbsoluteOffset();
        List<MyShortFormDirectory> data = new ArrayList<>((int)l);
        for (int i = 0; i < l; i++) {
            final MyShortFormDirectory dir = new MyShortFormDirectory(devApi, offset,is8Bit);
            offset += dir.getOffsetSize();
            data.add(dir);
        }
        return data;
    }

}
