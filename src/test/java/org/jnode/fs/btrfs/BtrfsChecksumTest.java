package org.jnode.fs.btrfs;

import java.io.File;
import java.io.RandomAccessFile;

import org.jnode.fs.FileSystemTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * crc32c metadata checksums, validated against a real (known-good) btrfs image: the FS-tree root
 * node's stored crc32c must match what we compute over its body, and any corruption — of the body or
 * of the stored checksum — must be detected.
 */
public class BtrfsChecksumTest {

    private File testFile;
    private RandomAccessFile raf;
    private BtrfsVolume volume;

    @Before
    public void setUp() throws Exception {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-multinode.img");
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
    public void realNodeChecksumValidates() throws Exception {
        byte[] node = volume.tree().readBlock(volume.subvolBytenr(BtrfsConstants.OBJECTID_FS_TREE));
        assertTrue("a genuine node must pass crc32c", BtrfsChecksum.crc32cValid(node, node.length));
    }

    @Test
    public void corruptBodyIsRejected() throws Exception {
        byte[] node = volume.tree().readBlock(volume.subvolBytenr(BtrfsConstants.OBJECTID_FS_TREE));
        node[node.length / 2] ^= 0xFF; // flip a byte in the body
        assertFalse(BtrfsChecksum.crc32cValid(node, node.length));
    }

    @Test
    public void corruptStoredChecksumIsRejected() throws Exception {
        byte[] node = volume.tree().readBlock(volume.subvolBytenr(BtrfsConstants.OBJECTID_FS_TREE));
        node[0] ^= 0xFF; // flip a byte of the stored csum
        assertFalse(BtrfsChecksum.crc32cValid(node, node.length));
    }

    @Test
    public void verificationEnabledWalksAGoodTreeWithoutError() throws Exception {
        // every node read during the walk is checksum-verified -> a good image passes cleanly
        volume.setVerifyChecksums(true);
        int[] count = {0};
        volume.tree().scanLeaves(volume.subvolBytenr(BtrfsConstants.OBJECTID_FS_TREE),
                (key, data) -> count[0]++);
        assertTrue("verified walk should still see every item", count[0] > 2000);
    }
}
