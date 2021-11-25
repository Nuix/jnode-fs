package org.jnode.fs.xfs;

import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FileSystemException;
import org.jnode.fs.FileSystemTestUtils;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

public class XfsFileSystemTest {

    private static final String TEST_1_DB_INSPECTION_STRING =
            "magicnum = 0x58465342\n" +
                    "blocksize = 4096\n" +
                    "dblocks = 32768\n" +
                    "rblocks = 0\n" +
                    "rextents = 0\n" +
                    "uuid = e660fd99-e85f-490e-8499-cb8c9395546c\n" +
                    "logstart = 16389\n" +
                    "rootino = 96\n" +
                    "rbmino = 97\n" +
                    "rsumino = 98\n" +
                    "rextsize = 1\n" +
                    "agblocks = 8192\n" +
                    "agcount = 4\n" +
                    "rbmblocks = 0\n" +
                    "logblocks = 855\n" +
                    "versionnum = 0xb4a5\n" +
                    "sectsize = 512\n" +
                    "inodesize = 512\n" +
                    "inopblock = 8\n" +
                    "fname = \"\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\\000\"\n" +
                    "blocklog = 12\n" +
                    "sectlog = 9\n" +
                    "inodelog = 9\n" +
                    "inopblog = 3\n" +
                    "agblklog = 13\n" +
                    "rextslog = 0\n" +
                    "inprogress = 0\n" +
                    "imax_pct = 25\n" +
                    "icount = 128\n" +
                    "ifree = 120\n" +
                    "fdblocks = 31858\n" +
                    "frextents = 0\n" +
                    "uquotino = null\n" +
                    "gquotino = null\n" +
                    "qflags = 0\n" +
                    "flags = 0\n" +
                    "shared_vn = 0\n" +
                    "inoalignmt = 4\n" +
                    "unit = 0\n" +
                    "width = 0\n" +
                    "dirblklog = 0\n" +
                    "logsectlog = 0\n" +
                    "logsectsize = 0\n" +
                    "logsunit = 1\n" +
                    "features2 = 0x18a\n" +
                    "bad_features2 = 0x18a\n" +
                    "features_compat = 0\n" +
                    "features_ro_compat = 0x1\n" +
                    "features_incompat = 0x1\n" +
                    "features_log_incompat = 0\n" +
                    "crc = 0x544616da (correct)\n" +
                    "spino_align = 0\n" +
                    "pquotino = null\n" +
                    "lsn = 0x100000022\n" +
                    "meta_uuid = 00000000-0000-0000-0000-000000000000";
    private static final String TEST_1_AG_FREE_SPACE_BLOCK = "magicnum = 0x58414746\n" +
            "versionnum = 1\n" +
            "seqno = 0\n" +
            "length = 8192\n" +
            "bnoroot = 1\n" +
            "cntroot = 2\n" +
            "rmaproot = \n" +
            "refcntroot = \n" +
            "bnolevel = 1\n" +
            "cntlevel = 1\n" +
            "rmaplevel = 0\n" +
            "refcntlevel = 0\n" +
            "rmapblocks = 0\n" +
            "refcntblocks = 0\n" +
            "flfirst = 0\n" +
            "fllast = 3\n" +
            "flcount = 4\n" +
            "freeblks = 8161\n" +
            "longest = 8159\n" +
            "btreeblks = 0\n" +
            "uuid = e660fd99-e85f-490e-8499-cb8c9395546c\n" +
            "lsn = 0x100000016\n" +
            "crc = 0x134dfd68 (correct)";

    private static final String TEST_1_INODE_INFO_DB =
            "magicnum = 0x58414749\n" +
            "versionnum = 1\n" +
            "seqno = 0\n" +
            "length = 8192\n" +
            "count = 64\n" +
            "root = 3\n" +
            "level = 1\n" +
            "freecount = 58\n" +
            "newino = 96\n" +
            "dirino = null\n" +
            "unlinked[0-63] = \n" +
            "uuid = e660fd99-e85f-490e-8499-cb8c9395546c\n" +
            "crc = 0x3f71239d (correct)\n" +
            "lsn = 0x100000011\n" +
            "free_root = 4\n" +
            "free_level = 1";

    public static final String TEST_2_FREE_LIST_HEADER_DB =
            "magicnum = 0x5841464c\n" +
            "seqno = 0\n" +
            "uuid = e660fd99-e85f-490e-8499-cb8c9395546c\n" +
            "lsn = 0\n" +
            "crc = 0x20299b4a (correct)\n";
    @Rule
    public final JUnitRuleMockery mockery = new JUnitRuleMockery();

    File testFile;
    FileDevice device;
    XfsFileSystem fs;
    MySuperblock superblock;

    @Before
    public void initialize() throws IOException, FileSystemException {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");
        device = new FileDevice(testFile, "r");
        XfsFileSystemType type = new XfsFileSystemType();
        fs = type.create(device, true);
        superblock = new MySuperblock(device, 0);
    }

    @After
    public void cleanup() {
        device.close();
        testFile.delete();
    }

    @Test
    public void testSuperblock() throws Exception {
        System.out.println("SUPERBLOCK INFO:");
        System.out.println("SUPERBLOCK SIGNATURE: " + superblock.isValidSignature());
        final String superblockDb = superblock.getXfsDbInspectionString();
        System.out.println(superblockDb);
        System.out.println("SuperblockVersion : " + superblock.getVersion());
        System.out.println("IS VALID SUPERBLOCK: " + superblockDb.equals(TEST_1_DB_INSPECTION_STRING));
    }

    @Test
    public void testFreeSpaceBlock() throws Exception {
        System.out.println("FREE BLOCK INFO:");
        MyAGFreeSpaceBlock freeblock = superblock.getAGFreeSpaceBlock();
        System.out.println("FREEBLOCK SIGNATURE: " + freeblock.isValidSignature());
        final String freeBlockDB = freeblock.getXfsDbInspectionString();
        System.out.println(freeBlockDB);
        System.out.println("IS VALID FREEBLOCK: " + freeBlockDB.equals(TEST_1_AG_FREE_SPACE_BLOCK));
    }

    @Test
    public void testINodeInformation() throws Exception {
        final MyINodeInformation inode = superblock.getINodeInformation();
        System.out.println("INODE INFO SIGNATURE: " + inode.getAsciiSignature() + inode.isValidSignature());
        System.out.println(inode.getXfsDbInspectionString());
    }
    @Test
    public void testUnkown() throws Exception {
        for (int i = 0; i < 5000; i++) {
            final MyAGFreeListHeader myAGFreeListHeader = new MyAGFreeListHeader(device,256 * i);
            final String signature = myAGFreeListHeader.getAsciiSignature();
            if (signature.length() == 4) {
                System.out.println("i: " + i + " SIGNATURE: " + signature);
            }
        }
    }

    @Test
    public void testFreeListHeader() throws Exception {
        final MyAGFreeListHeader header = superblock.getAGFreeListHeader();
        System.out.println("INODE INFO SIGNATURE: " + header.getAsciiSignature() + header.isValidSignature());
        System.out.println(header.getXfsDbInspectionString());
    }

    @Test
    public void testBTreeCanRead() throws Exception {
        Stream.of(superblock.getBlockCountBTree(),superblock.getBlockOffsetBTree(),superblock.getV5FreeINodeBTree(),superblock.getV5AllocatedINodeBTree())
                .forEach(tree -> {
                    try {
                        System.out.println("BTree INFO SIGNATURE: " + tree.getAsciiSignature());
                        System.out.println("BTree UUID: " + tree.getUuid());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

    }

}