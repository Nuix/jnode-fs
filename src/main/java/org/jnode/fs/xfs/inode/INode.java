package org.jnode.fs.xfs.inode;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jnode.fs.FSAttribute;
import org.jnode.fs.util.FSUtils;
import org.jnode.fs.xfs.XfsConstants;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.attribute.XfsLeafOrNodeAttributeReader;
import org.jnode.fs.xfs.attribute.XfsShortFormAttribute;
import org.jnode.fs.xfs.extent.DataExtent;

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
@Slf4j
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
        return FileMode.fromValue(getMode());
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
        return getUInt32(76); // xfs_extnum_t
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
     * Gets the {@link List} of {@link InodeFlags}s for the inode.
     *
     * @return the {@link List} of {@link InodeFlags}s for the inode.
     */
    public List<InodeFlags> getFlags() {
        return InodeFlags.fromValue(getRawFlags());
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
        return FSUtils.toNormalizedString(buffer.array());
    }

    /**
     * Gets all the entries of the current b+tree directory, and the index of the leaf extent.
     *
     * @return the list of extents entries and the index of the leaf extent.
     */
    @Nonnull
    public ExtentInfo getExtentInfo() {
        int extentOffset = getOffset() + getDataOffset();
        int extentCount = (int) getExtentCount();

        //The “leaf” block has a special offset defined by XFS_DIR2_LEAF_OFFSET. Currently, this is 32GB and in the
        //extent view, a block offset of 32GB / sb_blocksize. On a 4KB block filesystem, this is 0x800000 (8388608
        //decimal).
        long leafOffset = XfsConstants.BYTES_IN_32G / fs.getSuperblock().getBlockSize();
        int leafExtentIndex = -1;

        ArrayList<DataExtent> list = new ArrayList<>(extentCount);
        for (int i = 0; i < extentCount; i++) {
            DataExtent info = new DataExtent(this.getData(), extentOffset);
            if (leafExtentIndex == -1 && info.getStartOffset() == leafOffset) {
                leafExtentIndex = i;
            }
            list.add(info);
            extentOffset += DataExtent.PACKED_LENGTH;
        }
        return new ExtentInfo(list, leafExtentIndex);
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
     * Read the header first to get the number of entries that can be found in this structure, then get them one by one.
     * <pre>
     *   struct xfs_attr_sf_hdr {
     *     __be16 totsize;
     *     __u8 count;
     *   } hdr;
     * </pre>
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
            offset = attribute.getOffset();
        }

        return attributes;
    }

    @Override
    public String toString() {
        return String.format(
                "inode:[%d version:%d format:%d size:%d uid:%d gid:%d]",
                inodeNumber, getVersion(), getRawFormat(), getSize(), getUid(), getGid());
    }

    @Getter
    @AllArgsConstructor(access = AccessLevel.PRIVATE)
    public static class ExtentInfo {
        private final List<DataExtent> extents;
        private final int leafExtentIndex;
    }
}
