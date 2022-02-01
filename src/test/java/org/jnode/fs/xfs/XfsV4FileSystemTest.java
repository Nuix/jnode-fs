package org.jnode.fs.xfs;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.*;
import org.jnode.fs.service.FileSystemService;
import org.jnode.fs.xfs.inode.INode;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class XfsV4FileSystemTest {

    private FileSystemService fss;

    @Before
    public void setUp() {
        // create file system service.
        fss = FileSystemTestUtils.createFSService(XfsFileSystemType.class.getName());
    }

    @Test
    public void testImage1() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v4/default_named_image.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);
            final XfsEntry rootEntry = fs.getRootEntry();

            final StringBuilder builder = new StringBuilder();
            buildXfsDirStructure(rootEntry,builder,"");

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
     */
    private static void buildXfsDirStructure(XfsEntry entry,StringBuilder actual, String indent) throws IOException {

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
                buildXfsDirStructure((XfsEntry)child, actual, indent + "  ");
            }
        }
    }
}
