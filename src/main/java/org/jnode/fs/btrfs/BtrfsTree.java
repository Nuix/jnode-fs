package org.jnode.fs.btrfs;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Arrays;

import org.jnode.util.LittleEndian;

/**
 * Reads btrfs B-trees. A tree block is one {@code nodeSize} region at a logical address; its header
 * gives the level. Level 0 is a leaf (an array of {@code (key, offset, size)} items whose data
 * follows the header); level &gt; 0 is an internal node (key pointers to child blocks). A full
 * {@link #scanLeaves} visits every item in the tree (used once per subvolume to collect inodes and
 * directory entries); a keyed {@link #search} descends to a single item in O(depth) node reads and
 * iterates forward in key order (used to read one inode's extents without rescanning the tree).
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
    /** Depth guard against a corrupt/looping tree. */
    private static final int MAX_LEVEL = 16;

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

    /** Reads one tree block (node or leaf) at a logical address, verifying its checksum if enabled. */
    public byte[] readBlock(long logical) throws IOException {
        long physical = chunkMap.toPhysical(logical);
        byte[] block = reader.read(physical, nodeSize);
        if (verifyChecksums && csumType == BtrfsChecksum.CSUM_TYPE_CRC32C
                && !BtrfsChecksum.crc32cValid(block, block.length)) {
            throw new IOException("btrfs crc32c checksum mismatch at logical " + logical);
        }
        return block;
    }

    /**
     * Visits every item in the tree rooted at {@code rootLogical}.
     *
     * @param rootLogical the logical address of the tree's root block.
     * @param visitor     receives each leaf item's key and data, in key order.
     * @throws IOException if a tree block cannot be read (or fails checksum verification).
     */
    public void scanLeaves(long rootLogical, ItemVisitor visitor) throws IOException {
        scan(rootLogical, visitor, 0);
    }

    private void scan(long logical, ItemVisitor visitor, int depth) throws IOException {
        if (depth > MAX_LEVEL) {
            throw new IOException("btrfs tree too deep (corrupt?)");
        }
        byte[] block = readBlock(logical);
        int nrItems = (int) LittleEndian.getUInt32(block, BtrfsConstants.HDR_NRITEMS);
        int level = block[BtrfsConstants.HDR_LEVEL] & 0xFF;

        if (level == 0) {
            for (int i = 0; i < nrItems; i++) {
                int itemPos = BtrfsConstants.HEADER_SIZE + i * BtrfsConstants.ITEM_SIZE;
                if (itemPos + BtrfsConstants.ITEM_SIZE > block.length) {
                    break;
                }
                BtrfsDiskKey key = new BtrfsDiskKey(block, itemPos);
                int dataOff = (int) LittleEndian.getUInt32(block, itemPos + BtrfsConstants.KEY_SIZE);
                int dataLen = (int) LittleEndian.getUInt32(block, itemPos + BtrfsConstants.KEY_SIZE + 4);
                int start = BtrfsConstants.HEADER_SIZE + dataOff;
                if (start < 0 || dataLen < 0 || start + dataLen > block.length) {
                    continue; // guard against a corrupt item pointer
                }
                visitor.item(key, Arrays.copyOfRange(block, start, start + dataLen));
            }
        } else {
            for (int i = 0; i < nrItems; i++) {
                int ptrPos = BtrfsConstants.HEADER_SIZE + i * BtrfsConstants.KEY_PTR_SIZE;
                if (ptrPos + BtrfsConstants.KEY_PTR_SIZE > block.length) {
                    break;
                }
                long childLogical = LittleEndian.getInt64(block, ptrPos + BtrfsConstants.KEY_SIZE);
                scan(childLogical, visitor, depth + 1);
            }
        }
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

        /** A copy of the current item's data bytes (only when {@link #valid()}). */
        public byte[] data() {
            Frame f = path.peek();
            int itemPos = BtrfsConstants.HEADER_SIZE + f.slot * BtrfsConstants.ITEM_SIZE;
            int dataOff = (int) LittleEndian.getUInt32(f.block, itemPos + BtrfsConstants.KEY_SIZE);
            int dataLen = (int) LittleEndian.getUInt32(f.block, itemPos + BtrfsConstants.KEY_SIZE + 4);
            int start = BtrfsConstants.HEADER_SIZE + dataOff;
            if (start < 0 || dataLen < 0 || start + dataLen > f.block.length) {
                return new byte[0]; // corrupt item pointer
            }
            return Arrays.copyOfRange(f.block, start, start + dataLen);
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
            while (true) {
                Frame f = push(logical);
                if (f.level == 0) {
                    f.slot = lowerBound(f, target); // first leaf item >= target (may be nrItems)
                    normalize();                    // spill to the next leaf if this one is all < target
                    return;
                }
                int child = childSlot(f, target); // deepest child whose range can hold target
                f.slot = child + 1;                // resume at the following child when we return here
                logical = f.childAt(child);
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
                f.slot++;              // advance the parent so we take the next child after this subtree
                push(child).slot = 0;  // descend the child's left edge
            }
        }

        private Frame push(long logical) throws IOException {
            if (path.size() > MAX_LEVEL) {
                throw new IOException("btrfs tree too deep (corrupt?)");
            }
            Frame f = new Frame(readBlock(logical));
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
