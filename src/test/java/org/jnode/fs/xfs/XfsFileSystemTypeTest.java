package org.jnode.fs.xfs;

import java.io.File;

import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.xfs.inode.INode;
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
            final XfsFileSystem fileSystem = type.create(device, true);
            final MyBPlusTree bPlusTree = ag.getInodeBTreeOnAllocationGroupIndex(0);
            final INode rootInode = bPlusTree.getINode(ag.getRootINode());

            final XfsEntry xfsEntry = new XfsEntry(rootInode, "/", 0, fileSystem, null);
            final XfsDirectory xfsDirectory = new XfsDirectory(xfsEntry);
            final XfsEntry rootEntry = fileSystem.getRootEntry();
            final XfsDirectory rootDir = new XfsDirectory(rootEntry);
            System.out.println(rootDir.isRoot());
            System.out.println(rootDir.readEntries());
        }
        finally
        {
            testFile.delete();
        }
    }

}