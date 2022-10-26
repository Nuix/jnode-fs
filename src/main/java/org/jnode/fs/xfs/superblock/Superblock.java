package org.jnode.fs.xfs.superblock;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jnode.fs.FileSystemException;
import org.jnode.fs.util.FSUtils;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsRecord;

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
@Slf4j
@Getter
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
     * The size of a basic unit of space allocation in bytes. Typically, this is 4096 (4KB) but can range from 512 to
     * 65536 bytes.
     */
    private final long blockSize; // sb_blocksize

    /**
     * Total number of blocks available for data and metadata on the filesystem.
     */
    private final long dataBlockCount; // sb_dblocks

    /**
     * Number blocks in the real-time disk device. Refer to real-time sub-volumes for more information.
     */
    private final long realTimeBlockCount; // sb_rblocks

    /**
     * Number of extents on the real-time device.
     */
    private final long realTimeExtentCount; // sb_rextents

    /**
     * UUID (Universally Unique ID) for the filesystem. Filesystems can be mounted by the UUID instead of device
     * name.
     */
    UUID uuid; // sb_uuid

    /**
     * First block number for the journaling log if the log is internal (ie. not on a separate disk device). For an external
     * log device, this will be zero (the log will also start on the first block on the log device). The identity of the log
     * devices is not recorded in the filesystem, but the UUIDs of the filesystem and the log device are compared to
     * prevent corruption.
     */
    private final long logStart; // sb_logstart

    /**
     * Root inode number for the filesystem. Normally, the root inode is at the start of the first possible inode chunk
     * in AG 0. This is 128 when using a 4KB block size.
     */
    private final long rootInodeNumber; // sb_rootino

    /**
     * Bitmap inode for real-time extents.
     */
    private final long realTimeBitmapInode; // sb_rbmino

    /**
     * Summary inode for real-time bitmap.
     */
    private final long realTimeSummaryInode; // sb_rsumino

    /**
     * Realtime extent size in blocks.
     */
    private final long realTimeExtentSize; // sb_rextsize

    /**
     * Size of each AG in blocks. For the actual size of the last AG, refer to the free space agf_length value.
     */
    private final long agBlockSize; // sb_agblocks

    /**
     * Number of AGs in the filesystem.
     */
    private final long agCount; // sb_agcount

    /**
     * Number of real-time bitmap blocks.
     */
    private final long realTimeBitmapBlockCount; // sb_rbmblocks

    /**
     * Number of blocks for the journaling log.
     */
    private final long logBlockCount; // sb_logblocks

    /**
     * Filesystem version number. This is a bitmask specifying the features enabled when creating the filesystem.
     * Any disk checking tools or drivers that do not recognize any set bits must not operate upon the filesystem.
     * Most of the flags indicate features introduced over time. If the value of the lower nibble is >= 4, the higher bits
     * indicate feature flags as {@link VersionFlags}.
     * If the lower nibble of this value is 5, then this is a v5 filesystem; the XFS_SB_VERSION2_CRCBIT feature must
     * be set in sb_features2.
     */
    private final int versionNumber; // sb_versionnum

    /**
     * Specifies the underlying disk sector size in bytes. Typically, this is 512 or 4096 bytes. This determines the
     * minimum I/O alignment, especially for direct I/O.
     */
    private final int sectorSize; // sb_sectsize

    /**
     * Size of the inode in bytes. The default is 256 (2 inodes per standard sector) but can be made as large as 2048
     * bytes when creating the filesystem. On a v5 filesystem, the default and minimum inode size are both 512 bytes.
     */
    private final int inodeSize; // sb_inodesize

    /**
     * Number of inodes per block. This is equivalent to sb_blocksize / sb_inodesize.
     */
    private final int inodePerBlock; // sb_inopblock

    /**
     * Name for the filesystem. This value can be used in the mount command.
     */
    private final String fileSystemName; // sb_fname[12]

    /**
     * log2 value of sb_blocksize. In other terms, sb_blocksize = 2 sb_blocklog.
     */
    private final int blockSizeLog2; // sb_blocklog

    /**
     * log2 value of sb_sectsize.
     */
    private final int sectorSizeLog2; // sb_sectlog

    /**
     * log2 value of sb_inodesize.
     */
    private final int inodeSizeLog2; // sb_inodelog

    /**
     * log2 value of sb_inopblock.
     */
    private final int iNodePerBlockLog2; // sb_inopblog

    /**
     * log2 value of sb_agblocks (rounded up). This value is used to generate inode numbers and absolute block
     * numbers defined in extent maps.
     */
    private final int agSizeLog2; // sb_agblklog

    /**
     * log2 value of sb_rextents.
     */
    private final int realTimeExtentSizeLog2; // sb_rextslog

    /**
     * Flag specifying that the filesystem is being created.
     */
    private final int inProgressFlag; // sb_inprogress

    /**
     * Maximum percentage of filesystem space that can be used for inodes. The default value is 5%.
     */
    private final int maxInodePercentage; // sb_imax_pct

    /**
     * Global count for number inodes allocated on the filesystem. This is only maintained in the first superblock.
     */
    private final long allocatedInodeCount; // sb_icount

    /**
     * Global count of free inodes on the filesystem. This is only maintained in the first superblock.
     */
    private final long freeInodeCount; // sb_ifree

    /**
     * Global count of free data blocks on the filesystem. This is only maintained in the first superblock.
     */
    private final long freeDataBlockCount; // sb_fdblocks

    /**
     * Global count of free real-time extents on the filesystem. This is only maintained in the first superblock.
     */
    private final long freeRealTimeExtentCount; // sb_frextents

    /**
     * Inode for user quotas. This and the following two quota fields only apply if XFS_SB_VERSION_QUOTABIT
     * flag is set in sb_versionnum. Refer to quota inodes for more information
     */
    private final long userQuotaInode; // sb_uquotino

    /**
     * Inode for group or project quotas. Group and Project quotas cannot be used at the same time.
     */
    private final long groupQuotaInode; // sb_gquotino

    /**
     * Quota flags.
     */
    private final int quotaFlagNumber; // sb_qflags

    /**
     * Miscellaneous flags.
     */
    private final int flags; // sb_flags

    /**
     * Reserved and must be zero (“vn” stands for version number).
     */
    private final int sharedVersionNumber; // sb_shared_vn

    /**
     * Inode chunk alignment in fsblocks. Prior to v5, the default value provided for inode chunks to have an 8KiB
     * alignment. Starting with v5, the default value scales with the multiple of the inode size over 256 bytes. Concretely,
     * this means an alignment of 16KiB for 512-byte inodes, 32KiB for 1024-byte inodes, etc. If sparse inodes
     * are enabled, the ir_startino field of each inode B+tree record must be aligned to this block granularity,
     * even if the inode given by ir_startino itself is sparse.
     */
    private final long inodeChunkAlignment; // sb_inoalignmt

    /**
     * Underlying stripe or raid unit in blocks.
     */
    private final long unit; // sb_unit

    /**
     * Underlying stripe or raid width in blocks.
     */
    private final long width; // sb_width

    /**
     * log2 multiplier that determines the granularity of directory block allocations in fsblocks.
     */
    private final int directoryBlockLog2; // sb_dirblklog

    /**
     * log2 value of the log subvolume’s sector size. This is only used if the journaling log is on a separate disk device
     * (i.e. not internal).
     */
    private final int externalLogSectorSizeLog2; // sb_logsectlog

    /**
     * The log’s sector size in bytes if the filesystem uses an external log device.
     */
    private final int externalLogSectorSize; // sb_logsectsize

    /**
     * The log device’s stripe or raid unit size. This only applies to version 2 logs XFS_SB_VERSION_LOGV2BIT
     * is set in sb_versionnum.
     */
    private final long logUnitSize; // sb_logsunit

    /**
     * Additional version flags if XFS_SB_VERSION_MOREBITSBIT is set in sb_versionnum. The currently
     * defined additional features include {@link AdditionalVersionFlags}
     */
    private final long feature2; // sb_features2

    /**
     * This field mirrors sb_features2, due to past 64-bit alignment errors.
     */
    private final long additionalFeatureFlagsMirror; // sb_bad_features2

    // version 5 superblock fields start here

    /**
     * Read-write compatible feature flags. The kernel can still read and write this FS even if it doesn’t understand
     * the flag. Currently, there are no valid flags.
     */
    private final long compat; // sb_features_compat

    /**
     * Read-only compatible feature flags. The kernel can still read this FS even if it doesn’t understand the flag.
     */
    private final long roCompat; // sb_features_ro_compat

    /**
     * Read-write incompatible feature flags. The kernel cannot read or write this FS if it doesn’t understand the flag.
     */
    private final long incompat; // sb_features_incompat

    /**
     * Read-write incompatible feature flags for the log. The kernel cannot read or write this FS log if it doesn’t
     * understand the flag. Currently, no flags are defined.
     */
    private final long logIncompat; // sb_features_log_incompat

    /**
     * Superblock checksum.
     */
    private final long superblockChecksum; // sb_crc

    /**
     * Sparse inode alignment, in fsblocks. Each chunk of inodes referenced by a sparse inode B+tree record must be
     * aligned to this block granularity.
     */
    private final long sparseInodeAlignment; // sb_spino_align

    /**
     * Project quota inode.
     */
    private final long projectQuotaInode; // sb_pquotino

    /**
     * Log sequence number of the last superblock update.
     */
    private final long logSequenceNumber; // sb_lsn

    /**
     * If the XFS_SB_FEAT_INCOMPAT_META_UUID feature is set, then the UUID field in all metadata blocks
     * must match this UUID. If not, the block header UUID field must match sb_uuid.
     */
    private final UUID metadataUuid; // sb_meta_uuid

    /**
     * If the XFS_SB_FEAT_RO_COMPAT_RMAPBT feature is set and a real-time device is present (sb_rblocks
     * > 0), this field points to an inode that contains the root to the Real-Time Reverse Mapping B+tree. This field is
     * zero otherwise.
     */
    private final long realTimeReverseMappingBTreeInode; // sb_rrmapino

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
        skipBytes(4); // the magic
        blockSize = readUInt32(); // sb_blocksize

        dataBlockCount = readInt64(); // sb_dblocks
        realTimeBlockCount = readInt64(); // sb_rblocks
        realTimeExtentCount = readInt64(); // sb_rextents

        long upperValue = readInt64();
        long lowerValue = readInt64();
        uuid = new UUID(upperValue, lowerValue); // sb_uuid

        logStart = readInt64(); // sb_logstart
        rootInodeNumber = readInt64(); // sb_rootino
        realTimeBitmapInode = readInt64(); // sb_rbmino
        realTimeSummaryInode = readInt64(); // sb_rsumino

        // Interestingly, the entire superblock seems to be 64-bit (8-byte) alignment, but there are FIVE 32-bit here...
        // Well, the fileSystemName takes 12 bytes, so the entire superblock is still 8-byte alignment over all.
        realTimeExtentSize = readUInt32(); // sb_rextsize
        agBlockSize = readUInt32(); // sb_agblocks
        agCount = readUInt32(); // sb_agcount
        realTimeBitmapBlockCount = readUInt32(); // sb_rbmblocks
        logBlockCount = readUInt32(); // sb_logblocks

        versionNumber = readUInt16(); // sb_versionnum, a bitmask of VersionFlags
        sectorSize = readUInt16(); // sb_sectsize
        inodeSize = readUInt16(); // sb_inodesize
        inodePerBlock = readUInt16(); // sb_inopblock

        int nameLength = 12;
        byte[] buffer = new byte[nameLength];
        System.arraycopy(getData(), getOffset(), buffer, 0, nameLength);
        fileSystemName = FSUtils.toNormalizedString(buffer); // sb_fname[12]
        skipBytes(nameLength);

        blockSizeLog2 = readUInt8(); // sb_blocklog
        sectorSizeLog2 = readUInt8(); // sb_sectlog
        inodeSizeLog2 = readUInt8(); // sb_inodelog
        iNodePerBlockLog2 = readUInt8(); // sb_inopblog
        agSizeLog2 = readUInt8(); // sb_agblklog
        realTimeExtentSizeLog2 = readUInt8(); // sb_rextslog
        inProgressFlag = readUInt8(); // sb_inprogress
        maxInodePercentage = readUInt8(); // sb_imax_pct

        allocatedInodeCount = readInt64(); // sb_icount
        freeInodeCount = readInt64(); // sb_ifree
        freeDataBlockCount = readInt64(); // sb_fdblocks
        freeRealTimeExtentCount = readInt64(); // sb_frextents
        userQuotaInode = readInt64(); // sb_uquotino
        groupQuotaInode = readInt64(); // sb_gquotino

        quotaFlagNumber = readUInt16(); // sb_qflags
        flags = readUInt8(); // sb_flags
        sharedVersionNumber = readUInt8(); // sb_shared_vn
        inodeChunkAlignment = readUInt32(); // sb_inoalignmt

        unit = readUInt32(); // sb_unit
        width = readUInt32(); // sb_width

        directoryBlockLog2 = readUInt8(); // sb_dirblklog
        externalLogSectorSizeLog2 = readUInt8(); // sb_logsectlog
        externalLogSectorSize = readUInt16(); // sb_logsectsize
        logUnitSize = readUInt32(); // sb_logsunit

        feature2 = readUInt32(); // sb_features2, the bitmask of Features2
        additionalFeatureFlagsMirror = readUInt32(); // sb_bad_features2

        // version 5 superblock fields start here
        compat = readUInt32(); // sb_features_compat
        roCompat = readUInt32(); // sb_features_ro_compat
        incompat = readUInt32(); // sb_features_incompat
        logIncompat = readUInt32(); // sb_features_log_incompat
        superblockChecksum = readUInt32(); // sb_crc
        sparseInodeAlignment = readUInt32(); // sb_spino_align

        projectQuotaInode = readInt64(); // sb_pquotino
        // sb_lsn is "Log sequence number of the last superblock update". But there is no definition of the type "xfs_lsn_t" in the document.
        // It is "typedef	__int64_t	xfs_lsn_t" in https://github.com/torvalds/linux/blob/master/fs/xfs/libxfs/xfs_types.h#L23
        logSequenceNumber = readInt64(); // sb_lsn

        long upperMetaValue = readInt64();
        long lowerMetaValue = readInt64();
        metadataUuid = new UUID(upperMetaValue, lowerMetaValue); // sb_meta_uuid

        realTimeReverseMappingBTreeInode = readInt64(); // sb_rrmapino
    }

    /**
     * Gets the version value from sb_versionnum.
     *
     * @return the features.
     */
    public int getVersion() {
        return getVersionNumber() & 0xF;
    }

    @Override
    public String toString() {
        return String.format(
                "xfs-sb:[block-size:%d inode-size:%d root-ino:%d ag-size:%d ag-count: %d version:%d features2:0x%x]",
                blockSize, inodeSize, rootInodeNumber, agBlockSize, agCount, getVersion(), feature2);
    }

    /**
     * Gets the {@link List} of {@link VersionFlags} from the version value sb_versionnum.
     *
     * @return a {@link List} of {@link VersionFlags} from the version value sb_versionnum.
     * @see #getVersionNumber()
     */
    public List<VersionFlags> getVersionFlags() {
        return VersionFlags.fromValue(versionNumber);
    }

    /**
     * Gets the {@link List} of {@link AdditionalVersionFlags} from sb_features2.
     *
     * @return a {@link List} of {@link AdditionalVersionFlags} from sb_features2.
     */
    public List<AdditionalVersionFlags> getAdditionalVersionFlags() {
        return AdditionalVersionFlags.fromValue(feature2);
    }

    /**
     * Gets the {@link List} of {@link QuotaFlags} from sb_qflags.
     *
     * @return a {@link List} of {@link QuotaFlags} from sb_qflags.
     */
    public List<QuotaFlags> getQuotaFlags() {
        return QuotaFlags.fromValue(quotaFlagNumber);
    }

    /**
     * Gets the {@link List} of {@link MiscFlags} from sb_flags.
     *
     * @return a {@link List} of {@link MiscFlags} from sb_flags.
     */
    public List<MiscFlags> getMiscFlags() {
        return MiscFlags.fromValue(flags);
    }

    /**
     * Gets the raw value of sb_features_compat.
     * The doc says "Currently, there are no valid flags."
     *
     * @return the raw value of from sb_features_compat.
     */
    public long getReadWriteCompatibilityFlags() {
        return compat;
    }

    /**
     * Gets the {@link List} of {@link ReadOnlyCompatibilityFlags} from sb_features_ro_compat.
     *
     * @return a {@link List} of {@link ReadOnlyCompatibilityFlags} from sb_features_ro_compat.
     */
    public List<ReadOnlyCompatibilityFlags> getReadOnlyCompatibilityFlags() {
        return ReadOnlyCompatibilityFlags.fromValue(roCompat);
    }

    /**
     * Gets the {@link List} of {@link ReadWriteIncompatibilityFlags} from sb_features_incompat.
     *
     * @return a {@link List} of {@link ReadWriteIncompatibilityFlags} from sb_features_incompat.
     */
    public List<ReadWriteIncompatibilityFlags> getReadWriteIncompatibilityFlags() {
        return ReadWriteIncompatibilityFlags.fromValue(incompat);
    }

    /**
     * Gets the raw value of sb_features_log_incompat.
     * The doc says "Currently, no flags are defined."
     *
     * @return the raw value of sb_features_log_incompat.
     */
    public long getLogReadWriteIncompatibilityFlags() {
        return logIncompat;
    }
}
