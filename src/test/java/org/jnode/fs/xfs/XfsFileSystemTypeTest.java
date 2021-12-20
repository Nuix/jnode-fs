package org.jnode.fs.xfs;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.logger.Logger_File;
import org.jnode.partitions.PartitionTableEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class XfsFileSystemTypeTest {
    @Rule
    public final JUnitRuleMockery mockery = new JUnitRuleMockery();

    @Mock
    PartitionTableEntry partitionTableEntry;
    Logger_File log;
    MyXfsCreateOutput output_object;

    @Before
    public void initialize() throws IOException {
        log = new Logger_File();
        output_object = new MyXfsCreateOutput("");
    }

    @Test
    public void testSupports() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            byte[] buffer = new byte[512];
            assertThat(type.supports(partitionTableEntry, buffer, device), is(true));
            XfsFileSystem xfs = new XfsFileSystem(device, type);
            xfs.read();
            Superblock xfssuperb = xfs.getSuperblock();
            log.write_to_file(2, "SUPERBLOCK OFFSET: " + xfssuperb.getOffset());
            log.write_to_file(2, "SUPERBLOCK MAGICNUM: " + xfssuperb.getMagic());
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void testSimpleImage() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");


        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            byte[] buffer = new byte[512];
            assertThat(type.supports(partitionTableEntry, buffer, device), is(true));
            final MyXfsFileSystem ag = new MyXfsFileSystem(device);
            final MyInode rootInode = ag.getINode(ag.getRootINode());
            final MyInodeHeader rootInodeDirectoryHeader = rootInode.getDirectoryHeader();

            log.write_to_file(2, String.valueOf(rootInodeDirectoryHeader.getCount()));
            log.write_to_file(2, String.valueOf(rootInodeDirectoryHeader.getI8Count()));
            log.write_to_file(2, String.valueOf(rootInodeDirectoryHeader.getParentInode()));

            for (IMyDirectory directory : rootInode.getDirectories()) {
                log.write_to_file(2, "Directory " + directory.getName() + " / " + directory.getINodeNumber());
                output_object.write_to_disk(false, new File(directory.getName()), null);
            }

            // Using test Inode for testfile.txt inside current image
            for (MyExtentInformation info : ag.getINode(100).getExtentInfo()) {
                log.write_to_file(2, "blockCount: " + String.valueOf(info.getBlockCount()) + " startOffset: " + String.valueOf(info.getStartOffset()) + " startBlock: " + String.valueOf(info.getStartBlock()));
                final long start = info.getStartBlock() * ag.getBlockSize();
                String startByte = Long.toHexString(start);
                String endByte = Long.toHexString((info.getBlockCount() + info.getStartBlock()) * ag.getBlockSize());
                System.out.printf("check %d * %d = 0x%s to %d * %d = 0x%s%n", info.getStartBlock(), ag.getBlockSize(), startByte, info.getBlockCount() + info.getStartBlock(), ag.getBlockSize(), endByte);
                final ByteBuffer data = ByteBuffer.allocate((int) (ag.getBlockSize() * info.getStartBlock()));
                device.read(start,data);
                java.lang.String str = new String(data.array(), StandardCharsets.US_ASCII);
                File file = new File("testfile.txt");
                output_object.write_to_disk(false, file, data.array());
                log.write_to_file(2, str);
            }
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void testComplexImage() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-2.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            byte[] buffer = new byte[512];
            assertThat(type.supports(partitionTableEntry, buffer, device), is(true));
            final MyXfsFileSystem ag = new MyXfsFileSystem(device);
            final MyInode rootInode = ag.getINode(ag.getRootINode());

            recursiveFsPrint(rootInode,"",ag);
//            for (MyShortFormDirectory directory : rootInode.getDirectories()) {
//                System.out.println("Directory " + directory.getName() + " / " + directory.getINodeNumber());
//            }

//            final MyInode iNode = ag.getINode(100);
//            for (MyExtentInformation info : iNode.getExtentInfo()) {
//                System.out.printf("blockCount %d startOffset %d startBlock %d%n", info.getBlockCount(), info.getStartOffset(), info.getStartBlock());
//                final long start = info.getStartBlock() * ag.getBlockSize();
//                String startByte = Long.toHexString(start);
//                String endByte = Long.toHexString((info.getBlockCount() + info.getStartBlock()) * ag.getBlockSize());
//                System.out.printf("check %d * %d = 0x%s to %d * %d = 0x%s%n", info.getStartBlock(), ag.getBlockSize(), startByte, info.getBlockCount() + info.getStartBlock(), ag.getBlockSize(), endByte);
//                final ByteBuffer data = ByteBuffer.allocate((int) iNode.getSize());
//                device.read(start,data);
//
//                java.lang.String str = new String(data.array(), StandardCharsets.US_ASCII);
//                System.out.println(str);
//            }
        } finally {
            testFile.delete();
        }
    }
    @Test
    public void testOldImage() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/xfs.dd");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
//            byte[] buffer = new byte[512];
//            assertThat(type.supports(partitionTableEntry, buffer, device), is(true));
            final MyXfsFileSystem ag = new MyXfsFileSystem(device);
            final MyInode rootInode = ag.getINode(ag.getRootINode());

            recursiveFsPrint(rootInode,"",ag);
        } finally {
            testFile.delete();
        }
    }
    @Test
    public void testCentos() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/centos-xfs.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
//            byte[] buffer = new byte[512];
//            assertThat(type.supports(partitionTableEntry, buffer, device), is(true));
            final MyXfsFileSystem ag = new MyXfsFileSystem(device);
            final MyInode rootInode = ag.getINode(ag.getRootINode());

            recursiveFsPrint(rootInode,"",ag);
        } finally {
            testFile.delete();
        }
    }

    private static String byteArrToBinary(byte[] arr){
        StringBuilder sb = new StringBuilder(arr.length * 8);
        for (byte b :arr){
            sb.append(byteToBinary(b));
        }
        return sb.toString();
    }

    private static String byteToBinary(byte b){
        return String.format("%8s",Integer.toString(b,2)).replace(" ","0");
    }

    private void recursiveFsPrint(MyInode inode,String str,MyXfsFileSystem fs) throws IOException {
        if (inode.getFormat() == MyInode.INodeFormat.EXTENT.val && !inode.isDirectory()){
            System.out.println(str);
            return;
        }
        final List<? extends IMyDirectory> directories = inode.getDirectories();
        if (directories.size() == 0){
            System.out.println(str + "/");
            return;
        }
        for (IMyDirectory directory : directories) {
            final String name = directory.getName();
            if (name.equals(".") || name.equals("..")){
                continue;
            }
            String str2 = str + "/" + name; // + " -- " + directory.getINodeNumber();
            recursiveFsPrint(fs.getINode(directory.getINodeNumber()),str2,fs);
        }
    }
}