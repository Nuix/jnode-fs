package org.jnode.fs.btrfs;

import java.io.File;
import java.io.RandomAccessFile;

import org.jnode.fs.FileSystemTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Symlink recognition, end-to-end against a real image ({@code mkfs.btrfs -r} preserves symlinks as
 * {@code S_IFLNK} inodes with the target stored inline). A symlink node reports {@link
 * BtrfsNode#isSymlink()}, a size equal to the target-path length, and the target via {@link
 * BtrfsNode#getSymlinkTarget()} — and is never descended.
 */
public class BtrfsSymlinkTest {

    private File testFile;
    private RandomAccessFile raf;
    private BtrfsVolume volume;

    @Before
    public void setUp() throws Exception {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-symlink.img");
        raf = new RandomAccessFile(testFile, "r");
        BtrfsBlockReader reader = (offset, dst, off, length) -> {
            raf.seek(offset);
            raf.readFully(dst, off, length);
        };
        volume = new BtrfsVolume(reader);
    }

    @After
    public void tearDown() throws Exception {
        if (raf != null) {
            raf.close();
        }
        if (testFile != null) {
            testFile.delete();
        }
    }

    @Test
    public void symlinksAreFlaggedWithTheirTargetsAndRealFilesAreNot() throws Exception {
        BtrfsNode root = volume.getRoot();

        BtrfsNode link = child(root, "link.txt");     // -> target.txt (relative)
        assertTrue("link.txt is a symlink", link.isSymlink());
        assertFalse(link.isDirectory());
        assertEquals("target.txt", link.getSymlinkTarget());
        assertEquals(10, link.getSize()); // size is the target-path length, no trailing NUL

        BtrfsNode abs = child(root, "abslink");        // -> /etc/hostname (absolute)
        assertTrue(abs.isSymlink());
        assertEquals("/etc/hostname", abs.getSymlinkTarget());

        BtrfsNode target = child(root, "target.txt");  // a real file
        assertFalse("target.txt is a real file", target.isSymlink());
        assertEquals("", target.getSymlinkTarget());

        // a relative symlink one level down
        BtrfsNode rel = child(child(root, "sub"), "rel.txt");
        assertTrue(rel.isSymlink());
        assertEquals("../target.txt", rel.getSymlinkTarget());
    }

    @Test
    public void symlinksAreNotDescended() throws Exception {
        // getChildren() on a symlink yields nothing (it's not a directory) -> no follow, no loops
        BtrfsNode link = child(volume.getRoot(), "link.txt");
        assertTrue(link.getChildren().isEmpty());
    }

    private static BtrfsNode child(BtrfsNode dir, String name) throws Exception {
        for (BtrfsNode n : dir.getChildren()) {
            if (name.equals(n.getName())) {
                return n;
            }
        }
        assertNotNull("child not found: " + name, null);
        return null;
    }
}
