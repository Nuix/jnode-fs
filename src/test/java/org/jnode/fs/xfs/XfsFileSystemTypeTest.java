package org.jnode.fs.xfs;

import java.io.File;

import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.partitions.PartitionTableEntry;
import org.junit.Rule;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

public class XfsFileSystemTypeTest
{
    @Rule
    public final JUnitRuleMockery mockery = new JUnitRuleMockery();

    @Mock
    PartitionTableEntry partitionTableEntry;

    @Test
    public void testSupports() throws Exception
    {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");

        try (FileDevice device = new FileDevice(testFile, "r"))
        {
            XfsFileSystemType type = new XfsFileSystemType();
            byte[] buffer = new byte[512];
            assertThat(type.supports(partitionTableEntry, buffer, device), is(true));
        }
        finally
        {
            testFile.delete();
        }
    }

    @Test
    public void testRandom() throws Exception
    {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");


        try (FileDevice device = new FileDevice(testFile, "r"))
        {
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

        }
        finally
        {
            testFile.delete();
        }
    }

}