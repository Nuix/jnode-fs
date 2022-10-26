package org.jnode.fs.xfs;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.jnode.driver.block.FileDevice;
import org.jnode.fs.DataStructureAsserts;
import org.jnode.fs.FSAttribute;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.service.FileSystemService;
import org.jnode.fs.xfs.inode.Format;
import org.jnode.fs.xfs.inode.INode;
import org.jnode.fs.xfs.inode.INodeV3;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jnode.fs.util.FSUtils.*;

public class XfsFileSystemTest {

    private static FileSystemService fss;
    private static File baseTestFile;
    private static File extendedAttrTestFile;
    private static File dirTypesTestFile;

    @BeforeClass
    public static void setUp() throws IOException {
        // create file system service.
        fss = FileSystemTestUtils.createFSService(XfsFileSystemType.class.getName());
        baseTestFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/test-xfs-1.img");
        extendedAttrTestFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/extended_attr.img");
        // dirTypesTestFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/dir_types.img");
    }

    @AfterClass
    public static void cleanup() {
        baseTestFile.delete();
        extendedAttrTestFile.delete();
        // dirTypesTestFile.delete();
    }

    @Test
    public void testImage1() throws Exception {
        try (FileDevice device = new FileDevice(baseTestFile, "r")) {
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
        }
    }

    @Test
    public void testXfsMetaData() throws Exception {
        // Arrange
        String expectedStructure =
                "  /; \n" +
                        "    atime : 2021-11-17T06:50:04.416+0000; ctime : 2021-11-17T06:47:39.355+0000; mtime : 2021-11-17T06:48:33.735+0000\n" +
                        "    owner : 0; group : 0; size : 57; mode : 777; \n" +
                        "    folder1; \n" +
                        "        atime : 2021-11-17T06:50:07.494+0000; ctime : 2021-11-17T06:48:14.193+0000; mtime : 2021-11-17T06:50:07.430+0000\n" +
                        "        owner : 1000; group : 1000; size : 30; mode : 775; \n" +
                        "      this_is_fine.jpg; \n" +
                        "            atime : 2021-11-17T06:50:07.430+0000; ctime : 2021-11-17T06:50:07.430+0000; mtime : 2019-05-19T23:45:52.237+0000\n" +
                        "            owner : 1000; group : 1000; size : 53072; mode : 744; \n" +
                        "    folder 2; \n" +
                        "        atime : 2021-11-17T06:52:07.433+0000; ctime : 2021-11-17T06:48:17.294+0000; mtime : 2021-11-17T06:52:07.421+0000\n" +
                        "        owner : 1000; group : 1000; size : 21; mode : 775; \n" +
                        "      xfs.zip; \n" +
                        "            atime : 2021-11-17T06:52:07.421+0000; ctime : 2021-11-17T06:52:07.421+0000; mtime : 2021-11-17T06:52:03.068+0000\n" +
                        "            owner : 1000; group : 1000; size : 20103; mode : 744; \n" +
                        "    testfile.txt; \n" +
                        "        atime : 2021-11-17T06:48:33.735+0000; ctime : 2021-11-17T06:48:33.735+0000; mtime : 2021-11-17T06:48:33.735+0000\n" +
                        "        owner : 1000; group : 1000; size : 20; mode : 664; \n";

        try (FileDevice device = new FileDevice(baseTestFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            XfsFileSystem fs = type.create(device, true);

            XfsEntry entry = fs.getRootEntry();
            StringBuilder actual = new StringBuilder(expectedStructure.length());

            buildXfsMetaDataStructure(entry, actual, "  ");

            assertThat(actual.toString(), is(expectedStructure));

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
    private static void buildXfsMetaDataStructure(XfsEntry entry, StringBuilder actual, String indent) throws IOException {
        actual.append(indent);
        actual.append(entry.getName());
        actual.append("; \n");

        if (entry.isDirectory()) {
            getXfsMetadata(entry, actual, indent);
        }
        if (entry.isFile()) {
            getXfsMetadata(entry, actual, indent);
        } else {
            FSDirectory directory = entry.getDirectory();

            Iterator<? extends FSEntry> iterator = directory.iterator();

            while (iterator.hasNext()) {
                FSEntry child = iterator.next();

                if (".".equals(child.getName()) || "..".equals(child.getName())) {
                    continue;
                }

                buildXfsMetaDataStructure((XfsEntry) child, actual, indent + "  ");
            }
        }
    }

    /**
     * Get the metadata.
     *
     * @param entry  the entry to process.
     * @param actual the string to append to.
     * @param indent the indent level.
     */
    private static void getXfsMetadata(XfsEntry entry, StringBuilder actual, String indent) {
        actual.append(indent).append(indent);
        actual.append("atime : ").append(getDate(entry.getLastAccessed())).append("; ");
        if (entry.getINode() instanceof INodeV3) {
            INodeV3 v3 = (INodeV3) entry.getINode();
            actual.append("ctime : ").append(getDate(v3.getCreated())).append("; ");
        }
        actual.append("mtime : ").append(getDate(entry.getLastChanged())).append("\n");
        actual.append(indent).append(indent);
        actual.append("owner : ").append(entry.getINode().getUid()).append("; ");
        actual.append("group : ").append(entry.getINode().getGid()).append("; ");
        actual.append("size : ").append(entry.getINode().getSize()).append("; ");
        String mode = Integer.toOctalString(entry.getINode().getMode());
        actual.append("mode : ").append(mode.substring(mode.length() - 3)).append("; \n");
    }

    /**
     * Convert epoch to human-readable date.
     *
     * @param date the epoch value.
     */
    private static String getDate(long date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        return simpleDateFormat.format(new java.util.Date(date));
    }

    @Test
    public void testShortFormAttribute() throws Exception {
        try (FileDevice device = new FileDevice(extendedAttrTestFile, "r")) {
            XfsEntry entry = DataTestUtils.getDescendantData(new XfsFileSystemType().create(device, true), "short-form-attr.txt");
            assertThat(entry.getINode().getAttributesFormat(), is(1));// Short form attribute format
            List<FSAttribute> attributes = entry.getAttributes();
            assertThat(attributes, hasSize(1));
            FSAttribute attribute = attributes.get(0);
            assertThat(attribute.getName(), is("selinux"));
            String stringValue = toNormalizedString(attribute.getValue());
            assertThat(stringValue, is("unconfined_u:object_r:unlabeled_t:s0"));
        }
    }

    @Ignore("test data not in project, it is 17GB, too large to put in code.")
    @Test
    public void testBtreeWithHierarchy() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/v5/centos-xfs.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsEntry entry = DataTestUtils.getDescendantData(new XfsFileSystemType().create(device, true), "usr", "lib64");

            // It's the Btree node with complex folder / file hierarchy.
            XfsDirectory directory = new XfsDirectory(entry);
            assertThat(directory.getEntry().getINode().getFormat(), is(Format.BTREE));

            // It seems that all items in the same folder have the same attribute value of name "selinux".
            FSEntry file = directory.getEntry("librt.so.1"); // a file
            assertThat(toNormalizedString(file.getAttributes().get(0).getValue()), is("system_u:object_r:lib_t:s0"));

            FSEntry folder = directory.getEntry("dri"); // a folder
            assertThat(toNormalizedString(folder.getAttributes().get(0).getValue()), is("system_u:object_r:lib_t:s0"));

            FSEntry file1InFolder = new XfsDirectory((XfsEntry) folder).getEntry("i965_dri.so");
            assertThat(toNormalizedString(file1InFolder.getAttributes().get(0).getValue()), is("system_u:object_r:textrel_shlib_t:s0"));

            FSEntry file2InFolder = new XfsDirectory((XfsEntry) folder).getEntry("r600_dri.so");
            assertThat(toNormalizedString(file2InFolder.getAttributes().get(0).getValue()), is("system_u:object_r:textrel_shlib_t:s0"));
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void testLeafAttributes() throws Exception {
        try (FileDevice device = new FileDevice(extendedAttrTestFile, "r")) {
            XfsEntry entry = DataTestUtils.getDescendantData(new XfsFileSystemType().create(device, true), "leaf-attr.txt");

            INode leafAttributeINode = entry.getINode();

            // Only v3 has this property.
            assertThat(getDate(((INodeV3) leafAttributeINode).getCreated()), is("2022-01-28T16:27:16.646+0000"));

            // leaf/node form attribute format
            assertThat(leafAttributeINode.getAttributesFormat(), is(2));

            // leaf only has 1 extent
            assertThat(leafAttributeINode.getAttributeExtentCount(), is(1));

            List<FSAttribute> attributes = entry.getAttributes();
            assertThat(attributes, hasSize(31));
            assertThat(attributes, everyItem(getSampleAttributeMatcher()));
        }
    }

    @Test
    public void testNodeAttributes() throws Exception {
        try (FileDevice device = new FileDevice(extendedAttrTestFile, "r")) {
            XfsEntry entry = DataTestUtils.getDescendantData(new XfsFileSystemType().create(device, true), "node-attr.txt");
            INode nodeAttributeINode = entry.getINode();
            // leaf/node form attribute format
            assertThat(nodeAttributeINode.getAttributesFormat(), is(2));
            // node has more than 1 extent
            assertThat(nodeAttributeINode.getAttributeExtentCount(), greaterThan(1));

            List<FSAttribute> attributes = entry.getAttributes();
            assertThat(attributes, hasSize(201));
            assertThat(attributes, everyItem(getSampleAttributeMatcher()));
        }
    }

    @Test
    public void testSparseFiles() throws Exception {
        try (FileDevice device = new FileDevice(extendedAttrTestFile, "r")) {
            XfsFileSystem fs = new XfsFileSystemType().create(device, true);
            XfsEntry entry = DataTestUtils.getDescendantData(fs, "sparse.dat");
            long blockSize = fs.getSuperblock().getBlockSize();
            INode sparseFileINode = entry.getINode();
            long fileSize = sparseFileINode.getSize();
            for (int offset = 0; offset < fileSize; offset += blockSize) {
                int bufferSize = (int) Math.min(blockSize, fileSize - offset);
                ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
                entry.read(offset, buffer);
                if (blockSize == bufferSize) {
                    // in case of sparse data the buffer should be left untouched
                    assertThat(buffer.position(), is(0));
                } else {
                    String stringData = toNormalizedString(buffer.array());
                    assertThat(stringData, is("Just a little bit of data right at the end...\n"));
                }

            }
        }
    }

    private Matcher<FSAttribute> getSampleAttributeMatcher() {
        return new BaseMatcher<FSAttribute>() {
            private final Pattern namePattern = Pattern.compile("sample-attr([0-9]+)");
            private final Pattern valuePattern = Pattern.compile("sample-value([0-9]+)");

            @Override
            public boolean matches(Object o) {
                if (o instanceof FSAttribute) {
                    FSAttribute attr = (FSAttribute) o;
                    String name = attr.getName();
                    String stringValue = toNormalizedString(attr.getValue());
                    if (name.equals("selinux")) {
                        return stringValue.equals("unconfined_u:object_r:unlabeled_t:s0");
                    }
                    java.util.regex.Matcher nameMatcher = namePattern.matcher(name);
                    java.util.regex.Matcher valueMatcher = valuePattern.matcher(stringValue);
                    if (nameMatcher.matches() && valueMatcher.matches()) {
                        return nameMatcher.group(1).equals(valueMatcher.group(1));
                    }
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("Does not conform to sample attributeMatcher");
            }
        };
    }

    @Ignore("Test data not in project")
    @Test
    public void testCentos() throws Exception {

        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/xfs/centos-xfs.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            XfsFileSystemType type = new XfsFileSystemType();
            XfsFileSystem fs = type.create(device, true);
            XfsEntry entry = fs.getRootEntry();
            StringBuilder actual = new StringBuilder(1024);
            buildXfsDirStructure(entry, actual, "  ");
            // TODO: an assertion
        } finally {
            testFile.delete();
        }
    }

    @Ignore("test data not in project")
    @Test
    public void testShortFormDir() throws Exception {

        try (FileDevice device = new FileDevice(dirTypesTestFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);
            List<? extends FSEntry> entries = getInodeEntries(fs, 131);
            assertThat(entries.size(), Matchers.is(4));
        }
    }

    @Ignore("test data not in project")
    @Test
    public void testBlockDir() throws Exception {

        try (FileDevice device = new FileDevice(dirTypesTestFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);
            List<? extends FSEntry> entries = getInodeEntries(fs, 1048704);
            assertThat(entries.size(), Matchers.is(22));
        }
    }

    @Ignore("test data not in project")
    @Test
    public void testLeafDir() throws Exception {

        try (FileDevice device = new FileDevice(dirTypesTestFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);
            List<? extends FSEntry> entries = getInodeEntries(fs, 2117760);
            assertThat(entries.size(), Matchers.is(202));
        }
    }

    @Ignore("test data not in project")
    @Test
    public void testNodeDir() throws Exception {

        try (FileDevice device = new FileDevice(dirTypesTestFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);
            List<? extends FSEntry> entries = getInodeEntries(fs, 3145856);
            assertThat(entries.size(), Matchers.is(1_002));
        }
    }

    @Ignore("test data not in project")
    @Test
    public void testSingleLevelBTreeDir() throws Exception {

        try (FileDevice device = new FileDevice(dirTypesTestFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);
            List<? extends FSEntry> entries = getInodeEntries(fs, 132);
            assertThat(entries.size(), Matchers.is(10_002));
        }
    }

    @Ignore("test data not in project")
    @Test
    public void testMultiLevelBTreeDir() throws Exception {

        try (FileDevice device = new FileDevice(dirTypesTestFile, "r")) {
            XfsFileSystemType type = fss.getFileSystemType(XfsFileSystemType.ID);
            XfsFileSystem fs = type.create(device, true);
            List<? extends FSEntry> entries = getInodeEntries(fs, 1048705);
            assertThat(entries.size(), Matchers.is(1_000_002));
        }
    }

    private List<? extends FSEntry> getInodeEntries(XfsFileSystem fs, long inode) throws IOException {
        INode iNode = fs.getINode(inode);
        XfsEntry entry = new XfsEntry(iNode, "", 0, fs, null);
        return iteratorToList(entry.getDirectory().iterator());
    }

    public <T> List<T> iteratorToList(Iterator<T> iterator) {
        Spliterator<T> spliterator = Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED);
        return StreamSupport.stream(spliterator, false).collect(Collectors.toList());
    }


    /**
     * Build the directory string.
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
