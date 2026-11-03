package org.jnode.fs.btrfs;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jnode.util.LittleEndian;

/**
 * Reads btrfs B-trees. A tree block is one {@code nodeSize} region at a logical address; its header
 * gives the level. Level 0 is a leaf (an array of {@code (key, offset, size)} items whose data
 * follows the header); level &gt; 0 is an internal node (key pointers to child blocks). A full
 * {@link #scanLeaves} visits every item in the tree (used once per subvolume to collect inodes and
 * directory entries); a keyed {@link #search} descends to a single item in O(depth) node reads and
 * iterates forward in key order (used to read one inode's extents without rescanning the tree).
 *
 * <p>The reader treats the image as untrusted. Every block's header must carry the address it was
 * read from and a level below {@code BTRFS_MAX_LEVEL}; a child's level must be exactly one less than
 * its parent's; and neither a full scan nor a cursor will enter the same block twice. Together these turn a corrupt
 * or crafted tree (a self-pointing node, a DAG whose shared children would multiply the walk, a
 * pointer into the wrong block) into an {@link IOException} after a bounded number of reads.</p>
 *
 * @author David Baird
 */
public class BtrfsTree {

    /** Receives each leaf item: its key and a copy of its data bytes. */
    public interface ItemVisitor {
        void item(BtrfsDiskKey key, byte[] itemData) throws IOException;
    }

    private final BtrfsBlockReader reader;
    private final BtrfsChunkMap chunkMap;
    private final int nodeSize;

    private boolean verifyChecksums;
    private int csumType = BtrfsChecksum.CSUM_TYPE_CRC32C;

    public BtrfsTree(BtrfsBlockReader reader, BtrfsChunkMap chunkMap, int nodeSize) {
        this.reader = reader;
        this.chunkMap = chunkMap;
        this.nodeSize = nodeSize;
    }

    /**
     * Enables per-block checksum verification. Only crc32c is verified; for any other {@code csumType}
     * verification is silently skipped (we can't check it, but won't reject the volume).
     *
     * @param verify   whether to verify each block as it is read.
     * @param csumType the superblock's checksum algorithm (0 = crc32c).
     */
    public void setChecksumVerification(boolean verify, int csumType) {
        this.verifyChecksums = verify;
        this.csumType = csumType;
    }

    /**
     * Reads one tree block (node or leaf) at a logical address, verifying its checksum if enabled.
     * The header's {@code bytenr} must equal {@code logical} (btrfs always writes a block's own
     * address into it — a mismatch means the pointer or the chunk map is wrong) and its level must
     * be below {@link BtrfsConstants#MAX_LEVEL}.
     */
    public byte[] readBlock(long logical) throws IOException {
        long physical = chunkMap.toPhysical(logical);
        byte[] block = reader.read(physical, nodeSize);
        if (block.length < BtrfsConstants.HEADER_SIZE) {
            throw new IOException("btrfs node size " + nodeSize + " is smaller than a tree block header");
        }
        if (verifyChecksums && csumType == BtrfsChecksum.CSUM_TYPE_CRC32C
                && !BtrfsChecksum.crc32cValid(block, block.length)) {
            throw new IOException("btrfs crc32c checksum mismatch at logical " + logical);
        }
        long bytenr = LittleEndian.getInt64(block, BtrfsConstants.HDR_BYTENR);
        if (bytenr != logical) {
            throw new IOException("btrfs tree block at logical " + logical + " claims to be at " + bytenr
                    + " (misdirected pointer or corrupt chunk map)");
        }
        int level = block[BtrfsConstants.HDR_LEVEL] & 0xFF;
        if (level >= BtrfsConstants.MAX_LEVEL) {
            throw new IOException("btrfs tree block at logical " + logical + " has level " + level
                    + " (max " + (BtrfsConstants.MAX_LEVEL - 1) + ")");
        }
        return block;
    }

    /** A block the walk expects at {@code expectedLevel} (or any level, when negative, for a root). */
    private byte[] readBlock(long logical, int expectedLevel) throws IOException {
        byte[] block = readBlock(logical);
        int level = block[BtrfsConstants.HDR_LEVEL] & 0xFF;
        if (expectedLevel >= 0 && level != expectedLevel) {
            throw new IOException("btrfs tree block at logical " + logical + " has level " + level
                    + ", expected " + expectedLevel);
        }
        return block;
    }

    /**
     * Visits every item in the tree rooted at {@code rootLogical}.
     *
     * @param rootLogical the logical address of the tree's root block.
     * @param visitor     receives each leaf item's key and data, in key order.
     * @throws IOException if a tree block cannot be read (or fails checksum verification), or the
     *                     tree is malformed (a block reached twice, or a level out of sequence).
     */
    public void scanLeaves(long rootLogical, ItemVisitor visitor) throws IOException {
        scan(rootLogical, -1, visitor, new HashSet<Long>());
    }

    private void scan(long logical, int expectedLevel, ItemVisitor visitor, Set<Long> visited)
            throws IOException {
        if (!visited.add(logical)) {
            // within one tree every block has exactly one parent pointer, so a revisit means a
            // cycle or a DAG — either way a walk that would never end
            throw new IOException("btrfs tree block at logical " + logical + " reached twice (corrupt tree)");
        }
        byte[] block = readBlock(logical, expectedLevel);
        int nrItems = (int) LittleEndian.getUInt32(block, BtrfsConstants.HDR_NRITEMS);
        int level = block[BtrfsConstants.HDR_LEVEL] & 0xFF;

        if (level == 0) {
            for (int i = 0; i < nrItems; i++) {
                int itemPos = BtrfsConstants.HEADER_SIZE + i * BtrfsConstants.ITEM_SIZE;
                if (itemPos + BtrfsConstants.ITEM_SIZE > block.length) {
                    break;
                }
                byte[] data = itemData(block, itemPos);
                if (data == null) {
                    continue; // corrupt item pointer
                }
                visitor.item(new BtrfsDiskKey(block, itemPos), data);
            }
        } else {
            for (int i = 0; i < nrItems; i++) {
                int ptrPos = BtrfsConstants.HEADER_SIZE + i * BtrfsConstants.KEY_PTR_SIZE;
                if (ptrPos + BtrfsConstants.KEY_PTR_SIZE > block.length) {
                    break;
                }
                long childLogical = LittleEndian.getInt64(block, ptrPos + BtrfsConstants.KEY_SIZE);
                scan(childLogical, level - 1, visitor, visited);
            }
        }
    }

    /**
     * A copy of the leaf item's data bytes, or {@code null} if its {@code (offset, size)} pair does
     * not lie within the block. Both fields are u32 on disk; the arithmetic is arranged so that a
     * value near 2^31 cannot wrap the sum negative and slip past the check.
     */
    private static byte[] itemData(byte[] block, int itemPos) {
        int dataOff = (int) LittleEndian.getUInt32(block, itemPos + BtrfsConstants.KEY_SIZE);
        int dataLen = (int) LittleEndian.getUInt32(block, itemPos + BtrfsConstants.KEY_SIZE + 4);
        int room = block.length - BtrfsConstants.HEADER_SIZE;
        if (dataOff < 0 || dataLen < 0 || dataLen > room || dataOff > room - dataLen) {
            return null;
        }
        int start = BtrfsConstants.HEADER_SIZE + dataOff;
        return Arrays.copyOfRange(block, start, start + dataLen);
    }

    /**
     * Positions a {@link Cursor} at the first item whose key is &gt;= {@code target}, descending from
     * the root and picking, at each internal node, the child whose key range covers the target. If no
     * item is &gt;= target the returned cursor is not {@link Cursor#valid() valid}. Iterate forward
     * with {@link Cursor#next()}.
     *
     * @param rootLogical the logical address of the tree's root block.
     * @param target      the search key (lower bound).
     * @return a cursor at the first item &gt;= {@code target}; not valid if none exists.
     * @throws IOException if a tree block cannot be read.
     */
    public Cursor search(long rootLogical, BtrfsDiskKey target) throws IOException {
        Cursor cursor = new Cursor();
        cursor.seek(rootLogical, target);
        return cursor;
    }

    /** One tree block on the descent path, with our position ({@code slot}) in it. */
    private final class Frame {
        final byte[] block;
        final int level;
        final int nrItems;
        int slot;

        Frame(byte[] block) {
            this.block = block;
            this.level = block[BtrfsConstants.HDR_LEVEL] & 0xFF;
            int raw = (int) LittleEndian.getUInt32(block, BtrfsConstants.HDR_NRITEMS);
            int stride = (level == 0) ? BtrfsConstants.ITEM_SIZE : BtrfsConstants.KEY_PTR_SIZE;
            int fits = Math.max(0, (block.length - BtrfsConstants.HEADER_SIZE) / stride);
            this.nrItems = Math.min(Math.max(raw, 0), fits); // clamp a corrupt count to what fits
        }

        BtrfsDiskKey keyAt(int i) {
            int stride = (level == 0) ? BtrfsConstants.ITEM_SIZE : BtrfsConstants.KEY_PTR_SIZE;
            return new BtrfsDiskKey(block, BtrfsConstants.HEADER_SIZE + i * stride);
        }

        long childAt(int i) {
            return LittleEndian.getInt64(block,
                    BtrfsConstants.HEADER_SIZE + i * BtrfsConstants.KEY_PTR_SIZE + BtrfsConstants.KEY_SIZE);
        }
    }

    /**
     * A forward cursor over a tree's items in key order, starting at the first item &gt;= a search
     * key. btrfs leaves carry no sibling pointers, so crossing a leaf boundary means walking the
     * saved path back up to the next unread child and descending its left edge.
     */
    public final class Cursor {
        private final ArrayDeque<Frame> path = new ArrayDeque<Frame>(); // head = current (deepest) frame
        /**
         * Every block this cursor has descended into. A forward walk enters each block of a sound
         * tree once, so a repeat means nodes share a child — a DAG that would otherwise be iterated
         * fan-out^depth times (or, with empty shared leaves, spun through without ever yielding).
         */
        private final Set<Long> visited = new HashSet<Long>();

        private Cursor() {
        }

        /** True while positioned on a real leaf item; false once the tree is exhausted. */
        public boolean valid() {
            Frame f = path.peek();
            return f != null && f.level == 0 && f.slot < f.nrItems;
        }

        /** The current item's key (only when {@link #valid()}). */
        public BtrfsDiskKey key() {
            return path.peek().keyAt(path.peek().slot);
        }

        /** A copy of the current item's data bytes (only when {@link #valid()}); empty if corrupt. */
        public byte[] data() {
            Frame f = path.peek();
            byte[] data = itemData(f.block, BtrfsConstants.HEADER_SIZE + f.slot * BtrfsConstants.ITEM_SIZE);
            return data != null ? data : new byte[0];
        }

        /** Advances to the next item in key order. */
        public void next() throws IOException {
            Frame f = path.peek();
            if (f == null) {
                return;
            }
            f.slot++;
            normalize();
        }

        private void seek(long rootLogical, BtrfsDiskKey target) throws IOException {
            long logical = rootLogical;
            int expectedLevel = -1; // the root may sit at any level
            while (true) {
                Frame f = push(logical, expectedLevel);
                if (f.level == 0) {
                    f.slot = lowerBound(f, target); // first leaf item >= target (may be nrItems)
                    normalize();                    // spill to the next leaf if this one is all < target
                    return;
                }
                int child = childSlot(f, target); // deepest child whose range can hold target
                f.slot = child + 1;                // resume at the following child when we return here
                logical = f.childAt(child);
                expectedLevel = f.level - 1;
            }
        }

        /** Make the head a leaf sitting on a valid item, or empty the path (tree exhausted). */
        private void normalize() throws IOException {
            while (!path.isEmpty()) {
                Frame f = path.peek();
                if (f.level == 0) {
                    if (f.slot < f.nrItems) {
                        return; // valid item
                    }
                    path.pop(); // leaf exhausted -> back up to its parent
                    continue;
                }
                if (f.slot >= f.nrItems) {
                    path.pop(); // internal node exhausted -> back up
                    continue;
                }
                long child = f.childAt(f.slot);
                f.slot++;                         // advance the parent so we take the next child after this subtree
                push(child, f.level - 1).slot = 0; // descend the child's left edge
            }
        }

        private Frame push(long logical, int expectedLevel) throws IOException {
            if (path.size() >= BtrfsConstants.MAX_LEVEL) {
                throw new IOException("btrfs tree too deep (corrupt?)");
            }
            if (!visited.add(logical)) {
                throw new IOException("btrfs tree block at logical " + logical + " reached twice (corrupt tree)");
            }
            Frame f = new Frame(readBlock(logical, expectedLevel));
            path.push(f);
            return f;
        }

        /** Largest slot whose key is &lt;= target (0 if all keys exceed target) — the child to descend. */
        private int childSlot(Frame f, BtrfsDiskKey target) {
            int lo = 0;
            int hi = f.nrItems - 1;
            int res = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (f.keyAt(mid).compareTo(target) <= 0) {
                    res = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return res;
        }

        /** Smallest slot whose key is &gt;= target, or nrItems if none. */
        private int lowerBound(Frame f, BtrfsDiskKey target) {
            int lo = 0;
            int hi = f.nrItems;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (f.keyAt(mid).compareTo(target) < 0) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            return lo;
        }
    }
}
