package org.jnode.fs.xfs;

import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FileSystemException;
import org.jnode.fs.FileSystemTestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class XfsFileSystemTest {

    @Rule
    public final JUnitRuleMockery mockery = new JUnitRuleMockery();

    File testFile;
    FileDevice device;
    XfsFileSystem fs;


    @Before
    public void initialize() throws IOException, FileSystemException {
        testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");
        device = new FileDevice(testFile, "r");
        XfsFileSystemType type = new XfsFileSystemType();
        fs = type.create(device, true);
    }

    @After
    public void cleanup() {
        device.close();
        testFile.delete();
    }


    @Test
    public void testSupports() throws Exception {
        final MySuperblock superblock = new MySuperblock(device, 0);
        System.out.println(superblock.getXfsDbInspectionString());
    }
}