package org.jnode.fs.xfs;

import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FileSystemException;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.logger.Logger_File;
import org.junit.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
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
    Logger_File log;
    FileDevice device;
    XfsFileSystem fs;
    MyXfsFileSystem fileSystem;

    @Before
    public void initialize() throws IOException, FileSystemException {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");
        device = new FileDevice(testFile, "r");
        XfsFileSystemType type = new XfsFileSystemType();
        fs = type.create(device, true);
        log = new Logger_File();
        fileSystem = new MyXfsFileSystem(device);
    }

    @After
    public void cleanup() {
        device.close();
        testFile.delete();
        fileSystem = null;
    }

    @Test
    public void testSuperblock() throws Exception {
        final MySuperblock superblock = fileSystem.getSuperBlockOnAllocationGroupIndex(0);
        log.write_to_file(2, "SUPERBLOCK INFO:");
        log.write_to_file(2, "SUPERBLOCK SIGNATURE: " + superblock.isValidSignature());
        final String superblockDb = superblock.getXfsDbInspectionString();
        log.write_to_file(2, superblockDb);
        log.write_to_file(2,"SuperblockVersion : " + superblock.getVersion());
        log.write_to_file(2,"IS VALID SUPERBLOCK: " + superblockDb.equals(TEST_1_DB_INSPECTION_STRING));
    }

    @Test
    public void testFreeSpaceBlock() throws Exception {
        log.write_to_file(2,"FREE BLOCK INFO:");
        MyAGFreeSpaceBlock freeblock = fileSystem.getAGFreeSpaceBlockOnAllocationGroupIndex(0);
        log.write_to_file(2,"FREEBLOCK SIGNATURE: " + freeblock.isValidSignature());
        final String freeBlockDB = freeblock.getXfsDbInspectionString();
        log.write_to_file(2, freeBlockDB);
        log.write_to_file(2,"IS VALID FREEBLOCK: " + freeBlockDB.equals(TEST_1_AG_FREE_SPACE_BLOCK));
    }

    @Test
    public void testINodeInformation() throws Exception {
        final MyINodeInformation inode = fileSystem.getINodeInformationOnAllocationGroupIndex(0);
        log.write_to_file(2,"INODE INFO SIGNATURE: " + inode.getAsciiSignature() + inode.isValidSignature());
        System.out.println(inode.getXfsDbInspectionString());
    }

    @Test
    public void testUnkown() throws Exception {
//        final MyAllocationGroup allocationGroup2 = this.fileSystem.getNextAllocationGroup();
//        allocationGroup.getAGFreeSpaceBlock();
//        allocationGroup.getINodeInformation()
//        final MyAllocationGroup allocationGroup3 = allocationGroup2.getNextAllocationGroup();
//        final MyAllocationGroup allocationGroup4 = allocationGroup3.getNextAllocationGroup();
//        final String signature = allocationGroup.getSuperBlock().getAsciiSignature();
//        System.out.println("i: " + allocationGroup.getSuperBlock().getOffset() + " SIGNATURE: " + signature);
    }

    @Test
    public void testFreeListHeader() throws Exception {
        final MyAGFreeListHeader header = fileSystem.getAGFreeListHeaderOnAllocationGroupIndex(0);
        log.write_to_file(2, "INODE INFO SIGNATURE: " + header.getAsciiSignature() + header.isValidSignature());
        log.write_to_file(2, header.getXfsDbInspectionString());
    }

    @Test
    public void testBTreeCanRead() throws Exception {
        for (MyBPlusTree tree : fileSystem.getBTreesOnAllocationGroupIndex(0)) {
            log.write_to_file(2,"BTree INFO SIGNATURE: " + tree.getAsciiSignature());
            log.write_to_file(2,"BTree UUID: " + tree.getUuid());
            log.write_to_file(2,"BTree depth: " + tree.getDepth());
            log.write_to_file(2,"BTree before : " + tree.getPreviousBlockNumber());
            log.write_to_file(2,"BTree current #: " + tree.getRecordNumber());
            log.write_to_file(2,"BTree next: " + tree.getNextBlockNumber());
            log.write_to_file(2,"----------------");
        }
    }

    @Test
    public void generate_output_structure() throws IOException {
        log.write_to_file(2,"WE TRY TO CREATE THE OUTPUT FOR XFS READING.");
        MyXfsCreateOutput output_object = new MyXfsCreateOutput("");
        File first_dir = new File("folder1");
        File second_dir = new File("folder 2");

        File first_file = new File("folder1/testfile.txt");
        String txt_content = "this is a test file.";
        byte[] bytes_to_write = txt_content.getBytes();

        output_object.write_to_disk(false, first_dir, null);
        output_object.write_to_disk(false, second_dir, null);
        output_object.write_to_disk(false, first_file, bytes_to_write);
    }

}