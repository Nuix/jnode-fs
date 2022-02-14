package org.jnode.fs.apfs;

import java.io.File;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.DataStructureAsserts;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.service.FileSystemService;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class ApfsFileSystemTest
{
    private FileSystemService fss;

    @Before
    public void setUp() throws Exception
    {
        // create file system service.
        fss = FileSystemTestUtils.createFSService(ApfsFileSystemType.class.getName());
    }

    @Ignore("APFS not implemented")
    @Test
    public void testSimpleImage() throws Exception
    {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/apfs/simple.dd");
        try (FileDevice device = new FileDevice(testFile, "r"))
        {
            ApfsFileSystemType type = fss.getFileSystemType(ApfsFileSystemType.ID);
            ApfsFileSystem fs = type.create(device, true);

            String expectedStructure =
                "";

            DataStructureAsserts.assertStructure(fs, expectedStructure);
        }
        finally
        {
            testFile.delete();
        }
    }
}

