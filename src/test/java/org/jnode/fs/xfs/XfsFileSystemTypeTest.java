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
import org.jnode.fs.ext2.INode;
import org.jnode.partitions.PartitionTableEntry;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class XfsFileSystemTypeTest {
    @Rule
    public final JUnitRuleMockery mockery = new JUnitRuleMockery();

    @Mock
    PartitionTableEntry partitionTableEntry;

    @Test
    public void testSupports() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            byte[] buffer = new byte[512];
            assertThat(type.supports(partitionTableEntry, buffer, device), is(true));
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

            System.out.println(rootInodeDirectoryHeader.getCount());
            System.out.println(rootInodeDirectoryHeader.getI8Count());
            System.out.println(rootInodeDirectoryHeader.getParentInode());

            for (MyShortFormDirectory directory : rootInode.getDirectories()) {
                System.out.println("Directory " + directory.getName() + " / " + directory.getINodeNumber());
            }
            // Using test Inode for testfile.txt inside current image
            for (MyExtentInformation info : ag.getINode(100).getExtentInfo()) {
                System.out.printf("blockCount %d startOffset %d startBlock %d%n", info.getBlockCount(), info.getStartOffset(), info.getStartBlock());
                final long start = info.getStartBlock() * ag.getBlockSize();
                String startByte = Long.toHexString(start);
                String endByte = Long.toHexString((info.getBlockCount() + info.getStartBlock()) * ag.getBlockSize());
                System.out.printf("check %d * %d = 0x%s to %d * %d = 0x%s%n", info.getStartBlock(), ag.getBlockSize(), startByte, info.getBlockCount() + info.getStartBlock(), ag.getBlockSize(), endByte);
                final ByteBuffer data = ByteBuffer.allocate((int) (ag.getBlockSize() * info.getStartBlock()));
                device.read(start,data);

                java.lang.String str = new String(data.array(), StandardCharsets.US_ASCII);
                System.out.println(str);
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
            final MyInodeHeader rootInodeDirectoryHeader = rootInode.getDirectoryHeader();

            System.out.println(rootInodeDirectoryHeader.getCount());
            System.out.println(rootInodeDirectoryHeader.getI8Count());
            System.out.println(rootInodeDirectoryHeader.getParentInode());

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

    private void recursiveFsPrint(MyInode inode,String str,MyXfsFileSystem fs) throws IOException {
        if (inode.getFormat() == MyInode.INodeFormat.FILE.val){
            System.out.println(str);
            return;
        }
        final List<MyShortFormDirectory> directories = inode.getDirectories();
        if (directories.size() == 0){
            System.out.println(str + "/");
            return;
        }
        for (MyShortFormDirectory directory : directories) {
            String str2 = str + "/" + directory.getName() ;//+ " -- " + directory.getINodeNumber() + "(" + directory.getFileType() + ")";
            recursiveFsPrint(fs.getINode(directory.getINodeNumber()),str2,fs);
        }
    }
}