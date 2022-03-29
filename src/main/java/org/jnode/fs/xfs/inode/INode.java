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
 * An XFS inode ('xfs_dinode_core').
 *
 * <pre>
 * struct xfs_dinode_core {
 *     __uint16_t       di_magic;
 *     __uint16_t       di_mode;
 *     __int8_t         di_version;
 *     __int8_t         di_format;
 *     __uint16_t       di_onlink;
 *     __uint32_t       di_uid;
 *     __uint32_t       di_gid;
 *     __uint32_t       di_nlink;
 *     __uint16_t       di_projid;
 *     __uint16_t       di_projid_hi;
 *     __uint8_t        di_pad[6];
 *     __uint16_t       di_flushiter;
 *     xfs_timestamp_t  di_atime;
 *     xfs_timestamp_t  di_mtime;
 *     xfs_timestamp_t  di_ctime;
 *     xfs_fsize_t      di_size;
 *     xfs_rfsblock_t   di_nblocks;
 *     xfs_extlen_t     di_extsize;
 *     xfs_extnum_t     di_nextents;
 *     xfs_aextnum_t    di_anextents;
 *     __uint8_t        di_forkoff;
 *     __int8_t         di_aformat;
 *     __uint32_t       di_dmevmask;
 *     __uint16_t       di_dmstate;
 *     __uint16_t       di_flags;
 *     __uint32_t       di_gen;
 *
 *     // di_next_unlinked is the only non-core field in the old dinode
 *     __be32           di_next_unlinked;
 *
 *     // version 5 filesystem (inode version 3) fields start here
 *    __le32            di_crc;
 *    __be64            di_changecount;
 *    __be64            di_lsn;
 *    __be64            di_flags2;
 *    __be32            di_cowextsize;
 *    __u8              di_pad2[12];
 *    xfs_timestamp_t   di_crtime;
 *    __be64            di_ino;
 *    uuid_t            di_uuid;
 * };
 * </pre>
 *
 * @author Luke Quinane
 */
public class INode extends XfsObject {

    /**
     * The magic number ('IN').
     */
    static final long MAGIC = 0x494e;

    /**
     * The offset to the inode data.
     */
    private static final int DATA_OFFSET = 100;

    /**
     * The logger implementation.
     */
    private static final Logger logger = LoggerFactory.getLogger(INode.class);

    /**
     * The {@link XfsFileSystem}.
     */
    protected final XfsFileSystem fs;

    /**
     * The inode number.
     */
    private final long inodeNumber;

    /**
     * Creates a new inode.
     *
     * @param inodeNumber the number.
     * @param data        the data.
     * @param offset      the offset to this inode in the data.
     */
    INode(long inodeNumber, byte[] data, int offset, XfsFileSystem fs) {
        super(data, offset);
        this.fs = fs;
        this.inodeNumber = inodeNumber;
    }

    /**
     * Gets the inode number.
     *
     * @return the inode number.
     */
    public long getINodeNumber() {
        return inodeNumber;
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
        return getUInt16(2);
    }

    /**
     * Gets the file modes from the inode.
     *
     * @return a {@link List} of {@link FileMode}s from the inode.
     */
    public List<FileMode> getFileModes() {
        return FileMode.getModes(getMode());
    }

    /**
     * Gets the version.
     *
     * @return the version.
     */
    public int getVersion() {
        return getUInt8(4);
    }

    /**
     * Gets the raw format value for this inode.
     *
     * @return the raw format value for this inode.
     */
    public int getRawFormat() {
        return getUInt8(5);
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
        // Link count stored in di_onlink
        return getUInt16(6);
    }

    /**
     * Gets the user-id of the owner.
     *
     * @return the user-id.
     */
    public long getUid() {
        return getUInt32(8);
    }

    /**
     * Gets the group-id of the owner.
     *
     * @return the group-id.
     */
    public long getGid() {
        return getUInt32(12);
    }


    /**
     * Gets the flush counter.
     *
     * @return the flush counter.
     */
    public int getFlushCounter() {
        return getUInt16(30);
    }

    /**
     * Last access time. Contains a POSIX timestamp in seconds.
     *
     * @return the access time - seconds portion.
     */
    public long getAccessTimeSec() {
        return getUInt32(32);
    }

    /**
     * Last access time - nanoseconds portion.
     *
     * @return the access time - nanoseconds portion.
     */
    public long getAccessTimeNsec() {
        return getUInt32(36);
    }

    /**
     * Last modification time. Contains a POSIX timestamp in seconds.
     *
     * @return the modified time - seconds portion.
     */
    public long getModifiedTimeSec() {
        return getUInt32(40);
    }

    /**
     * Last modification time - nanoseconds portion.
     *
     * @return the modified time - nanoseconds portion.
     */
    public long getModifiedTimeNsec() {
        return getUInt32(44);
    }

    /**
     * Last inode change time. Contains a POSIX timestamp in seconds.
     *
     * @return the last inode change time - seconds portion.
     */
    public long getInodeChangeTimeSec() {
        return getUInt32(48);
    }

    /**
     * Last inode change time - nanoseconds portion.
     *
     * @return the last inode change time - nanoseconds portion.
     */
    public long getInodeChangeTimeNsec() {
        return getUInt32(52);
    }

    /**
     * Gets the size.
     *
     * @return the size.
     */
    public long getSize() {
        return getInt64(56);
    }

    /**
     * Gets the number of blocks.
     *
     * @return the number of blocks.
     */
    public long getNumberOfBlocks() {
        return getUInt32(64);
    }

    /**
     * Gets the extent length.
     *
     * @return the extent length.
     */
    public long getExtentLength() {
        return getUInt32(72);
    }

    /**
     * Gets the number of extents.
     *
     * @return the extent count.
     */
    public long getExtentCount() {
        return getUInt32(76);
    }

    /**
     * Gets the attribute extent count.
     *
     * @return the attribute extent count.
     */
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

    /**
     * Gets the raw flags value.
     *
     * @return the raw flags value.
     */
    public int getRawFlags() {
        return getUInt16(90);
    }

    /**
     * Gets the {@link List} of {@link Flag}s for the inode.
     *
     * @return the {@link List} of {@link Flag}s for the inode.
     */
    public List<Flag> getFlags() {
        return Flag.fromValue(getRawFlags());
    }

    /**
     * Gets the generation number.
     *
     * @return the generation number.
     */
    public long getGenerationNumber() {
        return getUInt32(92);
    }

    /**
     * Gets the inode data offset.
     *
     * @return the offset to start reading data.
     */
    public int getDataOffset() {
        return DATA_OFFSET;
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
        System.arraycopy(getData(), getOffset() + getDataOffset(), buffer.array(), 0, (int) getSize());
        return new String(buffer.array(), StandardCharsets.UTF_8);
    }

    /**
     * Gets all the entries of the current b+tree directory.
     *
     * @return the list of extents entries.
     */
    public List<DataExtent> getExtentInfo() {
        long offset = getOffset() + getDataOffset();
        int count = (int) getExtentCount();
        ArrayList<DataExtent> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            DataExtent info = new DataExtent(this.getData(), (int) offset);
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
        long offset = getOffset() + getDataOffset() + (getAttributesForkOffset() * 8);
        Format attributesFormat = Format.fromValue(getAttributesFormat());
        if (attributesFormat == Format.LOCAL) {
            return getShortFormAttributes((int) offset);
        } else if (attributesFormat == Format.EXTENTS) {
            XfsLeafOrNodeAttributeReader attributeReader = new XfsLeafOrNodeAttributeReader(getData(), (int) offset, this, fs);
            return attributeReader.getAttributes();
        } else {
            logger.warn(">>> Pending implementation due to lack of examples for attribute format {} Found on Inode {}",
                    attributesFormat, inodeNumber);
        }
        return Collections.emptyList();
    }

    /**
     * Gets the {@link List} of {@link XfsShortFormAttribute}s.
     *
     * @param offset the offset to start reading data from.
     * @return the {@link List} of {@link XfsShortFormAttribute}s.
     */
    private List<FSAttribute> getShortFormAttributes(int offset) {
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


    @Override
    public String toString() {
        return String.format(
                "inode:[%d version:%d format:%d size:%d uid:%d gid:%d]",
                inodeNumber, getVersion(), getRawFormat(), getSize(), getUid(), getGid());
    }

    /**
     * File modes.
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

    /**
     * <p>inode flags.</p>
     */
    public enum Flag {

        /**
         * The inode’s data is located on the real-time device.
         */
        REALTIME(0x01),

        /**
         * The inode’s extents have been preallocated.
         */
        PREALLOC(0x02),

        /**
         * Specifies the sb_rbmino uses the new real-time bitmap
         * format.
         */
        NEWRTBM(0x04),

        /**
         * Specifies the inode cannot be modified.
         */
        IMMUTABLE(0x08),

        /**
         * The inode is in append only mode.
         */
        APPEND(0x10),

        /**
         * The inode is written synchronously.
         */
        SYNC(0x20),

        /**
         * The inode’s di_atime is not updated.
         */
        NOATIME(0x40),

        /**
         * Specifies the inode is to be ignored by xfsdump.
         */
        NODUMP(0x80),

        /**
         * For directory inodes, new inodes inherit the
         * XFS_DIFLAG_REALTIME bit.
         */
        RTINHERIT(0x100),

        /**
         * For directory inodes, new inodes inherit the di_projid
         * value.
         */
        PROJINHERIT(0x200),

        /**
         * For directory inodes, symlinks cannot be created.
         */
        NOSYMLINKS(0x400),

        /**
         * Specifies the extent size for real-time files or an extent size
         * hint for regular files.
         */
        EXTSIZE(0x800),

        /**
         * For directory inodes, new inodes inherit the di_extsize
         * value.
         */
        EXTSZINHERIT(0x1000),

        /**
         * Specifies the inode is to be ignored when defragmenting
         * the filesystem.
         */
        NODEFRAG(0x2000),

        /**
         * Use the filestream allocator. The filestreams allocator
         * allows a directory to reserve an entire allocation group for
         * exclusive use by files created in that directory. Files in
         * other directories cannot use AGs reserved by other
         * directories
         */
        FILESTREAMS(0x4000);

        private final int bitValue;

        Flag(int bitValue) {
            this.bitValue = bitValue;
        }

        public boolean isSet(int value) {
            return (bitValue & value) == bitValue;
        }

        public static List<Flag> fromValue(int value) {
            return Arrays.stream(values())
                    .filter(f -> f.isSet(value))
                    .collect(Collectors.toList());
        }
    }
}
