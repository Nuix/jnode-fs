package org.jnode.fs.btrfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jnode.util.LittleEndian;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A mounted (read-only) btrfs volume: superblock → chunk map → root tree → FS tree(s). Presents the
 * top-level subvolume (FS_TREE, objectid 5) as the root and descends into nested subvolumes, so a
 * disk-usage walk sees every subvolume's files. Each subvolume's FS tree is scanned once (lazily)
 * into small inode/children maps; navigation and file-content reads use those maps.
 *
 * @author David Baird
 */
public class BtrfsVolume {

    private static final Logger log = LoggerFactory.getLogger(BtrfsVolume.class);

    /** One directory entry as stored in the FS tree. */
    static final class DirEntry {
        final String name;
        final long childObjectId;
        final int locationType; // INODE_ITEM (a file/dir) or ROOT_ITEM (a subvolume)
        final long dirIndex;    // the DIR_INDEX key offset: unique per entry within its directory

        DirEntry(String name, long childObjectId, int locationType, long dirIndex) {
            this.name = name;
            this.childObjectId = childObjectId;
            this.locationType = locationType;
            this.dirIndex = dirIndex;
        }
    }

    /** What the reader keeps of one {@code INODE_ITEM}: size, kind, and the four timestamps (ms). */
    static final class InodeInfo {
        final long size;
        final boolean directory;
        final boolean symlink;
        final long accessedMs;  // atime
        final long changedMs;   // ctime (inode change)
        final long modifiedMs;  // mtime (content change)
        final long createdMs;   // otime (birth; 0 on filesystems/tools that never set it)

        InodeInfo(long size, boolean directory, boolean symlink,
                long accessedMs, long changedMs, long modifiedMs, long createdMs) {
            this.size = size;
            this.directory = directory;
            this.symlink = symlink;
            this.accessedMs = accessedMs;
            this.changedMs = changedMs;
            this.modifiedMs = modifiedMs;
            this.createdMs = createdMs;
        }

        /** A stand-in for a missing inode (e.g. a subvolume root dir that failed to resolve). */
        static InodeInfo missingDirectory() {
            return new InodeInfo(0, true, false, 0, 0, 0, 0);
        }
    }

    /** What the reader keeps of one subvolume's {@code ROOT_ITEM}. */
    private static final class RootInfo {
        final long bytenr;              // the subvolume's FS tree root block
        final long dirId;               // its root directory inode
        final boolean readOnlySnapshot; // skipped unless snapshot descent is enabled

        RootInfo(long bytenr, long dirId, boolean readOnlySnapshot) {
            this.bytenr = bytenr;
            this.dirId = dirId;
            this.readOnlySnapshot = readOnlySnapshot;
        }
    }

    /** The scanned state of one subvolume's FS tree. */
    private static final class Subvol {
        final long rootDirId;
        final Map<Long, InodeInfo> inodes = new HashMap<Long, InodeInfo>();
        final Map<Long, List<DirEntry>> children = new HashMap<Long, List<DirEntry>>();

        Subvol(long rootDirId) {
            this.rootDirId = rootDirId;
        }
    }

    private final BtrfsBlockReader reader;
    private final BtrfsSuperblock sb;
    private final BtrfsChunkMap chunkMap;
    private final BtrfsTree tree;
    /** subvolume root objectid -> its ROOT_ITEM essentials (from one pass over the root tree). */
    private final Map<Long, RootInfo> roots = new HashMap<Long, RootInfo>();
    /** lazily scanned subvolumes, keyed by root objectid. */
    private final Map<Long, Subvol> scanned = new HashMap<Long, Subvol>();
    /**
     * Whether to descend read-only snapshot subvolumes. Off by default: a snapper/openSUSE root has
     * dozens of read-only snapshots (each ≈ a full root copy sharing storage via CoW), so descending
     * them all explodes the tree and massively overstates usage. Real subvolumes (root, home) are
     * always descended, and so is a <em>writable</em> snapshot — after {@code snapper rollback} or a
     * Timeshift restore the live root <em>is</em> a writable snapshot, and skipping it would hide the
     * whole filesystem. Override with {@code -Dorg.jnode.fs.btrfs.descendSnapshots=true} or
     * {@link #setDescendSnapshots(boolean)}.
     */
    private boolean descendSnapshots = Boolean.getBoolean("org.jnode.fs.btrfs.descendSnapshots");

    public BtrfsVolume(BtrfsBlockReader reader) throws IOException {
        this.reader = reader;
        this.sb = new BtrfsSuperblock(reader);
        if (sb.getNumDevices() != 1) {
            throw new IOException("Multi-device btrfs is not supported (" + sb.getNumDevices() + " devices)");
        }
        this.chunkMap = new BtrfsChunkMap(sb, reader);
        this.tree = new BtrfsTree(reader, chunkMap, sb.getNodeSize());
        if (Boolean.getBoolean("org.jnode.fs.btrfs.verifyChecksums")) {
            setVerifyChecksums(true);
        }
        readRootTree();
        if (!roots.containsKey(BtrfsConstants.OBJECTID_FS_TREE)) {
            throw new IOException("btrfs has no FS tree");
        }
    }

    public BtrfsSuperblock getSuperblock() {
        return sb;
    }

    /**
     * The volume root: the top-level subvolume's root directory.
     *
     * @return a node for the root directory of the FS tree (objectid 5).
     * @throws IOException if the FS tree cannot be read.
     */
    public BtrfsNode getRoot() throws IOException {
        Subvol top = subvol(BtrfsConstants.OBJECTID_FS_TREE);
        return new BtrfsNode(this, BtrfsConstants.OBJECTID_FS_TREE, top.rootDirId, BtrfsNode.NO_DIR_INDEX,
                "", infoOrDir(top, top.rootDirId));
    }

    private static InodeInfo infoOrDir(Subvol sv, long objectId) {
        InodeInfo info = sv.inodes.get(objectId);
        return info != null ? info : InodeInfo.missingDirectory();
    }

    /** Children of a directory node (crossing into subvolumes where a dir entry points at one). */
    List<BtrfsNode> listChildren(BtrfsNode dir) throws IOException {
        List<BtrfsNode> result = new ArrayList<BtrfsNode>();
        Subvol sv = subvol(dir.getSubvolId());
        List<DirEntry> entries = sv.children.get(dir.getObjectId());
        if (entries == null) {
            return result;
        }
        for (DirEntry e : entries) {
            if (e.locationType == BtrfsConstants.TYPE_ROOT_ITEM) {
                // a nested subvolume: descend into its own FS tree
                RootInfo root = roots.get(e.childObjectId);
                if (root == null) {
                    continue; // subvolume not found (e.g. deleted) — skip
                }
                if (!descendSnapshots && root.readOnlySnapshot) {
                    log.info("Skipping read-only btrfs snapshot '{}' (subvolume {}); enable snapshot descent to include it",
                            e.name, e.childObjectId);
                    continue;
                }
                Subvol child = subvol(e.childObjectId);
                result.add(new BtrfsNode(this, e.childObjectId, child.rootDirId, e.dirIndex, e.name,
                        infoOrDir(child, child.rootDirId)));
            } else {
                InodeInfo info = sv.inodes.get(e.childObjectId);
                if (info == null) {
                    continue;
                }
                result.add(new BtrfsNode(this, dir.getSubvolId(), e.childObjectId, e.dirIndex, e.name, info));
            }
        }
        return result;
    }

    // ---- scanning ----

    /** Collects every subvolume's ROOT_ITEM essentials (tree bytenr, root dirid, snapshot flag). */
    private void readRootTree() throws IOException {
        tree.scanLeaves(sb.getRootTreeLogical(), (key, data) -> {
            if (key.getType() == BtrfsConstants.TYPE_ROOT_ITEM) {
                long id = key.getObjectId();
                // subvolumes are FS_TREE (5) and objectids >= FIRST_FREE (256); skip the special
                // internal trees (extent/dev/csum/...) whose ids are < 256 and != 5
                if (id == BtrfsConstants.OBJECTID_FS_TREE || id >= BtrfsConstants.OBJECTID_FIRST_FREE) {
                    if (data.length < BtrfsConstants.ROOT_ITEM_BYTENR + 8) {
                        return; // too short to even name its tree
                    }
                    long bytenr = LittleEndian.getInt64(data, BtrfsConstants.ROOT_ITEM_BYTENR);
                    long dirId = LittleEndian.getInt64(data, BtrfsConstants.ROOT_ITEM_DIRID);
                    if (dirId == 0) {
                        dirId = BtrfsConstants.OBJECTID_FIRST_FREE;
                    }
                    // keep the highest generation if duplicated (later ROOT_ITEM wins by scan order)
                    roots.put(id, new RootInfo(bytenr, dirId, isReadOnlySnapshot(data)));
                }
            }
        });
    }

    /** Enables (or disables) descending into read-only snapshot subvolumes. Default: skipped. */
    public void setDescendSnapshots(boolean descend) {
        this.descendSnapshots = descend;
    }

    /**
     * Enables per-block crc32c verification (off by default — read-only browsing doesn't need it).
     * Once on, any tree block whose checksum doesn't match raises an {@code IOException} when read.
     */
    public void setVerifyChecksums(boolean verify) {
        tree.setChecksumVerification(verify, sb.getCsumType());
    }

    /**
     * A ROOT_ITEM is a <em>read-only snapshot</em> iff its {@code parent_uuid} is non-zero (it was
     * created as a snapshot of another subvolume) <em>and</em> {@code BTRFS_ROOT_SUBVOL_RDONLY} is
     * set in its flags. snapper and Timeshift snapshots are both read-only. A snapshot that was made
     * writable — which is what a rolled-back or restored root is — carries the parent_uuid but not
     * the flag, and is treated as a regular subvolume. Regular subvolumes ({@code btrfs subvolume
     * create}, e.g. root/home) have an all-zero parent_uuid, so they are never treated as snapshots.
     */
    static boolean isReadOnlySnapshot(byte[] data) {
        int off = BtrfsConstants.ROOT_ITEM_PARENT_UUID;
        if (data.length < off + BtrfsConstants.UUID_SIZE) {
            return false; // pre-uuid root_item (very old btrfs): nothing to distinguish
        }
        boolean hasParent = false;
        for (int i = 0; i < BtrfsConstants.UUID_SIZE; i++) {
            if (data[off + i] != 0) {
                hasParent = true;
                break;
            }
        }
        long flags = LittleEndian.getInt64(data, BtrfsConstants.ROOT_ITEM_FLAGS);
        return hasParent && (flags & BtrfsConstants.ROOT_SUBVOL_RDONLY) != 0;
    }

    /** Scans (once) a subvolume's FS tree into inode + children maps. */
    private Subvol subvol(long rootObjectId) throws IOException {
        Subvol existing = scanned.get(rootObjectId);
        if (existing != null) {
            return existing;
        }
        RootInfo root = roots.get(rootObjectId);
        if (root == null) {
            throw new IOException("Unknown btrfs subvolume " + rootObjectId);
        }
        Subvol sv = new Subvol(root.dirId);
        scanned.put(rootObjectId, sv); // insert before scanning to guard against self-referential loops
        tree.scanLeaves(root.bytenr, (key, data) -> {
            int type = key.getType();
            if (type == BtrfsConstants.TYPE_INODE_ITEM) {
                if (data.length < BtrfsConstants.INODE_MODE_OFF + 4) {
                    return; // truncated inode item
                }
                long size = LittleEndian.getInt64(data, BtrfsConstants.INODE_SIZE_OFF);
                int mode = (int) LittleEndian.getUInt32(data, BtrfsConstants.INODE_MODE_OFF);
                int fmt = mode & BtrfsConstants.S_IFMT;
                sv.inodes.put(key.getObjectId(), new InodeInfo(size,
                        fmt == BtrfsConstants.S_IFDIR, fmt == BtrfsConstants.S_IFLNK,
                        timespecMs(data, BtrfsConstants.INODE_ATIME),
                        timespecMs(data, BtrfsConstants.INODE_CTIME),
                        timespecMs(data, BtrfsConstants.INODE_MTIME),
                        timespecMs(data, BtrfsConstants.INODE_OTIME)));
            } else if (type == BtrfsConstants.TYPE_DIR_INDEX) {
                DirEntry e = parseDirEntry(data, key.getOffset());
                if (e != null) {
                    sv.children.computeIfAbsent(key.getObjectId(), k -> new ArrayList<DirEntry>()).add(e);
                }
            }
        });
        return sv;
    }

    /**
     * The inode's extended attributes, fetched with a keyed search of its {@code XATTR_ITEM}s
     * (contiguous in key order, like its extents).
     *
     * @param subvolId the subvolume the inode lives in.
     * @param objectId the inode's object id.
     * @return the attributes, in key order; empty if the inode has none.
     * @throws IOException if the FS tree cannot be read.
     */
    List<org.jnode.fs.FSAttribute> listXattrs(long subvolId, long objectId) throws IOException {
        List<org.jnode.fs.FSAttribute> result = new ArrayList<org.jnode.fs.FSAttribute>();
        BtrfsTree.Cursor cur = tree.search(subvolBytenr(subvolId),
                new BtrfsDiskKey(objectId, BtrfsConstants.TYPE_XATTR_ITEM, 0));
        while (cur.valid()) {
            BtrfsDiskKey key = cur.key();
            if (key.getObjectId() != objectId || key.getType() != BtrfsConstants.TYPE_XATTR_ITEM) {
                break; // walked past this inode's xattr range
            }
            BtrfsAttribute.parseItem(cur.data(), result);
            cur.next();
        }
        return result;
    }

    /** A btrfs_timespec ({@code __le64 sec; __le32 nsec;}) at {@code off}, as epoch milliseconds. */
    private static long timespecMs(byte[] data, int off) {
        if (off + 12 > data.length) {
            return 0; // truncated (pre-timespec) inode item
        }
        long sec = LittleEndian.getInt64(data, off);
        long nsec = LittleEndian.getUInt32(data, off + 8);
        return sec * 1_000L + nsec / 1_000_000L;
    }

    private static DirEntry parseDirEntry(byte[] data, long dirIndex) {
        if (data.length < BtrfsConstants.DIR_NAME) {
            return null;
        }
        long childId = LittleEndian.getInt64(data, BtrfsConstants.DIR_LOCATION_OBJECTID);
        int locationType = data[BtrfsConstants.DIR_LOCATION_TYPE] & 0xFF;
        int nameLen = LittleEndian.getUInt16(data, BtrfsConstants.DIR_NAME_LEN);
        if (BtrfsConstants.DIR_NAME + nameLen > data.length) {
            return null;
        }
        String name = new String(data, BtrfsConstants.DIR_NAME, nameLen,
                java.nio.charset.StandardCharsets.UTF_8);
        return new DirEntry(name, childId, locationType, dirIndex);
    }

    // ---- file content ----

    BtrfsBlockReader reader() {
        return reader;
    }

    BtrfsChunkMap chunkMap() {
        return chunkMap;
    }

    BtrfsTree tree() {
        return tree;
    }

    long subvolBytenr(long subvolId) throws IOException {
        RootInfo root = roots.get(subvolId);
        if (root == null) {
            throw new IOException("Unknown subvolume " + subvolId);
        }
        return root.bytenr;
    }

    int sectorSize() {
        return sb.getSectorSize();
    }
}
