package org.jnode.fs.xfs;

import org.jmock.auto.Mock;
import org.jmock.integration.junit4.JUnitRuleMockery;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.*;

import org.jnode.fs.service.FileSystemService;
import org.jnode.fs.spi.FSEntryTable;

import org.jnode.partitions.PartitionTableEntry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class XfsFileSystemTest {

    private FileSystemService fss;
    XfsFileSystem fs;

    @Before
    public void setUp() throws Exception
    {
        // create file system service.
        fss = FileSystemTestUtils.createFSService(XfsFileSystemType.class.getName());

        String [] xfsTestDirectory = { "/folder1", "/folder 2", "/testfile.txt" };
        for ( String xfsEntryElement : xfsTestDirectory ) {
            File file = new File("src/test/resources/"+xfsEntryElement).getAbsoluteFile();
            FileSystemTestUtils.deleteDirectory(file);
        }
    }

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
            fs = type.create(device, true);
            byte[] buffer = new byte[512];
            assertThat(type.supports(partitionTableEntry, buffer, device), is(true));

            XfsDirectory xfsdir = new XfsDirectory(fs.getRootEntry());
            /**
             * Table of entries of our parent
             */
            xfsdir.readEntries();
            createXfsFileStructureFromTestImage(xfsdir,"");
            String [] xfsEntriesArray = { "/folder1", "/folder 2","/folder1/this_is_fine.jpg", "/folder 2/xfs.zip","/testfile.txt" };
            for ( String xfsEntryElement : xfsEntriesArray ) {
                assertThat(FileSystemTestUtils.testIfExists( xfsEntryElement ), is(true));
            }
        }
        finally
        {
            testFile.delete();
        }
    }

    private void createXfsFileStructureFromTestImage(XfsDirectory inode,String str) throws IOException {

        final FSEntryTable entries = inode.readEntries();
        FileSystemTestUtils.createDirectory( str );
        if (entries.size() == 0){
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            FSEntry current = entries.get(i);
            XfsEntry xfsCurrentEntry = (XfsEntry) current;
            if (( current.getName().equals("..") || current.getName().equals(".")) ) {
                continue;
            }
            if (current.isFile()) {
                File f = FileSystemTestUtils.createFile( str + "/" + current.getName() );
                ByteBuffer allocate = ByteBuffer.allocate((int)xfsCurrentEntry.getINode().getSize());
                xfsCurrentEntry.readUnchecked(0,allocate);
                Files.write(f.toPath(), allocate.array() );
            } else {
                String str2 = str + "/" + current.getName();
                createXfsFileStructureFromTestImage(new XfsDirectory(xfsCurrentEntry) ,str2);
            }
        }
    }
}
