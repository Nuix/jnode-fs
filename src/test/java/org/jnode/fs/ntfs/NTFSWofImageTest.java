package org.jnode.fs.ntfs;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import org.jnode.driver.block.FileDevice;
import org.jnode.driver.block.TestImageDevice;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FSFile;
import org.jnode.fs.FileSystemTestUtils;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.ReparsePointAttributeRes;
import org.jnode.fs.ntfs.wof.WofCompressedStream;
import org.jnode.fs.ntfs.wof.WofCompressionFormat;
import org.jnode.fs.service.FileSystemService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;

/**
 * Reads files Windows compressed with {@code compact /exe} (WOF, Compact OS) and checks each against the hash
 * Windows read back through WOF.
 *
 * <p>The volumes and their manifests are in the test-data repository under {@code ntfs/generated-wof}, whose
 * {@code SOURCE.md} records how they were built. The {@code .sha256} lists here are generated from the manifests.</p>
 *
 * <ul>
 * <li>{@code ntfs-wof}: all four formats over sizes either side of a cluster and a chunk, compressible and random
 * data, an ADS, hard links, and an LZNT1 file. Files of a cluster or less, and the random one, Windows left
 * uncompressed.</li>
 * <li>{@code ntfs-wof-fragmented}: compressed streams in 276 runs (behind an {@code $ATTRIBUTE_LIST}), 129 runs
 * at descending LCNs and 31 runs.</li>
 * <li>{@code ntfs-wof-large}: two files of 4 GiB + 12,345 bytes, which need 8-byte chunk table entries. The image
 * expands to 1.1 GiB in the temporary directory.</li>
 * </ul>
 */
public class NTFSWofImageTest {

    @Rule
    public ErrorCollector errors = new ErrorCollector();

    @Test
    public void testMainVolume() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/ntfs-wof.raw")) {
            checkVolume(device, "ntfs-wof");
        }
    }

    @Test
    public void testFragmentedVolume() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/ntfs-wof-fragmented.raw")) {
            checkVolume(device, "ntfs-wof-fragmented");
        }
    }

    @Test
    public void testLargeVolume() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/ntfs-wof-large.raw")) {
            checkVolume(device, "ntfs-wof-large");
        }
    }

    /**
     * Past the end a WOF file reads as zeros, as any other non-resident file does.
     */
    @Test
    public void testReadPastEnd() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/ntfs-wof.raw")) {
            NTFSFile file = getFile(createFileSystem(device), "xpress4k/text-4097.txt");
            assertThat(file.getFileRecord().getWofCompressedStream(), notNullValue());

            byte[] last = new byte[1];
            file.read(4096, ByteBuffer.wrap(last));
            byte[] expected = new byte[4096];
            expected[0] = last[0];

            ByteBuffer buffer = ByteBuffer.allocate(4096);
            file.read(4096, buffer);
            assertThat(buffer.array(), is(expected));
        }
    }

    /**
     * A WOF file this reader does not support fails, rather than reading as the zeros of its sparse data attribute.
     */
    @Test
    public void testUnsupportedProviderFails() throws Exception {
        File image = FileSystemTestUtils.getTestFile("org/jnode/fs/ntfs/ntfs-wof.raw");
        byte[] bytes = Files.readAllBytes(image.toPath());
        // IO_REPARSE_TAG_WOF, 16 bytes of data, WOF_EXTERNAL_INFO {version 1, provider 2 (file)}: make it the WIM provider
        byte[] fileProvider = {0x17, 0, 0, (byte) 0x80, 16, 0, 0, 0, 1, 0, 0, 0, 2, 0, 0, 0};
        for (int i = 0; i <= bytes.length - fileProvider.length; i++) {
            if (regionMatches(bytes, i, fileProvider)) {
                bytes[i + 12] = 1;
            }
        }
        Files.write(image.toPath(), bytes);

        try (TestImageDevice device = new TestImageDevice(image)) {
            NTFSFile file = getFile(createFileSystem(device), "xpress4k/text-4097.txt");

            IOException e = assertThrows(IOException.class, () -> file.read(0, ByteBuffer.allocate(16)));
            assertThat(e.getMessage(), containsString("unsupported WOF version 1 provider 1"));
        }
    }

    /**
     * A failed lookup is kept: the file still fails once the reparse point would parse, as it is not looked at again.
     */
    @Test
    public void testFailedLookupIsCached() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/ntfs-wof.raw")) {
            FileRecord record = getFile(createFileSystem(device), "xpress4k/text-4097.txt").getFileRecord();
            ReparsePointAttributeRes reparsePoint =
                (ReparsePointAttributeRes) record.findAttributeByType(NTFSAttribute.Types.REPARSE_POINT);
            int provider = reparsePoint.getOffset() + reparsePoint.getAttributeOffset() + 12;

            reparsePoint.getBuffer()[provider] = 1;
            assertThrows(IOException.class, record::getWofCompressedStream);

            reparsePoint.getBuffer()[provider] = 2;
            IOException e = assertThrows(IOException.class, record::getWofCompressedStream);
            assertThat(e.getMessage(), containsString("provider 1"));
        }
    }

    /**
     * The slack of a WOF file is what follows its compressed stream, as the unnamed data attribute has no clusters.
     */
    @Test
    public void testSlackSpaceFollowsTheCompressedStream() throws Exception {
        try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/ntfs-wof.raw")) {
            NTFSFileSystem fs = createFileSystem(device);

            // A 6723 byte stream in 4 KiB clusters
            assertThat(getFile(fs, "lzx/text-1m-plus-7.txt").getSlackSpace().length, is(4096 - 6723 % 4096));

            // A resident stream, which has no slack of its own
            assertThat(getFile(fs, "xpress4k/text-4097.txt").getSlackSpace().length, is(0));
        }
    }

    private static boolean regionMatches(byte[] bytes, int offset, byte[] pattern) {
        for (int i = 0; i < pattern.length; i++) {
            if (bytes[offset + i] != pattern[i]) {
                return false;
            }
        }
        return true;
    }

    private static NTFSFileSystem createFileSystem(FileDevice device) throws Exception {
        FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
        return fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);
    }

    private static NTFSFile getFile(NTFSFileSystem fs, String path) throws IOException {
        FSEntry entry = fs.getRootEntry();
        for (String name : path.split("/")) {
            entry = entry.getDirectory().getEntry(name);
            if (entry == null) {
                throw new AssertionError(path + " not found");
            }
        }
        return (NTFSFile) entry.getFile();
    }

    private void checkVolume(FileDevice device, String volume) throws Exception {
        FileSystemService fss = FileSystemTestUtils.createFSService(NTFSFileSystemType.class.getName());
        NTFSFileSystem fs = fss.getFileSystemType(NTFSFileSystemType.ID).create(device, true);

        for (Expected expected : readExpected(volume)) {
            try {
                checkFile(fs, expected);
            } catch (Exception e) {
                errors.addError(new AssertionError(expected.path + ": " + e, e));
            }
        }
    }

    private void checkFile(NTFSFileSystem fs, Expected expected) throws Exception {
        String path = expected.path;
        String stream = null;
        int colon = path.indexOf(':');
        if (colon >= 0) {
            stream = path.substring(colon + 1);
            path = path.substring(0, colon);
        }

        FSEntry entry = fs.getRootEntry();
        for (String name : path.split("/")) {
            FSDirectory directory = entry.getDirectory();
            entry = directory.getEntry(name);
            if (entry == null) {
                throw new AssertionError("not found");
            }
        }

        NTFSFile file = (NTFSFile) entry.getFile();
        FSFile content = stream == null ? file : file.getStreams().get(stream);
        errors.checkThat(expected.path + " stream", content, notNullValue());
        if (content == null) {
            return;
        }

        errors.checkThat(expected.path + " length", content.getLength(), is(expected.size));
        errors.checkThat(expected.path + " sha256", sha256(content), is(expected.sha256));

        if (stream == null) {
            WofCompressedStream wof = file.getFileRecord().getWofCompressedStream();
            if (expected.format == null) {
                errors.checkThat(expected.path + " WOF", wof, nullValue());
            } else {
                errors.checkThat(expected.path + " WOF", wof, notNullValue());
                if (wof != null) {
                    errors.checkThat(expected.path + " WOF format", wof.getFormat(), is(expected.format));
                }
                errors.checkThat(expected.path + " streams", file.getStreams(), hasKey(FileRecord.WOF_STREAM_NAME));
            }
        }
    }

    private static String sha256(FSFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        ByteBuffer buffer = ByteBuffer.allocate(1 << 20);
        long length = file.getLength();

        for (long offset = 0; offset < length; offset += buffer.capacity()) {
            buffer.clear();
            buffer.limit((int) Math.min(buffer.capacity(), length - offset));
            file.read(offset, buffer);
            digest.update(buffer.array(), 0, buffer.limit());
        }

        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    private static List<Expected> readExpected(String volume) throws IOException {
        String resource = "/org/jnode/fs/ntfs/" + volume + ".sha256";
        List<Expected> expected = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            NTFSWofImageTest.class.getResourceAsStream(resource), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                String[] fields = line.split(" ", 4);
                expected.add(new Expected(fields[3], Long.parseLong(fields[1]), fields[0],
                    "-".equals(fields[2]) ? null : WofCompressionFormat.valueOf(fields[2])));
            }
        }

        return expected;
    }

    private static class Expected {
        final String path;
        final long size;
        final String sha256;
        final WofCompressionFormat format;

        Expected(String path, long size, String sha256, WofCompressionFormat format) {
            this.path = path;
            this.size = size;
            this.sha256 = sha256;
            this.format = format;
        }
    }
}
