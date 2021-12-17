package org.jnode.fs.xfs;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.DataStructureAsserts;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.*;
import org.jnode.fs.service.FileSystemService;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

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

            String expectedStructure = "type: XFS vol:\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000\u0000 total:134217728 free:130490368\n" +
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


    @Test
    public void testXfsMetaData() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");

        // Arrange
        String expectedStructure =
                "  /; \n" +
                        "    atime : 11/17/2021 00:50:04; ctime : 11/17/2021 00:48:33; mtime : 11/17/2021 00:48:33\n" +
                        "    owner : 0; group : 0; size : 57; mode : 777; \n" +
                        "    folder1; \n" +
                        "        atime : 11/17/2021 00:50:07; ctime : 11/17/2021 00:50:07; mtime : 11/17/2021 00:50:07\n" +
                        "        owner : 1000; group : 1000; size : 30; mode : 775; \n" +
                        "      this_is_fine.jpg; \n" +
                        "            atime : 11/17/2021 00:50:07; ctime : 11/17/2021 00:50:07; mtime : 05/19/2019 18:45:52\n" +
                        "            owner : 1000; group : 1000; size : 53072; mode : 744; \n" +
                        "    folder 2; \n" +
                        "        atime : 11/17/2021 00:52:07; ctime : 11/17/2021 00:52:07; mtime : 11/17/2021 00:52:07\n" +
                        "        owner : 1000; group : 1000; size : 21; mode : 775; \n" +
                        "      xfs.zip; \n" +
                        "            atime : 11/17/2021 00:52:07; ctime : 11/17/2021 00:52:07; mtime : 11/17/2021 00:52:03\n" +
                        "            owner : 1000; group : 1000; size : 20103; mode : 744; \n" +
                        "    testfile.txt; \n" +
                        "        atime : 11/17/2021 00:48:33; ctime : 11/17/2021 00:48:33; mtime : 11/17/2021 00:48:33\n" +
                        "        owner : 1000; group : 1000; size : 20; mode : 664; \n";

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            XfsFileSystem fs = type.create(device, true);

            FSEntry entry = fs.getRootEntry();
            StringBuilder actual = new StringBuilder(expectedStructure.length());

            DataStructureAsserts.buildXfsMetaDataStructure(entry, actual, "  ");

            assertThat(actual.toString(), is(expectedStructure));

        } finally {
            testFile.delete();
        }
    }

}
