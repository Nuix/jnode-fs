package org.jnode.fs.btrfs;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FSEntryLastAccessed;
import org.jnode.fs.FileSystemTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Entry metadata: unique ids across subvolumes, inode timestamps, and the volume label.
 *
 * @author David Baird
 */
public class BtrfsMetadataTest {

    /**
     * The fixture's inodes all carry this mtime/atime/ctime (captured from
     * {@code btrfs inspect-internal dump-tree}: {@code mtime 1783349589.0}); otime is 0 because
     * {@code mkfs.btrfs -r} never sets a birth time.
     */
    private static final long FIXTURE_TIME_MS = 1_783_349_589_000L;

    @Test
    public void timestampsComeFromTheInode() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-simple.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);
            Map<String, FSEntry> byPath = new HashMap<String, FSEntry>();
            collect(fs.getRootEntry().getDirectory(), "", byPath);

            FSEntry readme = byPath.get("readme.txt");
            assertEquals(FIXTURE_TIME_MS, readme.getLastModified());
            assertEquals(FIXTURE_TIME_MS, ((FSEntryLastAccessed) readme).getLastAccessed());
            assertEquals(FIXTURE_TIME_MS, ((BtrfsEntry) readme).getLastChanged());
            assertEquals("mkfs -r sets no birth time", 0, ((BtrfsEntry) readme).getCreated());

            // directories carry times too
            assertEquals(FIXTURE_TIME_MS, byPath.get("docs").getLastModified());
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void unlabelledVolumeHasAnEmptyName() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-simple.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);
            assertEquals("", fs.getVolumeName());
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void labelIsDecodedFromTheSuperblock() throws Exception {
        // synthetic superblock: magic + a label; parsed through the normal reader path
        byte[] sb = new byte[BtrfsConstants.SB_SYS_CHUNK_ARRAY + BtrfsConstants.SYS_CHUNK_ARRAY_MAX];
        System.arraycopy(BtrfsConstants.MAGIC, 0, sb, BtrfsConstants.SB_MAGIC, BtrfsConstants.MAGIC.length);
        byte[] label = "SpaceMap Test".getBytes("UTF-8");
        System.arraycopy(label, 0, sb, BtrfsConstants.SB_LABEL, label.length); // NUL-terminated by the zero fill

        BtrfsBlockReader reader = (offset, dst, off, length) -> {
            for (int i = 0; i < length; i++) {
                long src = offset + i - BtrfsConstants.SUPERBLOCK_OFFSET;
                dst[off + i] = (src >= 0 && src < sb.length) ? sb[(int) src] : 0;
            }
        };
        assertEquals("SpaceMap Test", new BtrfsSuperblock(reader).getLabel());
    }

    @Test
    public void idsAreUniqueAcrossSubvolumes() throws Exception {
        // btrfs objectids restart at 256 in every subvolume: the same objectId in two subvolumes MUST
        // yield different entry ids. Nodes are constructed directly (subvolume fixtures need a
        // privileged mount); the id format is what's under test.
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-simple.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);
            BtrfsVolume.InodeInfo info = BtrfsVolume.InodeInfo.missingDirectory();
            BtrfsNode inTop = new BtrfsNode(fs.getVolume(), 5, 256, "a", info);
            BtrfsNode inSubvol = new BtrfsNode(fs.getVolume(), 257, 256, "b", info);

            BtrfsEntry topEntry = new BtrfsEntry(inTop, "a", fs, null);
            BtrfsEntry subEntry = new BtrfsEntry(inSubvol, "b", fs, null);

            assertEquals("5-256", topEntry.getId());
            assertEquals("257-256", subEntry.getId());
            assertNotEquals("same objectId, different subvolume -> different id",
                    topEntry.getId(), subEntry.getId());
        } finally {
            testFile.delete();
        }
    }

    private static void collect(FSDirectory dir, String prefix, Map<String, FSEntry> out) throws Exception {
        java.util.Iterator<? extends FSEntry> it = dir.iterator();
        while (it.hasNext()) {
            FSEntry e = it.next();
            String name = e.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String path = prefix.isEmpty() ? name : prefix + "/" + name;
            out.put(path, e);
            if (e.isDirectory()) {
                collect(e.getDirectory(), path, out);
            }
        }
    }

}
