package org.jnode.fs.xfs;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSAttribute;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.service.FileSystemService;
import org.jnode.fs.xfs.inode.INode;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class XfsV4FileSystemTest {

    private FileSystemService fss;

    @Before
    public void setUp() {
        // create file system service.
        fss = FileSystemTestUtils.createFSService(XfsFileSystemType.class.getName());
    }

    @Test
    public void testShortFormDir() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v4/dir_types_xfs_v4.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);
            INode iNode = fs.getINode(131);
            XfsEntry entry = new XfsEntry(iNode, "", 0, fs, null);
            List<? extends FSEntry> entries = iteratorToList(entry.getDirectory().iterator());
            assertThat(entries.size(), is(4));

        } finally {
            testFile.delete();
        }
    }

    @Test
    public void testBlockDir() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v4/dir_types_xfs_v4.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);

            INode iNode = fs.getINode(131200);
            XfsEntry entry = new XfsEntry(iNode, "", 0, fs, null);
            List<? extends FSEntry> entries = iteratorToList(entry.getDirectory().iterator());
            assertThat(entries.size(), is(22));
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void testLeafDir() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v4/dir_types_xfs_v4.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);

            INode iNode = fs.getINode(281472);
            XfsEntry entry = new XfsEntry(iNode, "", 0, fs, null);
            List<? extends FSEntry> entries = iteratorToList(entry.getDirectory().iterator());
            assertThat(entries.size(), is(202));
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void testBtreeDirs() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v4/dir_types_xfs_v4.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);

            // TODO: figure out if this can be enabled
//            XfsEntry smallBtreeEntry = new XfsEntry(fs.getINode(393344),"",0,fs,null);
//            List<? extends FSEntry> smallBtreeEntries = iteratorToList(smallBtreeEntry.getDirectory().iterator());
//            assertThat(smallBtreeEntries.size(), is(2582));

            XfsEntry mediumBtreeEntry = new XfsEntry(fs.getINode(134), "", 0, fs, null);
            List<? extends FSEntry> mediumBtreeEntries = iteratorToList(mediumBtreeEntry.getDirectory().iterator());
            assertThat(mediumBtreeEntries.size(), is(300_002));
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void testLeafAttributes() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v4/ubuntu_attr_v4.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);

            XfsEntry leafAttributesEntry = new XfsEntry(fs.getINode(132), "", 0, fs, null);
            List<FSAttribute> attributes = leafAttributesEntry.getAttributes();
            assertThat(attributes.size(), is(1));
            assertThat(attributes.get(0).getValue().length, is(257));
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void testMultiLevelBtreeDir() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v4/mult_btree_level_v4.img");

        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);

            XfsEntry leafAttributesEntry = new XfsEntry(fs.getINode(131), "", 0, fs, null);
            List<? extends FSEntry> entries = iteratorToList(leafAttributesEntry.getDirectory().iterator());
            assertThat(entries.size(), is(317438));
        } finally {
            testFile.delete();
        }
    }


    public <T> List<T> iteratorToList(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        return StreamSupport.stream(spliterator, false).collect(Collectors.toList());
    }


    /**
     * Build the directory string.
     * TODO: review and possibly remove
     *
     * @param entry  the entry to process.
     * @param actual the string to append to.
     * @param indent the indent level.
     */
    private static void buildXfsDirStructure(XfsEntry entry, StringBuilder actual, String indent) throws IOException {

        actual.append(indent);
        actual.append(entry.getName());
        actual.append("; \n");

        if (entry.isDirectory()) {
            FSDirectory directory = entry.getDirectory();

            Iterator<? extends FSEntry> iterator = directory.iterator();

            while (iterator.hasNext()) {
                FSEntry child = iterator.next();

                if (".".equals(child.getName()) || "..".equals(child.getName())) {
                    continue;
                }
                buildXfsDirStructure((XfsEntry) child, actual, indent + "  ");
            }
        }
    }
}
