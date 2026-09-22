package org.jnode.fs.btrfs;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Snapshot detection: a ROOT_ITEM is a snapshot iff its {@code parent_uuid} is non-zero. This is what
 * lets the reader descend real subvolumes (root, home — zero parent_uuid) while skipping snapshot
 * clones by default. A real snapshotted image needs a privileged mount to build, so the classifier is
 * tested directly on synthetic ROOT_ITEM bytes.
 */
public class BtrfsSnapshotTest {

    /** A modern btrfs_root_item is 439 bytes; parent_uuid lives at 0x107 (16 bytes). */
    private static byte[] rootItem() {
        return new byte[439];
    }

    @Test
    public void regularSubvolumeIsNotASnapshot() {
        // all-zero parent_uuid == created with `btrfs subvolume create` (root/home)
        assertFalse(BtrfsVolume.isSnapshotRootItem(rootItem()));
    }

    @Test
    public void nonZeroParentUuidIsASnapshot() {
        byte[] data = rootItem();
        data[BtrfsConstants.ROOT_ITEM_PARENT_UUID + 7] = 0x42; // one non-zero byte anywhere in the uuid
        assertTrue(BtrfsVolume.isSnapshotRootItem(data));
    }

    @Test
    public void lastByteOfParentUuidCounts() {
        byte[] data = rootItem();
        data[BtrfsConstants.ROOT_ITEM_PARENT_UUID + BtrfsConstants.UUID_SIZE - 1] = 0x01;
        assertTrue(BtrfsVolume.isSnapshotRootItem(data));
    }

    @Test
    public void preUuidRootItemIsTreatedAsRegular() {
        // very old btrfs root_items predate the uuid fields; can't distinguish -> not a snapshot
        byte[] shortItem = new byte[239];
        assertFalse(BtrfsVolume.isSnapshotRootItem(shortItem));
    }
}
