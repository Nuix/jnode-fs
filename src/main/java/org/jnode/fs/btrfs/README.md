# btrfs (read-only)

A read-only btrfs reader for jnode-fs: enough to enumerate the directory tree, report file
sizes, and read file contents. Built for disk-usage / forensic browsing of btrfs volumes inside
VM disk images (the motivating use case: a disk-usage tool built on jnode-fs could not read
Fedora/openSUSE roots, which default to btrfs — jnode-fs previously had no btrfs support).

## Scope

**Supported**
- Single-device volumes (VM images are single-device). Multi-device / RAID is rejected cleanly.
- Superblock → `sys_chunk_array` bootstrap → chunk tree → logical→physical address map.
- Root tree → FS tree(s); descent into real subvolumes (root, home, …). **Snapshots** (ROOT_ITEM
  with a non-zero `parent_uuid`) are **skipped by default** — a snapper/openSUSE root has dozens,
  each ≈ a full copy sharing storage, which would explode the tree; re-enable with
  `setDescendSnapshots(true)` or `-Dorg.jnode.fs.btrfs.descendSnapshots=true`.
- FS-tree walk: `INODE_ITEM` (size, mode, atime/ctime/mtime/otime), `DIR_INDEX` (directory
  entries) — one scan per subvolume builds its inode/children maps. **Symlinks** are recognised
  (`BtrfsNode.isSymlink()` / `getSymlinkTarget()`) and never followed. Entries expose the standard
  jnode timestamp interfaces (`getLastModified`, `FSEntryCreated`/`LastAccessed`/`LastChanged`) and
  a volume-unique id (`subvolId-objectId` — plain objectids repeat in every subvolume). The volume
  label (`mkfs.btrfs -L`) is reported via `getVolumeName()`.
- Extended attributes: `BtrfsEntry.getAttributes()` (mirroring `XfsEntry`) returns each inode's
  xattrs — SELinux context, `user.*`, POSIX ACLs, capabilities, btrfs per-file properties — via a
  keyed `XATTR_ITEM` lookup. Values are always inline; hash-colliding names packed into one item
  are handled.
- Keyed B-tree lookup (`BtrfsTree.search`): file-content reads descend to an inode's `EXTENT_DATA`
  items in O(tree depth) node reads and iterate the matching range, rather than rescanning the whole
  FS tree per open. (The size/tree build still scans once — it wants every item.) Uncompressed
  extents are then read **by range** (only the requested bytes, never a whole — up to 128 MiB —
  extent), and compressed extents go through a per-handle one-slot cache so sequential paging
  decompresses each ≤128 KiB extent once.
- File content: inline extents, regular extents, and zlib-, zstd- and lzo-compressed extents (zstd
  and lzo via the pure-Java aircompressor decoder). zstd is the Fedora/openSUSE default; lzo is rare
  but fully supported. All are verified end-to-end against real compressed images (see Verification).
  **Prealloc** (fallocate) extents read as zeros, not the stale unwritten disk blocks.
- crc32c checksums: verification is **optional** and off by default (read-only browsing doesn't need
  it); `setVerifyChecksums(true)` / `-Dorg.jnode.fs.btrfs.verifyChecksums=true` checks every metadata
  block as it's read (crc32c only; other csum types are skipped, not rejected).

**Not supported (degrades, does not crash)**
- Writing (read-only), multi-device/RAID, and the extent/free-space trees (not needed to walk the
  FS tree). Hardlinks are reported once per link (no size de-duplication across links).

## Why this is the right layer

btrfs stores everything in copy-on-write B-trees keyed by `(objectid, type, offset)`. For a
read-only tree walk we only need three trees:
1. **chunk tree** — translate btrfs *logical* addresses to *physical* device offsets
   (bootstrapped by the superblock's `sys_chunk_array` so the chunk tree itself is readable);
2. **root tree** — locate the FS tree of each subvolume (`ROOT_ITEM.bytenr`);
3. **FS tree(s)** — a single leaf scan yields every `INODE_ITEM` (sizes) and `DIR_INDEX`
   (names + child object ids), which we assemble into the directory tree in memory.

The GMTA/btrfs-libs project reads btrfs *send-streams* (the output of `btrfs send`), which is a
different, higher-level format — not the on-disk B-trees — so it does not help here beyond its
(MIT) CRC32C, which Guava (already a dependency) provides via `Hashing.crc32c()`.

## Verification

Fixtures are produced unprivileged with `mkfs.btrfs -r <dir>` and cross-checked against
`btrfs inspect-internal dump-tree` / `dump-super`.

zstd is verified two ways. At the decode layer (`BtrfsZstdDecodeTest`), data compressed with
aircompressor is reproduced in btrfs's exact on-disk shape — one frame, zero-padded to the sector
size — and asserted to round-trip, including the sector-padding case the frame-length walk handles
and the "frame declares more than `ram_bytes`" case. End-to-end (`BtrfsZstdFileSystemTest`), a real
image whose files were written through `mount -o compress-force=zstd` — inline, a full 128 KiB
regular extent, and a mid-size one, all confirmed `compression 3` by `dump-tree` — is read back and
the decompressed bytes checked against what was written. (That fixture needs a privileged loopback
mount to build, the one step `mkfs.btrfs -r` can't do; the multi-node keyed-lookup fixture and the
simple one are still produced unprivileged.)

lzo uses btrfs's own segment framing (a total-length header, then per-segment length-prefixed
`lzo1x` blocks, each ≤ one sector, with the segment header kept off sector boundaries). It is
unit-tested with a symmetric encoder (`BtrfsLzoDecodeTest`) and — like zstd — end-to-end
(`BtrfsLzoFileSystemTest`) against a real `mount -o compress-force=lzo` image: an inline
single-segment extent and a full 128 KiB ~32-segment extent read back to the exact bytes written.
