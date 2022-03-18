package org.jnode.fs.xfs;

import org.jnode.fs.FileSystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <p>The XFS superblock.</p>
 *
 * <pre>
 * struct xfs_sb
 * {
 *     __uint32_t sb_magicnum;
 *     __uint32_t sb_blocksize;
 *     xfs_rfsblock_t sb_dblocks;
 *     xfs_rfsblock_t sb_rblocks;
 *     xfs_rtblock_t sb_rextents;
 *     uuid_t sb_uuid;
 *     xfs_fsblock_t sb_logstart;
 *     xfs_ino_t sb_rootino;
 *     xfs_ino_t sb_rbmino;
 *     xfs_ino_t sb_rsumino;
 *     xfs_agblock_t sb_rextsize;
 *     xfs_agblock_t sb_agblocks;
 *     xfs_agnumber_t sb_agcount;
 *     xfs_extlen_t sb_rbmblocks;
 *     xfs_extlen_t sb_logblocks;
 *     __uint16_t sb_versionnum;
 *     __uint16_t sb_sectsize;
 *     __uint16_t sb_inodesize;
 *     __uint16_t sb_inopblock;
 *     char sb_fname[12];
 *     __uint8_t sb_blocklog;
 *     __uint8_t sb_sectlog;
 *     __uint8_t sb_inodelog;
 *     __uint8_t sb_inopblog;
 *     __uint8_t sb_agblklog;
 *     __uint8_t sb_rextslog;
 *     __uint8_t sb_inprogress;
 *     __uint8_t sb_imax_pct;
 *     __uint64_t sb_icount;
 *     __uint64_t sb_ifree;
 *     __uint64_t sb_fdblocks;
 *     __uint64_t sb_frextents;
 *     xfs_ino_t sb_uquotino;
 *     xfs_ino_t sb_gquotino;
 *     __uint16_t sb_qflags;
 *     __uint8_t sb_flags;
 *     __uint8_t sb_shared_vn;
 *     xfs_extlen_t sb_inoalignmt;
 *     __uint32_t sb_unit;
 *     __uint32_t sb_width;
 *     __uint8_t sb_dirblklog;
 *     __uint8_t sb_logsectlog;
 *     __uint16_t sb_logsectsize;
 *     __uint32_t sb_logsunit;
 *     __uint32_t sb_features2;
 *     __uint32_t sb_bad_features2;
 *     // version 5 superblock fields start here
 *     __uint32_t sb_features_compat;
 *     __uint32_t sb_features_ro_compat;
 *     __uint32_t sb_features_incompat;
 *     __uint32_t sb_features_log_incompat;
 *     __uint32_t sb_crc;
 *     xfs_extlen_t sb_spino_align;
 *     xfs_ino_t sb_pquotino;
 *     xfs_lsn_t sb_lsn;
 *     uuid_t sb_meta_uuid;
 *     xfs_ino_t sb_rrmapino;
 * }
 * </pre>
 *
 * @author Luke Quinane
 */
public class Superblock extends XfsRecord {

    /**
     * The size of the super block.
     */
    public static final int SUPERBLOCK_LENGTH = 512;

    /**
     * The super block magic number ('XFSB').
     */
    public static final long XFS_SUPER_MAGIC = 0x58465342;

    /**
     * The logger.
     */
    private static final Logger logger = LoggerFactory.getLogger(Superblock.class);

    /**
     * The block size.
     */
    private final long blockSize;

    /**
     * The inode size.
     */
    private final int iNodeSize;

    /**
     * The allocation group size value in log2.
     */
    private final int aGSizeLog2;

    /**
     * The number of inodes per block in log2.
     */
    private final int iNodePerBlockLog2;

    /**
     * The UUID for the filesystem.
     */
    private final UUID uuid;

    /**
     * If the XFS_SB_FEAT_INCOMPAT_META_UUID feature is set, then the UUID field in all metadata blocks must match
     * this UUID. If not, the block header UUID field must match sb_uuid.
     */
    private final UUID metadataUuid;

    /**
     * The name for the filesystem.
     */
    private final String name;

    /**
     * Creates a new super block.
     *
     * @param fileSystem the file system to read from.
     * @throws FileSystemException if an error occurs reading in the super block.
     */
    public Superblock(XfsFileSystem fileSystem) throws FileSystemException {
        super(new byte[SUPERBLOCK_LENGTH], 0);

        try {
            logger.debug("Reading XFS super block");

            ByteBuffer buffer = ByteBuffer.allocate(SUPERBLOCK_LENGTH);
            fileSystem.getApi().read(0, buffer);
            buffer.position(0);
            buffer.get(getData());

            if (getMagic() != XFS_SUPER_MAGIC) {
                throw new FileSystemException("Wrong magic number for XFS: " + getMagic());
            }
        } catch (IOException e) {
            throw new FileSystemException(e);
        }
        blockSize = getUInt32(4);
        iNodeSize = getUInt16(104);
        aGSizeLog2 = getUInt8(124);
        iNodePerBlockLog2 = getUInt8(123);

        long upperValue = getInt64(32);
        long lowerValue = getInt64(40);
        uuid = new UUID(upperValue, lowerValue);

        upperValue = getInt64(248);
        lowerValue = getInt64(256);
        metadataUuid = new UUID(upperValue, lowerValue);

        byte[] buffer = new byte[12];
        System.arraycopy(getData(), getOffset() + 108, buffer, 0, buffer.length);
        name = new String(buffer, StandardCharsets.UTF_8).replace("\0", "");

    }

    /**
     * Gets the block size stored in the super block.
     *
     * @return the block size.
     */
    public long getBlockSize() {
        return blockSize;
    }

    /**
     * Gets the total block size stored in the super block.
     *
     * @return the total block size.
     */
    public long getTotalBlocks() {
        return getInt64(8);
    }

    /**
     * Gets the total free block size stored in the super block.
     *
     * @return the free block size.
     */
    public long getFreeBlocks() {
        return getInt64(144);
    }

    /**
     * Gets the UUID for the volume stored in the super block.
     *
     * @return the UUID.
     */
    public UUID getUuid() {
        return uuid;
    }

    /**
     * Gets the metadata UUID.
     *
     * @return the metadata UUID.
     */
    public UUID getMetadataUuid() {
        return metadataUuid;
    }

    /**
     * Gets the root inode.
     *
     * @return the root inode.
     */
    public long getRootInode() {
        return getInt64(56);
    }

    /**
     * Gets the size of each allocation group in blocks.
     *
     * @return the size in blocks.
     */
    public long getAGSize() {
        return getUInt32(84);
    }

    /**
     * Gets the number of allocation groups in the file system.
     *
     * @return the number of allocation groups.
     */
    public long getAGCount() {
        return getUInt32(88);
    }

    /**
     * Gets the version value from sb_versionnum.
     *
     * @return the features.
     */
    public int getVersion() {
        return getRawVersion() & 0xF;
    }

    /**
     * Gets the raw version value sb_versionnum.
     *
     * @return the raw version value.
     */
    public int getRawVersion() {
        return getUInt16(100);
    }

    /**
     * Gets the {@link List} of {@link VersionFlags} from the version value sb_versionnum.
     *
     * @return a {@link List} of {@link VersionFlags} from the version value sb_versionnum.
     * @see #getRawVersion()
     */
    public List<VersionFlags> getVersionFlags() {
        return VersionFlags.fromValue(getRawVersion());
    }

    /**
     * Gets the size of inodes in the file system.
     *
     * @return the inode size.
     */
    public int getInodeSize() {
        return iNodeSize;
    }

    /**
     * Gets the number of inodes per block (should be the same as block size / inode size).
     *
     * @return the inodes per block.
     */
    public int getInodesPerBlock() {
        return getUInt16(0x6a);
    }

    /**
     * Gets the name for the file system.
     *
     * @return the name.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the number of inodes currently allocation in the file system.
     *
     * @return the number of allocated inodes.
     */
    public long getInodeCount() {
        return getInt64(0x80);
    }

    /**
     * Gets the number of inodes currently allocation in the file system.
     *
     * @return the number of allocated inodes.
     */
    public long getInodeAlignment() {
        return getUInt32(0xb4);
    }

    /**
     * Gets additional features value sb_features2.
     *
     * @return the additional features.
     */
    public long getRawFeatures2() {
        return getUInt32(0xc8);
    }

    /**
     * Gets the {@link List} of {@link Features2} from sb_features2.
     *
     * @return a {@link List} of {@link Features2} from sb_features2.
     */
    public List<Features2> getFeatures2() {
        return Features2.fromValue(getRawFeatures2());
    }

    /**
     * Allocation group size in log2, where
     * value = ( 2 ^ value in log2 ) or 0 if value in log2 is 0.
     */
    public long getAGSizeLog2() {
        return aGSizeLog2;
    }

    /**
     * Number of inodes per block in log2, where
     * value = ( 2 ^ value in log2 ) or 0 if value in log2 is 0
     */
    public long getINodePerBlockLog2() {
        return iNodePerBlockLog2;
    }

    /**
     * Directory block size in log2.
     */
    public long getDirectoryBlockSizeLog2() {
        return getUInt8(0xc0);
    }

    /**
     * Journal device sector size in log2.
     */
    public long getJournalDeviceSizeLog2() {
        return getUInt8(0xc1);
    }

    @Override
    public String toString() {
        return String.format(
                "xfs-sb:[block-size:%d inode-size:%d root-ino:%d ag-size:%d ag-count: %d version:%d features2:0x%x]",
                getBlockSize(), getInodeSize(), getRootInode(), getAGSize(), getAGCount(), getRawVersion(), getRawFeatures2());
    }

    /**
     * The version flags in the version value sb_versionnum.
     */
    public enum VersionFlags {

        /**
         * Set if any inode have extended attributes.
         */
        ATTRBIT(0x10),

        /**
         * Set if any inodes use 32-bit di_nlink values.
         */
        NLINKBIT(0x20),

        /**
         * Quotas are enabled on the filesystem. This also brings in the various quota fields in the superblock.
         */
        QUOTABIT(0x40),

        /**
         * Set if sb_inoalignmt is used.
         */
        ALIGNBIT(0x80),

        /**
         * Set if sb_unit and sb_width are used.
         */
        DALIGNBIT(0x100),

        /**
         * Set if sb_shared_vn is used.
         */
        SHAREDBIT(0x200),

        /**
         * Version 2 journaling logs are used.
         */
        LOGV2BIT(0x400),

        /**
         * Set if sb_sectsize is not 512.
         */
        SECTORBIT(0x800),

        /**
         * Unwritten extents are used. This is always set.
         */
        EXTFLGBIT(0x1000),

        /**
         * Version 2 directories are used. This is always set.
         */
        DIRV2BIT(0x2000),

        /**
         * Set if the sb_features2 field in the superblock contains more flags.
         */
        MOREBITSBIT(0x4000);

        private final int flag;

        VersionFlags(int flag) {
            this.flag = flag;
        }

        public boolean isSet(int value) {
            return (flag & value) == flag;
        }

        public static List<VersionFlags> fromValue(int value) {
            return Arrays.stream(values())
                    .filter(vf -> vf.isSet(value))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Flags from the sb_features2.
     */
    public enum Features2 {

        /**
         * Lazy global counters. Making a filesystem with this bit set can improve
         * performance. The global free space and inode counts are only updated in
         * the primary superblock when the filesystem is cleanly unmounted.
         */
        LAZYSBCOUNTBIT(0x01),

        /**
         * Extended attributes version 2. Making a filesystem with this optimises
         * the inode layout of extended attributes. If this bit is set and the noattr2
         * mount flag is not specified, the di_forkoff inode field will be dynamically
         * adjusted.
         */
        ATTR2BIT(0x02),

        /**
         * Parent pointers. All inodes must have an extended attribute that points
         * back to its parent node. The primary purpose for this information is in
         * backup systems.
         */
        PARENTBIT(0x04),

        /**
         * 32-bit Project ID. Inodes can be associated with a project ID number,
         * which can be used to enforce disk space usage quotas for a particular
         * group of directories.This flag indicates that project IDs can be 32 bits
         * in size.
         */
        PROJID32BIT(0x08),

        /**
         * Metadata checksumming. All metadata blocks have an extended header containing
         * the block checksum, a copy of the metadata UUID, the log sequence number of the
         * last update to prevent stale replays, and a back pointer to the owner of the
         * block. This feature must be and can only be set of the lowest nibble of
         * sb_versionnum is set to 5.
         */
        CRCBIT(0x10),

        /**
         * Directory file type. Each directory entry records the type of the inode to which
         * the entry points. This speeds up directory iteration by removing the need to load
         * every inode into memory.
         */
        FTYPE(0x20);

        private final int flag;

        Features2(int flag) {
            this.flag = flag;
        }

        public boolean isSet(long value) {
            return (flag & value) == flag;
        }

        public static List<Features2> fromValue(long value) {
            return Arrays.stream(values())
                    .filter(f2 -> f2.isSet(value))
                    .collect(Collectors.toList());
        }
    }
}
