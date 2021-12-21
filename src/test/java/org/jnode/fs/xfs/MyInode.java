package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.xfs.btree.Leaf;
import org.jnode.fs.xfs.btree.LeafInfo;
import org.jnode.fs.xfs.btree.MyXfsDir3BlkHdr;
import org.jnode.fs.xfs.btree.MyXfsDir3DataHdr;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MyInode extends MyXfsBaseAccessor {

    enum INodeFormat {
        LOCAL(1),EXTENT(2),BTREE(3);
        final int val;
        INodeFormat(int val){
            this.val = val;
        }
    }

    enum FileMode {
        // FILE PERMISSIONS
        OTHER_X(0x0007 ,0x0001),
        OTHER_W(0x0007,0x0002),
        OTHER_R(0x0007,0x0004),
        GROUP_X(0x0038 ,0x0008),
        GROUP_W(0x0038,0x0010),
        GROUP_R(0x0038,0x0020),
        USER_X(0x01c0 ,0x0040),
        USER_W(0x01c0,0x0080),
        USER_R(0x01c0,0x0100),
        //TODO: Check mask
//        STICKY_BIT(0xFFFF,0x0200),
//        SET_GID(0xFFFF,0x0400),
//        SET_UID(0xFFFF,0x0800),
        // FILE TYPE
        NAMED_PIPE(0xf000,0x1000),
        CHARACTER_DEVICE(0xf000,0x2000),
        DIRECTORY(0xf000,0x4000),
        BLOCK_DEVICE(0xf000,0x6000),
        FILE(0xf000,0x8000),
        SYM_LINK(0xf000,0xa000),
        Socket(0xf000,0xc000);
        final int mask;
        final int val;

        private FileMode(int mask,int val){
            this.mask = mask;
            this.val = val;
        }

        public static boolean is(int data,FileMode mode){
            return (data & mode.mask) == mode.val;
        }

        public static List<FileMode> getModes(int data){
            return Arrays.stream(FileMode.values()).filter(mode -> FileMode.is(data,mode)).collect(Collectors.toList());
        }

    }

    /**
     * The magic number ('IN').
     */
    public static final long MAGIC = AsciiToHex("IN");

    private final long iNodeNumber;

    public MyInode(FSBlockDeviceAPI devApi, long offset,long iNodeNumber,MyXfsFileSystem fs) throws IOException {
        super(devApi, offset,fs);
        // Only works for V5
        if (read(152,8) != iNodeNumber) {
            throw new InvalidParameterException("Inode number " + iNodeNumber + " does not match with disk number " + read(152,8));
        }
        this.iNodeNumber = iNodeNumber;
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

    public boolean isDirectory() throws IOException {
        return FileMode.is((int) getMode(),FileMode.DIRECTORY);
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

    public long getINodeNumber() {
        return iNodeNumber;
    }

    public String getUuId() throws IOException {
        return readUuid(160, 16);
    }

    public int getINodeSizeForOffset() throws IOException {
        return getVersion() == 3 ? 176 : 96;
    }

    public MyInodeHeader getDirectoryHeader() throws IOException {
        return new MyInodeHeader(devApi, getOffset() + getINodeSizeForOffset(),fs);
    }

    public List<? extends IMyDirectory> getDirectories() throws IOException {
        final long format = getFormat();
        if (format == INodeFormat.LOCAL.val ){
            final MyInodeHeader header = getDirectoryHeader();
            final long count = header.getCount();
            final long i8Count = header.getI8Count();
            final boolean is8Bit = i8Count > 0;
            final long l = count > 0 ? count : i8Count;
            long offset = header.getFirstEntryAbsoluteOffset();
            List<MyShortFormDirectory> data = new ArrayList<>((int)l);
            for (int i = 0; i < l; i++) {
                final MyShortFormDirectory dir = new MyShortFormDirectory(devApi, offset,is8Bit,fs);
                offset += dir.getOffsetSize();
                data.add(dir);
            }
            return data;
        } else if (format == INodeFormat.EXTENT.val){
            if (!isDirectory()){
                throw new UnsupportedOperationException("Trying to get directories of a non directory inode");
            }
            final List<MyExtentInformation> extents = getExtentInfo();
            int size = 0;
            List<List<MyBlockDirectoryEntry>> bag = new ArrayList<>(extents.size());
            //o MyBlockDirectory(devApi, offset,fs) Como hacer esta decision
            for (int i=0,l=extents.size()-1;i<l;i++) {
                final MyExtentInformation extent = extents.get(i);
                final long offset = extent.getExtentOffset();
                final MyXfsDir3DataHdr data = new MyXfsDir3DataHdr(devApi, offset, fs);
                final List<MyBlockDirectoryEntry> tmp = data.getEntries();
                size += tmp.size();
                bag.add(tmp);
            }

            List<MyBlockDirectoryEntry> entries = new ArrayList<>(size);
            for (List<MyBlockDirectoryEntry> myBlockDirectoryEntries : bag) {
                entries.addAll(myBlockDirectoryEntries);
            }

            final MyExtentInformation leafExtent = extents.get(extents.size() - 1);
            final Leaf leaf = new Leaf(devApi, leafExtent.getExtentOffset(), fs, extents.size() - 1);

            return entries;
        }
        throw new UnsupportedOperationException("getDirectories not supported for inode format " + format);

    }

    public List<MyExtentInformation> getExtentInfo() throws IOException {
        long offset = getOffset() + getINodeSizeForOffset();
        final int count = (int) getExtentCount();
        final ArrayList<MyExtentInformation> list = new ArrayList<>(count);
        for (int i=0;i<count;i++) {
            final MyExtentInformation info = new MyExtentInformation(devApi, offset,fs);
            list.add(info);
            offset += 0x10;
        }
        return list;
    }

}
