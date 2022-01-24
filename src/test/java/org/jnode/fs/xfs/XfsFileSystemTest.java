package org.jnode.fs.xfs;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.DataStructureAsserts;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.*;
import org.jnode.fs.service.FileSystemService;
import org.jnode.fs.spi.FSEntryTable;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.TimeZone;

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

            String expectedStructure = "type: XFS vol: total:134217728 free:130490368\n" +
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
                "    atime : 2021-11-17T06:50:04.000+0000; ctime : 2021-11-17T06:48:33.000+0000; mtime : 2021-11-17T06:48:33.000+0000\n" +
                "    owner : 0; group : 0; size : 57; mode : 777; \n" +
                "    folder1; \n" +
                "        atime : 2021-11-17T06:50:07.000+0000; ctime : 2021-11-17T06:50:07.000+0000; mtime : 2021-11-17T06:50:07.000+0000\n" +
                "        owner : 1000; group : 1000; size : 30; mode : 775; \n" +
                "      this_is_fine.jpg; \n" +
                "            atime : 2021-11-17T06:50:07.000+0000; ctime : 2021-11-17T06:50:07.000+0000; mtime : 2019-05-19T23:45:52.000+0000\n" +
                "            owner : 1000; group : 1000; size : 53072; mode : 744; \n" +
                "    folder 2; \n" +
                "        atime : 2021-11-17T06:52:07.000+0000; ctime : 2021-11-17T06:52:07.000+0000; mtime : 2021-11-17T06:52:07.000+0000\n" +
                "        owner : 1000; group : 1000; size : 21; mode : 775; \n" +
                "      xfs.zip; \n" +
                "            atime : 2021-11-17T06:52:07.000+0000; ctime : 2021-11-17T06:52:07.000+0000; mtime : 2021-11-17T06:52:03.000+0000\n" +
                "            owner : 1000; group : 1000; size : 20103; mode : 744; \n" +
                "    testfile.txt; \n" +
                "        atime : 2021-11-17T06:48:33.000+0000; ctime : 2021-11-17T06:48:33.000+0000; mtime : 2021-11-17T06:48:33.000+0000\n" +
                "        owner : 1000; group : 1000; size : 20; mode : 664; \n";

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            XfsFileSystem fs = type.create(device, true);

            FSEntry entry = (XfsEntry) fs.getRootEntry();
            StringBuilder actual = new StringBuilder(expectedStructure.length());

            buildXfsMetaDataStructure(entry, actual, "  ");

            assertThat(actual.toString(), is(expectedStructure));

        } finally {
            testFile.delete();
        }
    }

    /**
     * Builds up the structure for the given file system entry to get the metadata.
     *
     * @param entry  the entry to process.
     * @param actual the string to append to.
     * @param indent the indent level.
     * @throws IOException if an error occurs.
     */
    public static void buildXfsMetaDataStructure(FSEntry entry, StringBuilder actual, String indent) throws IOException
    {
        actual.append(indent);
        actual.append(entry.getName());
        actual.append("; \n");

        if (entry.isDirectory()) {
            getXfsMetadata(entry, actual, indent);
        }
        if (entry.isFile()) {
            FSFile file = entry.getFile();
            getXfsMetadata(entry, actual, indent);
        }
        else {
            FSDirectory directory = entry.getDirectory();

            Iterator<? extends FSEntry> iterator = directory.iterator();

            while (iterator.hasNext()) {
                FSEntry child = iterator.next();

                if (".".equals(child.getName()) || "..".equals(child.getName()))
                {
                    continue;
                }

                buildXfsMetaDataStructure(child, actual, indent + "  ");
            }
        }
    }

    /**
     * Get the metadata.
     *
     * @param entry  the entry to process.
     * @param actual the string to append to.
     * @param indent the indent level.
     *
     */
    private static StringBuilder getXfsMetadata(FSEntry entry, StringBuilder actual,String indent) throws IOException {
        actual.append(indent);
        actual.append(indent);
        actual.append("atime : " +  getDate(((FSEntryLastAccessed) entry).getLastAccessed() ));
        actual.append("; ");
        actual.append("ctime : " +  getDate(((FSEntryCreated) entry).getCreated()));
        actual.append("; ");
        actual.append("mtime : " +  getDate(((FSEntryLastChanged) entry).getLastChanged()) +"\n" );
        actual.append(indent);
        actual.append(indent);
        actual.append("owner : " + ((XfsEntry) entry).getINode().getUid() );
        actual.append("; ");
        actual.append("group : " + ((XfsEntry) entry).getINode().getGid() );
        actual.append("; ");
        actual.append("size : " +  ((XfsEntry) entry).getINode().getSize() );
        actual.append("; ");
        String mode = Integer.toOctalString(((XfsEntry) entry).getINode().getMode());
        actual.append("mode : " +  mode.substring(mode.length()-3));
        actual.append("; \n");

        return actual;
    }

    /**
     * Convert epoch to human-readable date.
     *
     * @param date  the epoch value.
     */
    private static String getDate(long date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return simpleDateFormat.format(new java.util.Date(date));
    }

    @Test
    public void testCentos() throws Exception {

        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/centos-xfs.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            XfsFileSystem fs = type.create(device, true);
            FSEntry entry = fs.getRootEntry();
            StringBuilder actual = new StringBuilder(1024);
            buildXfsDirStructure(entry, actual, "  ");
            //System.out.println(actual);
        } finally {
            testFile.delete();
        }
    }

    /**
     * Build the directory string.
     *
     * @param entry  the entry to process.
     * @param actual the string to append to.
     * @param indent the indent level.
     *
     * @throws IOException
     */
    private void buildXfsDirStructure(FSEntry entry,StringBuilder actual, String indent) throws IOException {

        actual.append(indent);
        actual.append(entry.getName() );
        actual.append("; \n");

        if (entry.isDirectory()) {
            FSDirectory directory = entry.getDirectory();

            Iterator<? extends FSEntry> iterator = directory.iterator();

            while (iterator.hasNext()) {
                FSEntry child = iterator.next();

                if ( ".".equals(child.getName()) || "..".equals(child.getName()) ) {
                    continue;
                }
                buildXfsDirStructure(child, actual, indent + "  ");
            }
        }
    }
}
