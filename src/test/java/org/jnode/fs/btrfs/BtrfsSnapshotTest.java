package org.jnode.fs.btrfs;

import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Snapshot classification: a ROOT_ITEM is a <em>read-only snapshot</em> iff its {@code parent_uuid}
 * is non-zero <em>and</em> {@code BTRFS_ROOT_SUBVOL_RDONLY} is set. That lets the reader descend real
 * subvolumes (root, home — zero parent_uuid) and writable snapshots (a rolled-back or restored root
 * keeps its parent_uuid but is not read-only) while skipping snapper/Timeshift clones by default.
 * The classifier is tested directly on synthetic ROOT_ITEM bytes; the end-to-end listing behaviour
 * is covered by {@link BtrfsRobustnessTest}.
 */
public class BtrfsSnapshotTest {

    /** A modern btrfs_root_item is 439 bytes; parent_uuid lives at 0x107 (16 bytes), flags at 0xd0. */
    private static byte[] rootItem() {
        return new byte[439];
    }

    private static byte[] withParent(byte[] data, int uuidByte) {
        data[BtrfsConstants.ROOT_ITEM_PARENT_UUID + uuidByte] = 0x42;
        return data;
    }

    private static byte[] readOnly(byte[] data) {
        LittleEndian.setInt64(data, BtrfsConstants.ROOT_ITEM_FLAGS, BtrfsConstants.ROOT_SUBVOL_RDONLY);
        return data;
    }

    @Test
    public void regularSubvolumeIsNotASnapshot() {
        // all-zero parent_uuid == created with `btrfs subvolume create` (root/home)
        assertFalse(BtrfsVolume.isReadOnlySnapshot(rootItem()));
    }

    @Test
    public void readOnlySnapshotIsASnapshot() {
        assertTrue(BtrfsVolume.isReadOnlySnapshot(readOnly(withParent(rootItem(), 7))));
    }

    @Test
    public void lastByteOfParentUuidCounts() {
        assertTrue(BtrfsVolume.isReadOnlySnapshot(readOnly(withParent(rootItem(), BtrfsConstants.UUID_SIZE - 1))));
    }

    @Test
    public void writableSnapshotIsTreatedAsARegularSubvolume() {
        // parent_uuid set but not read-only: a `snapper rollback` / Timeshift-restored live root
        assertFalse(BtrfsVolume.isReadOnlySnapshot(withParent(rootItem(), 7)));
    }

    @Test
    public void readOnlyFlagAloneIsNotASnapshot() {
        // `btrfs property set ro true` on a plain subvolume: read-only, but nothing was cloned
        assertFalse(BtrfsVolume.isReadOnlySnapshot(readOnly(rootItem())));
    }

    @Test
    public void preUuidRootItemIsTreatedAsRegular() {
        // very old btrfs root_items predate the uuid fields; can't distinguish -> not a snapshot
        byte[] shortItem = new byte[239];
        assertFalse(BtrfsVolume.isReadOnlySnapshot(shortItem));
    }
}
