package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MySuperblock extends MyXfsBaseAccessor {

    public static final long MAGIC_NUMBER = AsciiToHex("XFSB");

    public MySuperblock(FSBlockDeviceAPI devApi, long superBlockStart) {
        super(devApi, superBlockStart);
    }

    @Override
    public boolean isValidSignature() throws IOException {
        return getSignature() == MAGIC_NUMBER;
    }

    enum FeatureFlags {
        // Inodes support extended attributes
        ATTRBIT(0x0010),
        // Inodes has 32-bit number of links value
        NLINKBIT(0x0020),
        // Quotas enabled
        QUOTABIT(0x0040),
        // Use inode chunk alignment
        ALIGNBIT(0x0080),
        //Has underlying stripe or RAID
        DALIGNBIT(0x0100),
        //Unknown (set if reserved shared version is used)
        SHAREDBIT(0x0200),
        //Has version 2 journaling logs
        LOGV2BIT(0x0400),
        //Sector size is not 512 bytes
        SECTORBIT(0x0800),
        //Unwritten extents are used
        //        Should always be set.
        EXTFLGBIT(0x1000),
        //Version 2 directories are used
        //        Should always be set.
        DIRV2BIT(0x2000),
        //Unknown (ASCII only case-insensitive)
        BORGBIT(0x4000),
        // Secondary feature flags are used
        MOREBITSBIT(0x8000);

        private final int value;

        FeatureFlags(int val) {
            this.value = val;
        }

        public static List<FeatureFlags> getFlags(long flagsBinary) {
            return Arrays.stream(FeatureFlags.values())
                    .filter(flag -> (flag.value & flagsBinary) > 0)
                    .collect(Collectors.toList());
        }
    }

    /**
     * https://mirrors.edge.kernel.org/pub/linux/utils/fs/xfs/docs/xfs_filesystem_structure.pdf page 50
     */
    public String getXfsDbInspectionString() throws IOException {
        String str = "";
        str += "magicnum = " + Long.toOctalString(getMagicNum()) + "\n";
        str += "blocksize = " + getBlockSize() + "\n";
        str += "dblocks = " + getTotalBlocks() + "\n";
        str += "rblocks = " + getDeviceBlocks() + "\n";
        str += "rextents = " + getDeviceExtents() + "\n";
        str += "uuid = " + getDeviceIdentifier() + "(PENDING to FIND PARSING METHOD)\n";
        str += "logstart = " + getJournalBlockNumber() + "\n";
        str += "rootino = " + getRootINodeNumber() + "\n";
        str += "rbmino = " + getBitmapExtentsINodeNumber() + "\n";
        str += "rsumino = " + getBitmapSummaryINodeNumber() + "\n";
        str += "rextsize = " + getExtentSize() + "\n";
        str += "agblocks = " + getAGSize() + "\n";
        str += "agcount = " + getAGCount() + "\n";
        str += "rbmblocks = " + getBlockNumber() + "\n";
        str += "logblocks = " + getJournalBlockNumber() + "\n";
        str += "versionnum = " + Long.toOctalString(getFeatureFlags()) + "\n";
        str += "sectsize = " + getSectorSize() + "\n";
        str += "inodesize = " + getINodeSize() + "\n";
        str += "inopblock = " + getINodePerBlock() + "\n";
//        str += "fname = ”" + getVolumeLabel() + "”\n";
//        str += "blocklog = 12" + "\n";
//        str += "sectlog = 9" + "\n";
//        str += "inodelog = 8" + "\n";
//        str += "inopblog = 4" + "\n";
//        str += "agblklog = 22" + "\n";
//        str += "rextslog = 0" + "\n";
        str += "inprogress = " + getCreationFlag() + "\n";
        str += "imax_pct = " + getINodesPercentage() + "\n";
        str += "icount = " + getNumberOfINodes() + "\n";
        str += "ifree = " + getNumberOfFreeINodes() + "\n";
        str += "fdblocks = " + getNumberOfFreeDataBlocks() + "\n";
        str += "frextents = " + getNumberOfFreeExtents() + "\n";
//        str += "uquotino = = 0" + "\n";
//        str += "gquotino = 0" + "\n";
//        str += "qflags = 0" + "\n";
//        str += "flags = 0" + "\n";
//        str += "shared_vn = 0" + "\n";
//        str += "inoalignmt = 2" + "\n";
//        str += "unit = 0" + "\n";
//        str += "width = 0" + "\n";
//        str += "dirblklog = 2" + "\n";
//        str += "logsectlog = 0" + "\n";
//        str += "logsectsize = 0" + "\n";
//        str += "logsunit = 0" + "\n";
//        str += "features2 = 8" + "\n";

        return str;
    }

    public long getMagicNum() throws IOException {
        return read(0, 4);
    }

    /**
     * Typicaly 4096 bytes (4 KiB), can range from 512 to 65536 bytes block size
     */
    public long getBlockSize() throws IOException {
        return read(4, 4);
    }

    /**
     * Total number of blocks
     */
    public long getTotalBlocks() throws IOException {
        return read(8, 8);
    }

    /**
     * Number of real-time (device) blocks
     */
    public long getDeviceBlocks() throws IOException {
        return read(16, 8);
    }

    /**
     * Number of real-time (device) extents
     */
    public long getDeviceExtents() throws IOException {
        return read(24, 8);
    }

    /**
     * File system (or volume) identifier
     * Contains an UUID
     */
    public String getDeviceIdentifier() throws IOException {
        // TODO: Check if is correct reading
        final long p1 = read(32, 8);
        final long p2 = read(40, 8);
        return Long.toOctalString(p1) + Long.toOctalString(p2);
    }

    /**
     * Contains a file system block number or 0 if the journal is stored on a separate device
     * See section: File system block number
     */
    public long getJournalBlockNumber() throws IOException {
        return read(48, 8);
    }

    /**
     * Root directory inode number
     */
    public long getRootINodeNumber() throws IOException {
        return read(56, 8);
    }

    /**
     * Real-time bitmap extents inode number
     */
    public long getBitmapExtentsINodeNumber() throws IOException {
        return read(64, 8);
    }

    /**
     * Real-time bitmap summary inode number
     */
    public long getBitmapSummaryINodeNumber() throws IOException {
        return read(72, 8);
    }

    /**
     * Real-time extent size
     * Contains number of blocks
     */
    public long getExtentSize() throws IOException {
        return read(80, 4);
    }

    /**
     * Allocation group size
     * Contains number of blocks
     */
    public long getAGSize() throws IOException {
        return read(84, 4);
    }

    /**
     * Number of allocation groups
     */
    public long getAGCount() throws IOException {
        return read(88, 4);
    }

    /**
     * Real-time bitmap size
     * Contains number of blocks
     */
    public long getBlockNumber() throws IOException {
        return read(92, 4);
    }

    /**
     * Real-time bitmap size
     * Contains number of blocks
     */
    public long getBitmapSize() throws IOException {
        return read(92, 4);
    }

    /**
     * Journal size
     * Contains number of blocks
     */
    public long getJournalSize() throws IOException {
        return read(96, 4);
    }

    /**
     * Version and feature flags
     * The 4 LSB contain the version the remaining bits are used to store the feature flags
     */
    public long getFeatureFlags() throws IOException {
        return read(100, 2);
    }

    public List<FeatureFlags> getFeatureFlagsEnumList() throws IOException {
        return FeatureFlags.getFlags(getFeatureFlags());
    }


    /**
     * Sector size (in bytes)
     */
    public long getSectorSize() throws IOException {
        return read(102, 2);
    }

    /**
     * Inode size (in bytes)
     * Supported range 256 - 2048
     */
    public long getINodeSize() throws IOException {
        return read(104, 2);
    }

    /**
     * Number of inodes per block
     */
    public long getINodePerBlock() throws IOException {
        return read(106, 2);
    }

    /**
     * Volume label (or name)
     */
    public long getVolumeLabel() throws IOException {
        // TODO: Verify hoy to read
        return read(108, 12);
    }

    // TODO: Verify if log2 values are required

    /**
     * Creation flag
     * Value to indicate file system is being created
     */
    public long getCreationFlag() throws IOException {
        return read(126, 1);
    }

    /**
     * Inodes percentage
     * Contains the percentage of the maximum space of the volume to use for inodes
     */
    public long getINodesPercentage() throws IOException {
        return read(127, 1);
    }

    //------------- Only used in first SuperBlock -------------


    /**
     * Number of inodes
     */
    public long getNumberOfINodes() throws IOException {
        return read(128, 8);
    }

    /**
     * Number of free inodes
     */
    public long getNumberOfFreeINodes() throws IOException {
        return read(136, 8);
    }

    /**
     * Number of free data blocks
     */
    public long getNumberOfFreeDataBlocks() throws IOException {
        return read(144, 8);
    }

    /**
     * Number of free real-time extents
     */
    public long getNumberOfFreeExtents() throws IOException {
        return read(152, 8);
    }

    // ----------- Only used if the XFS_SB_VERSION_QUOTABIT feature flag is set --------------

    /**
     * User quota inode number
     */
    public long getUserQuotaINodeNumber() throws IOException {
        return read(160, 8);
    }

    /**
     * Group (or project) quota inode number
     */
    public long getGroupQuotaINodeNumber() throws IOException {
        return read(168, 8);
    }

    /**
     * Quota flags
     * See section: Quota flags
     */
    public long getQuotaFlag() throws IOException {
        return read(176, 2);
    }

    /**
     * Miscellaneous flags
     * See sction: Miscellaneous flags
     */
    public long getMiscellaneousFlags() throws IOException {
        return read(178, 1);
    }


    // ------- Only used if the XFS_SB_VERSION_ALIGNBIT feature flag is set --------


    /**
     * Inode chunk alignment size
     * Contains number of blocks
     */
    public long getINodeChunkAlignmentSize() throws IOException {
        return read(180, 4);
    }

    /**
     * Stripe or RAID unit size
     * Contains number of blocks
     */
    public long getRaidUnitSize() throws IOException {
        return read(184, 4);
    }


    /**
     * Stripe of RAID width
     * Contains number of blocks
     */
    public long getRaidWidth() throws IOException {
        return read(188, 4);
    }

    /**
     * Directory block size in log2
     */
    public long getDirectoryBlockSizeLog2() throws IOException {
        return read(192, 1);
    }

    /**
     * Journal device sector size in log2
     */
    public long getJournalDeviceSizeLog2() throws IOException {
        return read(193, 1);
    }

    /**
     * Journal device sector size (in bytes)
     */
    public long getJournalDeviceSizeBytes() throws IOException {
        return read(194, 2);
    }

    // ------- Only used if the XFS_SB_VERSION_LOGV2BIT feature flag is set -------


    /**
     * Journal device stripe or RAID unit size
     */
    public long getJournalRaidUnitSize() throws IOException {
        return read(196, 4);
    }


    /**
     * Secondary feature flags
     * See section: Secondary feature flags
     */
    public long getSecondaryFeatureFlags() throws IOException {
        return read(200, 4);
    }

    /**
     * Copy of secondary feature flags
     * Introduced to work-around 64-bit alignment errors
     * See section: Secondary feature flags
     */
    public long getSecondaryFeatureFlagsCopy() throws IOException {
        return read(204, 4);
    }

    // ----- If file system version >= 5 ------

    /**
     * (Read-write) compatible feature flags
     * See section: Compatible feature flags
     */
    public long getReadWriteFeatureFlags() throws IOException {
        return read(208, 4);
    }

    /**
     * Read-only compatible feature flags
     * See section: Read-only compatible feature flags
     */
    public long getReadOnlyFeatureFlags() throws IOException {
        return read(212, 4);
    }

    /**
     * (Read-write) incompatible feature flags
     * See section: Incompatible feature flags
     */
    public long getReadWriteIncompatibleFeatureFlags() throws IOException {
        return read(216, 4);
    }

    /**
     * Journal (read-write) incompatible feature flags
     * See section: Journal incompatible feature flags
     */
    public long getJournalReadWriteIncompatibleFeatureFlags() throws IOException {
        return read(220, 4);
    }

    /**
     * Checksum of the superblock
     */
    public long getSuperBlockChecksum() throws IOException {
        return read(224, 4);
    }

    /**
     * Checksum of the superblock
     */
    public long getProjectQuotaINodeNumber() throws IOException {
        return read(232, 4);
    }

    /**
     * Journal log sequence number (LSN) of the last superblock update
     */
    public long getJournalLogSequenceNumber() throws IOException {
        return read(240, 8);
    }

    // ------- Only used if the XFS_SB_FEAT_INCOMPAT_META_UUID incompatible feature flag is set -------

    /**
     * Metadata identifier
     * Contains an UUID
     */
    public long getMetadataIdentifier() throws IOException {
        return read(248, 16);
    }

    /**
     * Real-time Reverse Mapping B+tree inode number
     */
    public long getReverseMappingBTreeINodeNumber() throws IOException {
        return read(264, 8);
    }

    public long getVersion() throws IOException {
        return getFeatureFlags() & 0x0008;
    }

    public long getLastBytePositionForOffset() throws IOException {
        return getOffset() + (getVersion() >= 5 ? 272 : 208);
    }
}
