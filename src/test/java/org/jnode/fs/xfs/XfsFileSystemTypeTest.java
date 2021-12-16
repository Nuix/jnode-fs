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
            XfsFileSystem xfs = type.create(device, true);
            System.out.println("THE SUPERBLOCK INFO OF THIS ONE IS: " + xfs.getSuperblock());
            String magic_num = Long.toHexString(xfs.getSuperblock().getMagic());
            String ascii_magic_numbre = XfsFileSystem.HexToAscii(magic_num);
            System.out.println("THE SUPERBLOCK MAGIC NUMBER IS: " + ascii_magic_numbre);
            System.out.println("VOLUME NAME IS: " + xfs.getVolumeName());
            System.out.println("TOTAL SPACE: " + xfs.getTotalSpace());
            System.out.println("FREE SPACE: " + xfs.getFreeSpace());
            System.out.println("USABLE SPACE: " + xfs.getUsableSpace());
        }
        finally
        {
            testFile.delete();
        }
    }

}