package org.jnode.fs.xfs.inode;

import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.XfsValidSignature;
import org.jnode.fs.xfs.attribute.XfsAttribute;
import org.jnode.fs.xfs.attribute.XfsAttributeHeader;
import org.jnode.fs.xfs.extent.DataExtent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A XFS inode ('xfs_dinode_core').
 *
 * @author Luke Quinane
 */
public class INode extends XfsObject {

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
    public static final long MAGIC = 0x494e;

    /**
     * The offset to the inode data.
     */
    public static final int DATA_OFFSET = 0x64;

    /**
     * The offset to the v3 inode data.
     */
    public static final int V3_DATA_OFFSET = 0xb0;

    /**
     * The offset to the v3 inode data.
     */
    public static final int V3 = 0x3;

    /**
     * The inode number.
     */
    private final long inodeNr;

    /**
     * Creates a new inode.
     *
     * @param inodeNr the number.
     * @param data the data.
     * @param offset the offset to this inode in the data.
     */
    public INode(long inodeNr, byte[] data, int offset) {
        super(data, offset);
        this.inodeNr = inodeNr;
        try {
            if (!isValidSignature()) {
                throw new XfsValidSignature(getAsciiSignature(), validSignatures(), (long)offset, this.getClass());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (getVersion() >= V3) {
            if (getV3INodeNumber() != inodeNr) {
                throw new IllegalStateException("Stored inode (" + getV3INodeNumber() +
                    ") does not match passed in number:" + inodeNr);
            }
        }
    }
    protected List<Long> validSignatures() { return Arrays.asList(MAGIC); }

    /**
     * Gets the inode number.
     *
     * @return the inode number.
     */
    public long getINodeNr() {
        return inodeNr;
    }

    /**
     * Gets the magic number stored in this inode.
     *
     * @return the magic.
     */
    public long getMagicSignature() { return getUInt16(0); }

    /**
     * Gets the mode stored in this inode.
     *
     * @return the mode.
     */
    public int getMode() {
        return getUInt16(0x2);
    }

    /**
     * Is Directory the current inode.
     *
     * @return true.
     */
    public boolean isDirectory() throws IOException {
        return FileMode.is((int) getMode(),FileMode.DIRECTORY);
    }

    /**
     * Gets the mode stored in this inode.
     *
     * @return the mode.
     */
    public boolean isFile() throws IOException {
        return FileMode.is((int) getMode(),FileMode.FILE);
    }

    /**
     * Gets the version.
     *
     * @return the version.
     */
    public int getVersion() {
        return getUInt8(0x4);
    }

    /**
     * Gets the format stored in this inode.
     *
     * @return the format.
     */
    public int getFormat() {
        return getUInt8(0x5);
    }

    /**
     * Gets the number of hard links to this inode.
     *
     * @return the link count.
     */
    public long getLinkCount() {
        if (getVersion() == 1) {
            // Link count stored in di_onlink
            return getUInt16(0x6);
        } else {
            // Link count stored in di_nlink
            return getUInt32(0x10);
        }
    }

    /**
     * Gets the user-id of the owner.
     *
     * @return the user-id.
     */
    public long getUid() {
        return getUInt32(0x8);
    }

    /**
     * Gets the group-id of the owner.
     *
     * @return the group-id.
     */
    public long getGid() {
        return getUInt32(0xc);
    }

    /**
     * Gets the access time.
     *
     * @return the access time.
     */
    public long getAccessTime() { return getInt64(0x20); }

    /**
     * Gets the modified time.
     *
     * @return the modified time.
     */
    public long getModifiedTime() {
        return getInt64(0x28);
    }

    /**
     * Gets the created time.
     *
     * @return the created time.
     */
    public long getCreatedTime() { return getInt64(0x30); }

    /**
     * Gets the size.
     *
     * @return the size.
     */
    public long getSize() {
        return getInt64(0x38);
    }

    /**
     * Gets the number of blocks.
     *
     * @return the number of blocks.
     */
    public long getNumberOfBlocks() {
        return getUInt32(0x40);
    }

    /**
     * Gets the extent length.
     *
     * @return the extent length.
     */
    public long getExtentLength() {
        return getUInt32(0x48);
    }

    /**
     * Gets the number of extents.
     *
     * @return the extent count.
     */
    public long getExtentCount() {
        return getUInt32(0x4c);
    }

    /**
     * Gets the time the inode was created for a v3 inode.
     *
     * @return the created time.
     */
    public long getV3CreatedTime() {
        return getInt64(0x98);
    }

    /**
     * Gets the stored inode number if this is a v3 inode.
     *
     * @return the number.
     */
    public long getV3INodeNumber() {
        return getInt64(0x98);
    }

    /**
     * Gets the v3 inode file system UUID.
     *
     * @return the UUID.
     */
    public long getV3Uuid() {
        return getInt64(0xa0);
    }

    public int getINodeSizeForOffset() throws IOException {
        return getVersion() == V3 ? V3_DATA_OFFSET : DATA_OFFSET;
    }

    public boolean isSymLink() throws IOException {
        return FileMode.is((int) getMode(),FileMode.SYM_LINK);
    }

    public List<DataExtent> getExtentInfo() throws IOException {
        long offset = getOffset() + getINodeSizeForOffset();
        final int count = (int) getExtentCount();
        final ArrayList<DataExtent> list = new ArrayList<>(count);
        for (int i=0;i<count;i++) {
            final DataExtent info = new DataExtent(this.getData(), (int) offset);
            list.add(info);
            offset += 0x10;
        }
        return list;
    }

    public List<XfsAttribute> getAttributes() throws IOException {
        long off =  getOffset() + getINodeSizeForOffset() + (getAttributesForkOffset() * 8);
        final XfsAttributeHeader myXfsAttributeHeader = new XfsAttributeHeader(getData(), off);
        final long attributesFormat = getAttributesFormat();
        if (attributesFormat == 1){
            off += 4;  // header length remeber header has a 1 byte padding
            final int count = (int) myXfsAttributeHeader.getCount();
            List<XfsAttribute> attributes = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                final XfsAttribute attribute = new XfsAttribute(getData(), off);
                attributes.add(attribute);
                off += attribute.getAttributeSizeForOffset();
            }
            return attributes;
        } else {
            System.out.println(">>> Pending implementation due to lack of examples for attribute format " + attributesFormat
                    + " Found on Inode " + inodeNr);
        }
        return Collections.emptyList();
    }

    public long getAttributesForkOffset() throws IOException {
        return read(82, 1);
    }

    public long getAttributesFormat() throws IOException {
        return read(83, 1);
    }

    @Override
    public String toString() {
        return String.format(
            "inode:[%d version:%d format:%d size:%d uid:%d gid:%d]",
            inodeNr, getVersion(), getFormat(), getSize(), getUid(), getGid());
    }
}
