package org.jnode.fs.btrfs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jnode.util.LittleEndian;

/**
 * A mounted (read-only) btrfs volume: superblock → chunk map → root tree → FS tree(s). Presents the
 * top-level subvolume (FS_TREE, objectid 5) as the root and descends into nested subvolumes, so a
 * disk-usage walk sees every subvolume's files. Each subvolume's FS tree is scanned once (lazily)
 * into small inode/children maps; navigation and file-content reads use those maps.
 *
 * @author David Baird
 */
public class BtrfsVolume {

    /** One directory entry as stored in the FS tree. */
    static final class DirEntry {
        final String name;
        final long childObjectId;
        final int locationType; // INODE_ITEM (a file/dir) or ROOT_ITEM (a subvolume)

        DirEntry(String name, long childObjectId, int locationType) {
            this.name = name;
            this.childObjectId = childObjectId;
            this.locationType = locationType;
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
    /** subvolume root objectid -> its FS tree bytenr (from the root tree's ROOT_ITEMs). */
    private final Map<Long, Long> subvolBytenr = new HashMap<Long, Long>();
    /** subvolumes that are snapshots (ROOT_ITEM.parent_uuid != 0) — skipped by default. */
    private final Set<Long> snapshotSubvols = new HashSet<Long>();
    /** lazily scanned subvolumes, keyed by root objectid. */
    private final Map<Long, Subvol> scanned = new HashMap<Long, Subvol>();
    /**
     * Whether to descend snapshot subvolumes. Off by default: a snapper/openSUSE root has dozens of
     * read-only snapshots (each ≈ a full root copy sharing storage via CoW), so descending them all
     * explodes the tree and massively overstates usage. Real subvolumes (root, home) are always
     * descended — only snapshots are gated. Override with {@code -Dorg.jnode.fs.btrfs.descendSnapshots=true}
     * or {@link #setDescendSnapshots(boolean)}.
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
        if (!subvolBytenr.containsKey(BtrfsConstants.OBJECTID_FS_TREE)) {
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
        return new BtrfsNode(this, BtrfsConstants.OBJECTID_FS_TREE, top.rootDirId, "",
                infoOrDir(top, top.rootDirId));
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
                Long bytenr = subvolBytenr.get(e.childObjectId);
                if (bytenr == null) {
                    continue; // subvolume not found (e.g. deleted) — skip
                }
                if (!descendSnapshots && snapshotSubvols.contains(e.childObjectId)) {
                    continue; // a snapshot (parent_uuid set) — skipped unless descent is enabled
                }
                Subvol child = subvol(e.childObjectId);
                result.add(new BtrfsNode(this, e.childObjectId, child.rootDirId, e.name,
                        infoOrDir(child, child.rootDirId)));
            } else {
                InodeInfo info = sv.inodes.get(e.childObjectId);
                if (info == null) {
                    continue;
                }
                result.add(new BtrfsNode(this, dir.getSubvolId(), e.childObjectId, e.name, info));
            }
        }
        return result;
    }

    // ---- scanning ----

    /** Collects every subvolume's FS-tree bytenr from the root tree's ROOT_ITEMs. */
    private void readRootTree() throws IOException {
        tree.scanLeaves(sb.getRootTreeLogical(), (key, data) -> {
            if (key.getType() == BtrfsConstants.TYPE_ROOT_ITEM) {
                long id = key.getObjectId();
                // subvolumes are FS_TREE (5) and objectids >= FIRST_FREE (256); skip the special
                // internal trees (extent/dev/csum/...) whose ids are < 256 and != 5
                if (id == BtrfsConstants.OBJECTID_FS_TREE || id >= BtrfsConstants.OBJECTID_FIRST_FREE) {
                    long bytenr = LittleEndian.getInt64(data, BtrfsConstants.ROOT_ITEM_BYTENR);
                    // keep the highest generation if duplicated (later ROOT_ITEM wins by scan order)
                    subvolBytenr.put(id, bytenr);
                    if (isSnapshotRootItem(data)) {
                        snapshotSubvols.add(id);
                    }
                }
            }
        });
    }

    /** Enables (or disables) descending into snapshot subvolumes. Default: snapshots are skipped. */
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
     * A ROOT_ITEM is a snapshot iff its {@code parent_uuid} is non-zero — i.e. it was created as a
     * snapshot/clone of another subvolume. Regular subvolumes ({@code btrfs subvolume create}, e.g.
     * root/home) leave it all-zero, so they are never treated as snapshots.
     */
    static boolean isSnapshotRootItem(byte[] data) {
        int off = BtrfsConstants.ROOT_ITEM_PARENT_UUID;
        if (data.length < off + BtrfsConstants.UUID_SIZE) {
            return false; // pre-uuid root_item (very old btrfs): nothing to distinguish
        }
        for (int i = 0; i < BtrfsConstants.UUID_SIZE; i++) {
            if (data[off + i] != 0) {
                return true;
            }
        }
        return false;
    }

    /** Scans (once) a subvolume's FS tree into inode + children maps. */
    private Subvol subvol(long rootObjectId) throws IOException {
        Subvol existing = scanned.get(rootObjectId);
        if (existing != null) {
            return existing;
        }
        Long bytenr = subvolBytenr.get(rootObjectId);
        if (bytenr == null) {
            throw new IOException("Unknown btrfs subvolume " + rootObjectId);
        }
        long rootDirId = rootDirIdOf(rootObjectId);
        Subvol sv = new Subvol(rootDirId);
        scanned.put(rootObjectId, sv); // insert before scanning to guard against self-referential loops
        tree.scanLeaves(bytenr, (key, data) -> {
            int type = key.getType();
            if (type == BtrfsConstants.TYPE_INODE_ITEM) {
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
                DirEntry e = parseDirEntry(data);
                if (e != null) {
                    sv.children.computeIfAbsent(key.getObjectId(), k -> new ArrayList<DirEntry>()).add(e);
                }
            }
        });
        return sv;
    }

    /** The root directory inode id of a subvolume (from its ROOT_ITEM.dirid; defaults to 256). */
    private long rootDirIdOf(long rootObjectId) throws IOException {
        long[] dirId = {BtrfsConstants.OBJECTID_FIRST_FREE};
        tree.scanLeaves(sb.getRootTreeLogical(), (key, data) -> {
            if (key.getType() == BtrfsConstants.TYPE_ROOT_ITEM && key.getObjectId() == rootObjectId) {
                long d = LittleEndian.getInt64(data, BtrfsConstants.ROOT_ITEM_DIRID);
                if (d != 0) {
                    dirId[0] = d;
                }
            }
        });
        return dirId[0];
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

    private static DirEntry parseDirEntry(byte[] data) {
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
        return new DirEntry(name, childId, locationType);
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
        Long b = subvolBytenr.get(subvolId);
        if (b == null) {
            throw new IOException("Unknown subvolume " + subvolId);
        }
        return b;
    }

    int sectorSize() {
        return sb.getSectorSize();
    }
}
