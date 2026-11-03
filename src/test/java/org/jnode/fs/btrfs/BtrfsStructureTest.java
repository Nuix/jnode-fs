package org.jnode.fs.btrfs;

import java.io.File;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.DataStructureAsserts;
import org.jnode.fs.FileSystemTestUtils;
import org.junit.Test;

/**
 * The standard jnode-fs structure assertions (tree + sizes + per-file MD5) over each btrfs fixture,
 * matching the convention used by the XFS/ext4/HFS+ test suites.
 *
 * @author David Baird
 */
public class BtrfsStructureTest {

    private static void assertImage(String image, String expected) throws Exception {
        File testFile = FileSystemTestUtils.getTestFile(image);
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);
            DataStructureAsserts.assertStructure(fs, expected);
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void simpleImage() throws Exception {
        assertImage("org/jnode/fs/btrfs/btrfs-simple.img",
                "type: BTRFS vol: total:314572800 free:314400768\n" +
                "  /; \n" +
                "    readme.txt; 17; 738dca5c323a24ef20121ee920cf6c9d\n" +
                "    data; \n" +
                "      repeated.log; 5000; 1811ed45a571e515ed0faefc29f27026\n" +
                "    docs; \n" +
                "      tiny.txt; 1; 9dd4e461268c8034f5c8564e155c67a6\n" +
                "      notes.md; 23; 15acd125c66a304556b9b26e31e7430d\n");
    }

    @Test
    public void zstdImage() throws Exception {
        // every file zstd-compressed (compress-force=zstd); md5s are of the decompressed bytes
        assertImage("org/jnode/fs/btrfs/btrfs-zstd.img",
                "type: BTRFS vol: total:536870912 free:536698880\n" +
                "  /; \n" +
                "    docs; \n" +
                "      tiny.txt; 36; 981ad15b6b60d8fc12670c194b0d682a\n" +
                "      mid.txt; 20000; 5a1d68aa720f89f79b3e480229a42ff8\n" +
                "    big.log; 131072; f99fcfa9d0d0ece9993266ea81df4dc7\n");
    }

    @Test
    public void lzoImage() throws Exception {
        // every file lzo-compressed (compress-force=lzo); md5s are of the decompressed bytes
        assertImage("org/jnode/fs/btrfs/btrfs-lzo.img",
                "type: BTRFS vol: total:536870912 free:536698880\n" +
                "  /; \n" +
                "    docs; \n" +
                "      tiny.txt; 35; 6eafd6f47b61be646189f7b7d2f46276\n" +
                "      mid.txt; 20000; 5a1d68aa720f89f79b3e480229a42ff8\n" +
                "    big.log; 131072; f99fcfa9d0d0ece9993266ea81df4dc7\n");
    }

    @Test
    public void symlinkImage() throws Exception {
        // symlinks surface as small "files" whose content is the target path (md5s of the path text)
        assertImage("org/jnode/fs/btrfs/btrfs-symlink.img",
                "type: BTRFS vol: total:105906176 free:105742336\n" +
                "  /; \n" +
                "    sub; \n" +
                "      plain.txt; 6; 5839145a19c13f3ffb0a3b9527e0a912\n" +
                "      rel.txt; 13; e942d748a84637d78f1941486a01cd20\n" +
                "    abslink; 13; a69c84a628c199bc14f166b95b055c4a\n" +
                "    link.txt; 10; 4d6f333d2bc24ffddcca34414a0cb12d\n" +
                "    target.txt; 22; 481cc2e27f9828e09046533a1373c80d\n");
    }
}
