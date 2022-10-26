package org.jnode.fs.xfs;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSAttribute;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.service.FileSystemService;
import org.jnode.fs.xfs.inode.FileMode;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

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
            XfsEntry entry = DataTestUtils.getDescendantData(type.create(device, true), "short-form-dir");
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
            XfsEntry entry = DataTestUtils.getDescendantData(type.create(device, true), "block-dir");
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

            XfsEntry entry = DataTestUtils.getDescendantData(fs, "leaf-dir");
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
            XfsEntry smallBtreeEntry = DataTestUtils.getDescendantData(fs, "node-dir");
            List<? extends FSEntry> smallBtreeEntries = iteratorToList(smallBtreeEntry.getDirectory().iterator());
            assertThat(smallBtreeEntries.size(), is(3002));

            XfsEntry mediumBtreeEntry = DataTestUtils.getDescendantData(fs, "btree-dir");
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
            XfsEntry leafAttributesEntry = DataTestUtils.getDescendantData(type.create(device, true), "short-form-dir", "empty-file1.txt");
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
            XfsEntry leafAttributesEntry = DataTestUtils.getDescendantData(type.create(device, true), "btree_dir");
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

    @Ignore("test data not in project, it is 10.5GB, too large to put in code.")
    @Test
    public void testSocket() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v4/ubuntu_xfs_v4.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsEntry entry = DataTestUtils.getDescendantData(new XfsFileSystemType().create(device, true), "tmp", "keyring-eoplNH", "ssh");

            assertThat(entry.getName(), is ("ssh"));
            assertThat(entry.getId(), is("2084921-4"));
            assertThat(FileMode.SOCKET.isSet(entry.getINode().getMode()), is(true));
        } finally {
            testFile.delete();
        }
    }
}
