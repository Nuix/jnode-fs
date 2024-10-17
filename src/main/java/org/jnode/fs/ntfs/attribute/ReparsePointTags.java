package org.jnode.fs.ntfs.attribute;

/**
 * Well known reparse point tags, documented in "[MS-FSCC]: File System Control Codes".
 *
 * @author Luke Quinane
 */
public class ReparsePointTags {
    /**
     * Reserved reparse tag value.
     */
    public static final int O_REPARSE_TAG_RESERVED_ZERO = 0x00000000;

    /**
     * Reserved reparse tag value.
     */
    public static final int IO_REPARSE_TAG_RESERVED_ONE = 0x00000001;

    /**
     * Reserved reparse tag value.
     */
    public static final int IO_REPARSE_TAG_RESERVED_TWO = 0x00000002;

    /**
     * Used for mount point support.
     */
    public static final int IO_REPARSE_TAG_MOUNT_POINT = 0xA0000003;

    /**
     * Obsolete. Used by legacy Hierarchical Storage Manager Product.
     */
    public static final int IO_REPARSE_TAG_HSM = 0xC0000004;

    /**
     * Home server drive extender.
     */
    public static final int IO_REPARSE_TAG_DRIVE_EXTENDER = 0x80000005;

    /**
     * Obsolete. Used by legacy Hierarchical Storage Manager Product.
     */
    public static final int IO_REPARSE_TAG_HSM2 = 0x80000006;

    /**
     * Used by single-instance storage (SIS) filter driver. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_SIS = 0x80000007;

    /**
     * Used by the WIM Mount filter. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_WIM = 0x80000008;

    /**
     * Obsolete. Used by Clustered Shared Volumes (CSV) version 1 in Windows Server 2008 R2 operating system. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_CSV = 0x80000009;

    /**
     * Used by the DFS filter. The DFS is described in the Distributed File System (DFS): Referral Protocol Specification [MS-DFSC]. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_DFS = 0x8000000A;

    /**
     * Used by filter manager test harness.
     */
    public static final int IO_REPARSE_TAG_FILTER_MANAGER = 0x8000000B;

    /**
     * Used for symbolic link support.
     */
    public static final int IO_REPARSE_TAG_SYMLINK = 0xA000000C;

    /**
     * Used by Microsoft Internet Information Services (IIS) caching. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_IIS_CACHE = 0xA0000010;

    /**
     * Used by the DFS filter. The DFS is described in [MS-DFSC]. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_DFSR = 0x80000012;

    /**
     * Used by the Data Deduplication (Dedup) filter. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_DEDUP = 0x80000013;

    /**
     * Not used.
     */
    public static final int IO_REPARSE_TAG_APPXSTRM = 0xC0000014;

    /**
     * Used by the Network File System (NFS) component. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_NFS = 0x80000014;

    /**
     * Obsolete. Used by Windows Shell for legacy placeholder files in Windows 8.1. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_FILE_PLACEHOLDER = 0x80000015;

    /**
     * Used by the Dynamic File filter. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_DFM = 0x80000016;

    /**
     * Used by the Windows Overlay filter, for either WIMBoot or single-file compression. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_WOF = 0x80000017;

    /**
     * Used by the Windows Container Isolation filter. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_WCI = 0x80000018;

    /**
     * Used by the Windows Container Isolation filter. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_WCI_1 = 0x90001018;

    /**
     * Used by NPFS to indicate a named pipe symbolic link from a server silo into the host silo. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_GLOBAL_REPARSE = 0xA0000019;

    /**
     * Used by the Cloud Files filter, for files managed by a sync engine such as Microsoft OneDrive. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_CLOUD = 0x9000001A;

    /**
     * Used by the Cloud Files filter, for files managed by a sync engine such as OneDrive. Server-side interpretation only, not meaningful over the wire.
     */
    public static final int IO_REPARSE_TAG_CLOUD_1 = 0x9000101A;
}
