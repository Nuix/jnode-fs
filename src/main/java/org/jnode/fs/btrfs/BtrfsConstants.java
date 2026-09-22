package org.jnode.fs.btrfs;

/**
 * On-disk constants for the btrfs format: the offsets, magic, key types and object ids the
 * read-only reader needs. See the Linux kernel {@code fs/btrfs/ctree.h} for the authoritative
 * definitions.
 *
 * @author David Baird
 */
public final class BtrfsConstants {

    private BtrfsConstants() {
    }

    /** The primary superblock lives at this physical offset. */
    public static final long SUPERBLOCK_OFFSET = 0x10000L; // 65536

    /** {@code _BHRfS_M} at offset 0x40 within the superblock. */
    public static final byte[] MAGIC = {'_', 'B', 'H', 'R', 'f', 'S', '_', 'M'};

    // ---- superblock field offsets (within the superblock) ----
    public static final int SB_FSID = 0x20;      // uuid, 16 bytes
    public static final int SB_MAGIC = 0x40;
    public static final int SB_GENERATION = 0x48;
    public static final int SB_ROOT = 0x50;          // logical addr of the root tree
    public static final int SB_CHUNK_ROOT = 0x58;    // logical addr of the chunk tree
    public static final int SB_TOTAL_BYTES = 0x70;
    public static final int SB_BYTES_USED = 0x78;
    public static final int SB_ROOT_DIR_OBJECTID = 0x80;
    public static final int SB_NUM_DEVICES = 0x88;
    public static final int SB_SECTORSIZE = 0x90;
    public static final int SB_NODESIZE = 0x94;
    public static final int SB_SYS_CHUNK_ARRAY_SIZE = 0xa0;
    public static final int SB_INCOMPAT_FLAGS = 0xbc;
    public static final int SB_CSUM_TYPE = 0xc4;
    public static final int SB_ROOT_LEVEL = 0xc6;
    public static final int SB_CHUNK_ROOT_LEVEL = 0xc7;
    /** Volume label: NUL-terminated UTF-8, follows the embedded dev_item (0xc9 + 0x62). */
    public static final int SB_LABEL = 0x12b;
    public static final int SB_LABEL_SIZE = 256;
    public static final int SB_SYS_CHUNK_ARRAY = 0x32b; // 811
    public static final int SYS_CHUNK_ARRAY_MAX = 2048;

    // ---- tree node header (btrfs_header), total 101 bytes ----
    public static final int HEADER_SIZE = 0x65; // 101
    public static final int HDR_BYTENR = 0x30;
    public static final int HDR_GENERATION = 0x50;
    public static final int HDR_OWNER = 0x58;
    public static final int HDR_NRITEMS = 0x60;
    public static final int HDR_LEVEL = 0x64;

    /** Leaf item: key(17) + offset(4) + size(4). */
    public static final int ITEM_SIZE = 25;
    /** Internal node key pointer: key(17) + blockptr(8) + generation(8). */
    public static final int KEY_PTR_SIZE = 33;
    /** A disk key: objectid(8) + type(1) + offset(8). */
    public static final int KEY_SIZE = 17;

    // ---- key types ----
    public static final int TYPE_INODE_ITEM = 0x01;
    public static final int TYPE_INODE_REF = 0x0c;
    public static final int TYPE_XATTR_ITEM = 0x18;
    public static final int TYPE_DIR_ITEM = 0x54;   // 84
    public static final int TYPE_DIR_INDEX = 0x60;  // 96
    public static final int TYPE_EXTENT_DATA = 0x6c; // 108
    public static final int TYPE_ROOT_ITEM = 0x84;  // 132
    public static final int TYPE_CHUNK_ITEM = 0xe4; // 228

    // ---- well-known object ids ----
    public static final long OBJECTID_ROOT_TREE = 1;
    public static final long OBJECTID_FS_TREE = 5;
    public static final long OBJECTID_FIRST_FREE = 256; // first inode / subvol root dir
    public static final long OBJECTID_FIRST_CHUNK_TREE = 256;

    // ---- directory entry file types (btrfs_dir_item.type) ----
    public static final int FT_REG_FILE = 1;
    public static final int FT_DIR = 2;

    // ---- INODE_ITEM (btrfs_inode_item, 160 bytes) field offsets ----
    // struct btrfs_inode_item {
    //     __le64 generation;        0x00      __le64 rdev;              0x38
    //     __le64 transid;           0x08      __le64 flags;             0x40
    //     __le64 size;              0x10      __le64 sequence;          0x48
    //     __le64 nbytes;            0x18      __le64 reserved[4];       0x50
    //     __le64 block_group;       0x20      struct btrfs_timespec atime; 0x70
    //     __le32 nlink;             0x28      struct btrfs_timespec ctime; 0x7c
    //     __le32 uid;               0x2c      struct btrfs_timespec mtime; 0x88
    //     __le32 gid;               0x30      struct btrfs_timespec otime; 0x94
    //     __le32 mode;              0x34
    // };   btrfs_timespec = { __le64 sec; __le32 nsec; }  (12 bytes)
    public static final int INODE_SIZE_OFF = 0x10;
    public static final int INODE_NLINK_OFF = 0x28;
    public static final int INODE_MODE_OFF = 0x34;
    public static final int INODE_ATIME = 0x70;
    public static final int INODE_CTIME = 0x7c;
    public static final int INODE_MTIME = 0x88;
    public static final int INODE_OTIME = 0x94;
    /** S_IFMT / S_IFDIR from POSIX mode. */
    public static final int S_IFMT = 0xf000;
    public static final int S_IFDIR = 0x4000;
    public static final int S_IFREG = 0x8000;
    public static final int S_IFLNK = 0xa000;

    // ---- DIR_ITEM / DIR_INDEX (btrfs_dir_item) field offsets ----
    public static final int DIR_LOCATION_OBJECTID = 0x00;
    public static final int DIR_LOCATION_TYPE = 0x08;
    public static final int DIR_DATA_LEN = 0x19;
    public static final int DIR_NAME_LEN = 0x1b;
    public static final int DIR_FTYPE = 0x1d;
    public static final int DIR_NAME = 0x1e;

    // ---- ROOT_ITEM (btrfs_root_item) field offsets ----
    public static final int ROOT_ITEM_DIRID = 0xa8;
    public static final int ROOT_ITEM_BYTENR = 0xb0;
    public static final int ROOT_ITEM_LEVEL = 0xee;
    /** parent_uuid: non-zero iff the subvolume was created as a snapshot of another (16 bytes). */
    public static final int ROOT_ITEM_PARENT_UUID = 0x107;
    public static final int UUID_SIZE = 16;

    // ---- CHUNK_ITEM (btrfs_chunk) field offsets ----
    public static final int CHUNK_LENGTH = 0x00;
    public static final int CHUNK_TYPE = 0x18;
    public static final int CHUNK_NUM_STRIPES = 0x2c;
    public static final int CHUNK_STRIPES = 0x30; // first btrfs_stripe
    public static final int STRIPE_SIZE = 32;     // devid(8) + offset(8) + dev_uuid(16)
    public static final int STRIPE_OFFSET = 0x08; // physical offset within the stripe

    // ---- EXTENT_DATA (btrfs_file_extent_item) ----
    public static final int EXTENT_RAM_BYTES = 0x08;
    public static final int EXTENT_COMPRESSION = 0x10;
    public static final int EXTENT_TYPE = 0x14;      // 0=inline, 1=regular, 2=prealloc
    public static final int EXTENT_INLINE_DATA = 0x15;
    public static final int EXTENT_DISK_BYTENR = 0x15;
    public static final int EXTENT_DISK_NUM_BYTES = 0x1d;
    public static final int EXTENT_DATA_OFFSET = 0x25;
    public static final int EXTENT_NUM_BYTES = 0x2d;
    public static final int EXTENT_TYPE_INLINE = 0;
    public static final int EXTENT_TYPE_REGULAR = 1;
    public static final int EXTENT_TYPE_PREALLOC = 2;
    public static final int COMPRESS_NONE = 0;
    public static final int COMPRESS_ZLIB = 1;
    public static final int COMPRESS_LZO = 2;
    public static final int COMPRESS_ZSTD = 3;

    // ---- chunk block-group profile bits (btrfs_chunk.type) ----
    public static final long BLOCK_GROUP_RAID0 = 1L << 3;
    public static final long BLOCK_GROUP_RAID1 = 1L << 4;
    public static final long BLOCK_GROUP_DUP = 1L << 5;
    public static final long BLOCK_GROUP_RAID10 = 1L << 6;
    public static final long BLOCK_GROUP_RAID5 = 1L << 7;
    public static final long BLOCK_GROUP_RAID6 = 1L << 8;
    /** Profiles that stripe a logical range across stripes — can't be mapped linearly (need &gt;1
     *  device anyway). DUP/RAID1 mirror the full range, so stripe 0 is a complete linear copy. */
    public static final long BLOCK_GROUP_STRIPED_MASK =
            BLOCK_GROUP_RAID0 | BLOCK_GROUP_RAID10 | BLOCK_GROUP_RAID5 | BLOCK_GROUP_RAID6;
}
