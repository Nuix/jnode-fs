package org.jnode.fs.xfs;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.DataStructureAsserts;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.service.FileSystemService;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

public class XfsFileSystemTest {

    private FileSystemService fss;

    @Before
    public void setUp() {
        // create file system service.
        fss = FileSystemTestUtils.createFSService(XfsFileSystemType.class.getName());
    }

    @Test
    public void testImage1() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);

            String expectedStructure = "type: XFS vol:null total:0 free:0\n" +
                    "  /; \n" +
                    "    folder1; \n" +
                    "      this_is_fine.jpg; 53072; ee04081c3182a44a1c6944e94012e977\n" +
                    "    folder 2; \n" +
                    "      xfs.zip; 20103; d5f8c07fdff365b45b8af1ae7622a98d\n" +
                    "    testfile.txt; 20; 5dd39cab1c53c2c77cd352983f9641e1\n";
            DataStructureAsserts.assertStructure(fs, expectedStructure);
        } finally {
            testFile.delete();
        }
    }
}
