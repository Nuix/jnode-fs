package org.jnode.fs.xfs.inode;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.attribute.XfsLeafOrNodeAttributeReader;
import org.jnode.fs.xfs.attribute.XfsShortFormAttribute;
import org.jnode.fs.xfs.extent.DataExtent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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
     * The logger implementation.
     */
    private static final Logger logger = LoggerFactory.getLogger(INode.class);

    /**
     * The {@link XfsFileSystem}.
     */
    private final XfsFileSystem fs;

    /**
     * The inode number.
     */
    private final long inodeNr;

    /**
     * Creates a new inode.
     *
     * @param inodeNr the number.
     * @param data    the data.
     * @param offset  the offset to this inode in the data.
     */
    public INode(long inodeNr, byte[] data, int offset, XfsFileSystem fs) throws IOException {
        super(data, offset);

        if (getMagicSignature() != MAGIC) {
            throw new IOException("Wrong magic number for XFS INODE: " + getAsciiSignature(getMagicSignature()));
        }
        this.fs = fs;

        this.inodeNr = inodeNr;
        if (getVersion() >= V3 && getV3INodeNumber() != inodeNr) {
            throw new IllegalStateException("Stored inode (" + getV3INodeNumber() +
                    ") does not match passed in number:" + inodeNr);
        }
    }

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
    public long getMagicSignature() {
        return getUInt16(0);
    }

    /**
     * Gets the mode stored in this inode.
     *
     * @return the mode.
     */
    public int getMode() {
        return getUInt16(0x2);
    }

    /**
     * Returns {@code true} if the current inode is a directory
     *
     * @return {@code true} if this is a directory, {@code false} otherwise.
     */
    public boolean isDirectory() {
        return FileMode.DIRECTORY.isSet(getMode());
    }

    /**
     * Returns {@code true} if the current inode is a file.
     *
     * @return {@code true} if this is a file, {@code false} otherwise.
     */
    public boolean isFile() {
        return FileMode.FILE.isSet(getMode());
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
     * Gets the raw format value for this inode.
     *
     * @return the raw format value for this inode.
     */
    public int getRawFormat() {
        return getUInt8(0x5);
    }

    /**
     * Gets the format stored in this inode.
     *
     * @return the format.
     */
    public Format getFormat() {
        return Format.fromValue(getRawFormat());
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
     * (last) access time
     * Contains a POSIX timestamp in seconds
     *
     * @return the access time.
     */
    public long getAccessTimeSec() {
        return getUInt32(0x20);
    }

    /**
     * Gets the (last) access time fraction of second
     * Contains number of nano seconds
     *
     * @return the access time.
     */
    public long getAccessTimeNsec() {
        return getUInt32(0x24);
    }

    /**
     * Gets the (last) modification time
     * Contains a POSIX timestamp in seconds
     *
     * @return the modified time.
     */
    public long getChangedTimeSec() {
        return getUInt32(0x28);
    }

    /**
     * Gets the (last) modification time fraction of second
     * Contains number of nano seconds
     *
     * @return the modified time.
     */
    public long getChangedTimeNsec() {
        return getUInt32(0x2c);
    }

    /**
     * Gets the (last) inode change time
     * Contains a POSIX timestamp in seconds
     *
     * @return the created time.
     */
    public long getCreatedTimeSec() {
        return getUInt32(0x30);
    }

    /**
     * Gets the (last) inode change time fraction of second
     * Contains number of nano seconds
     *
     * @return the created time.
     */
    public long getCreatedTimeNsec() {
        return getUInt32(0x34);
    }

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
        // TODO: review the UUID value here
        return getInt64(0xa0);
    }

    /**
     * Gets the inode size for offset.
     *
     * @return the size for offset.
     */
    public int getINodeSizeForOffset() {
        return getVersion() == V3 ? V3_DATA_OFFSET : DATA_OFFSET;
    }

    /**
     * Checks if the inode is a symbolic link.
     *
     * @return true if inode is a symbolic link.
     */
    public boolean isSymLink() {
        return FileMode.SYM_LINK.isSet(getMode());
    }

    /**
     * Gets the text of the symlink file.
     *
     * @return the symbolic link text.
     */
    public String getSymLinkText() {
        ByteBuffer buffer = ByteBuffer.allocate((int) getSize());
        System.arraycopy(getData(), getOffset() + getINodeSizeForOffset(), buffer.array(), 0, (int) getSize());
        return new String(buffer.array(), StandardCharsets.UTF_8);
    }

    /**
     * Gets all the entries of the current b+tree directory.
     *
     * @return the list of extents entries.
     */
    public List<DataExtent> getExtentInfo() {
        long offset = getOffset() + getINodeSizeForOffset();
        final int count = (int) getExtentCount();
        final ArrayList<DataExtent> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            final DataExtent info = new DataExtent(this.getData(), (int) offset);
            list.add(info);
            offset += 0x10;
        }
        return list;
    }

    /**
     * Gets the inode attributes.
     *
     * @return list of inode attributes.
     */
    public List<FSAttribute> getAttributes() throws IOException {
        long offset = getOffset() + getINodeSizeForOffset() + (getAttributesForkOffset() * 8);
        Format attributesFormat = Format.fromValue(getAttributesFormat());
        if (attributesFormat == Format.LOCAL) {
            return getShortFormAttributes((int) offset);
        } else if (attributesFormat == Format.EXTENTS) {
            XfsLeafOrNodeAttributeReader attributeReader = new XfsLeafOrNodeAttributeReader(getData(), (int) offset, this, fs);
            return attributeReader.getAttributes();
        } else {
            logger.warn(">>> Pending implementation due to lack of examples for attribute format {} Found on Inode {}",
                    attributesFormat, inodeNr);
        }
        return Collections.emptyList();
    }

    /**
     * Gets the {@link List} of {@link XfsShortFormAttribute}s.
     *
     * @param offset the offset to start reading data from.
     * @return the {@link List} of {@link XfsShortFormAttribute}s.
     */
    private List<FSAttribute> getShortFormAttributes(int offset)
    {
        // totSize is the first value - we don't use it.
        offset += 2;
        int attributeCount = getUInt8(offset);
        offset += 2;

        List<FSAttribute> attributes = new ArrayList<>(attributeCount);
        for (int i = 0; i < attributeCount; i++) {
            XfsShortFormAttribute attribute = new XfsShortFormAttribute(getData(), offset);
            attributes.add(attribute);
            offset += attribute.getAttributeSizeForOffset();
        }

        return attributes;
    }

    public int getAttributeExtentCount() {
        return getUInt16(80);
    }

    /**
     * Gets the offset of the fork attribute.
     *
     * @return the offset.
     */
    public long getAttributesForkOffset() {
        return getUInt8(82);
    }

    /**
     * Gets attribute format value.
     *
     * @return the attribute format value.
     */
    public int getAttributesFormat() {
        return getUInt8(83);
    }

    @Override
    public String toString() {
        return String.format(
                "inode:[%d version:%d format:%d size:%d uid:%d gid:%d]",
                inodeNr, getVersion(), getRawFormat(), getSize(), getUid(), getGid());
    }

    /**
     * File modes.
     * TODO: review usage
     */
    public enum FileMode {
        // FILE PERMISSIONS
        OTHER_X(0x0007, 0x0001),
        OTHER_W(0x0007, 0x0002),
        OTHER_R(0x0007, 0x0004),
        GROUP_X(0x0038, 0x0008),
        GROUP_W(0x0038, 0x0010),
        GROUP_R(0x0038, 0x0020),
        USER_X(0x01c0, 0x0040),
        USER_W(0x01c0, 0x0080),
        USER_R(0x01c0, 0x0100),
        //TODO: Check mask
//        STICKY_BIT(0xFFFF,0x0200),
//        SET_GID(0xFFFF,0x0400),
//        SET_UID(0xFFFF,0x0800),
        // FILE TYPE
        NAMED_PIPE(0xf000, 0x1000),
        CHARACTER_DEVICE(0xf000, 0x2000),
        DIRECTORY(0xf000, 0x4000),
        BLOCK_DEVICE(0xf000, 0x6000),
        FILE(0xf000, 0x8000),
        SYM_LINK(0xf000, 0xa000),
        SOCKET(0xf000, 0xc000);

        /**
         * The mask.
         */
        final int mask;

        /**
         * The value.
         */
        final int val;

        FileMode(int mask, int val) {
            this.mask = mask;
            this.val = val;
        }

        public boolean isSet(int data) {
            return (data & mask) == val;
        }

        public static List<FileMode> getModes(int data) {
            return Arrays.stream(FileMode.values()).filter(mode -> mode.isSet(data)).collect(Collectors.toList());
        }
    }

    /**
     * <p>inode format values.</p>
     *
     * <pre>
     * typedef enum xfs_dinode_fmt {
     *     XFS_DINODE_FMT_DEV,
     *     XFS_DINODE_FMT_LOCAL,
     *     XFS_DINODE_FMT_EXTENTS,
     *     XFS_DINODE_FMT_BTREE,
     *     XFS_DINODE_FMT_UUID,
     *     XFS_DINODE_FMT_RMAP,
     * } xfs_dinode_fmt_t;
     * </pre>
     */
    public enum Format {
        /**
         * Character and block devices.
         */
        DEV,

        /**
         * All metadata associated with the file is within the inode.
         */
        LOCAL,

        /**
         * The inode contains an array of extents to other filesystem blocks which contain
         * the associated metadata or data.
         */
        EXTENTS,

        /**
         * The inode contains a B+tree root node which points to filesystem blocks containing
         * the metadata or data.
         */
        BTREE,

        /**
         * Defined, but currently not used.
         */
        UUID,

        /**
         * A reverse-mapping B+tree is rooted in the fork.
         */
        RMAP,

        /**
         * Unknown
         */
        UNKNOWN;

        public static Format fromValue(int value) {
            return Arrays.stream(values())
                    .filter(fmt -> fmt.ordinal() == value)
                    .findFirst()
                    .orElse(UNKNOWN);
        }
    }
}
