package org.jnode.fs.xfs.extent;


import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsValidSignature;


public class BPlusTreeDataExtent extends XfsObject {

    private final static Long MAGIC = asciiToHex("BMA3");
    private final long level;
    private final long numrecs;
    private final long right;
    private final long left;
    private final long blockNo;
    private final long lsn;
    private final String uuid;
    private final long owner;
    private final List<DataExtent> extents;
    private final long crc;

    public BPlusTreeDataExtent(byte[] data, long offset) throws IOException {
        super(data, (int)offset);
        try {
            if (!isValidSignature()) {
                throw new XfsValidSignature(getAsciiSignature(), validSignatures(), (long)offset, this.getClass());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
    private List<DataExtent> getExtentInfo() throws IOException {
        long offset = getOffset() + 72;
        final List<DataExtent> list = new ArrayList<>((int)numrecs);
        for (int i=0;i<numrecs;i++) {
            final DataExtent info = new DataExtent(getData(), (int)offset);
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

    public List<DataExtent> getExtents() {
        return extents;
    }

    public long getCrc() {
        return crc;
    }

    protected List<Long> validSignatures() { return Arrays.asList(MAGIC); }

}
