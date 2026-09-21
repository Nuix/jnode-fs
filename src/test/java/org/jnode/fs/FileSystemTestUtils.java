package org.jnode.fs;

import java.io.*;
import java.nio.file.Files;
import org.jnode.driver.block.TestImageDevice;
import java.nio.file.Paths;
import java.util.zip.GZIPInputStream;
import javax.annotation.Nonnull;
import org.jnode.fs.service.FileSystemService;

public class FileSystemTestUtils {
    /**
     * Gets a copy of the test file from the resources folder. If the test file is gzipped,
     * the decompressed version of the test file is returned. It is up to the caller to
     * delete the file when complete - prefer {@link #openImage(String)}, which ties the
     * copy's life to the device and cleans up on close.
     *
     * @param path the path to the test file.
     * @return a copy of the test file.
     * @throws IOException if any unexpected file operations occur.
     */
    public static File getTestFile(String path) throws IOException {
        File tempFile = File.createTempFile("testFile", ".tmp");
        File resourceFile = new File("src/test/resources/", path).getAbsoluteFile();

        File gzipFile = new File(resourceFile.getParent(), resourceFile.getName() + ".gz");
        try (InputStream in = new GZIPInputStream(new FileInputStream(gzipFile))) {
            try (OutputStream out = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[32768];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
                out.flush();
            }

            return tempFile;
        } catch (FileNotFoundException ignore) {
            tempFile.delete();
        }

        try {
            // test file is not gzipped. Just create a copy and return.
            Files.copy(resourceFile.toPath(), tempFile.toPath());

            return tempFile;
        } catch (IOException e) {
            tempFile.delete();
            throw e;
        }
    }

    /**
     * Opens a test image as a block device.
     *
     * <p>The image is decompressed to a temporary file which the device deletes when it is closed, so callers
     * only need a try-with-resources rather than a try/finally that remembers to clean up.</p>
     *
     * @param path the path to the test file, without any .gz suffix.
     * @return the device.
     * @throws IOException if the image cannot be read.
     */
    public static TestImageDevice openImage(String path) throws IOException {
        return new TestImageDevice(getTestFile(path));
    }

    /**
     * Reads a test image straight into memory, with no temporary file.
     *
     * <p>Suitable for the smaller images and for bare structures such as an $MFT, which are read as a byte array
     * rather than opened as a device. Do not use it for the volume images, which run to hundreds of megabytes
     * once expanded.</p>
     *
     * @param path the path to the test file, without any .gz suffix.
     * @return the decompressed contents.
     * @throws IOException if the image cannot be read.
     */
    public static byte[] readImage(String path) throws IOException {
        File resourceFile = new File("src/test/resources/", path).getAbsoluteFile();
        File gzipFile = new File(resourceFile.getParent(), resourceFile.getName() + ".gz");
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try (InputStream in = gzipFile.isFile()
                ? new GZIPInputStream(new FileInputStream(gzipFile))
                : new FileInputStream(resourceFile)) {

            byte[] buffer = new byte[32768];
            int length;
            while ((length = in.read(buffer)) > 0) {
                out.write(buffer, 0, length);
            }
        }

        return out.toByteArray();
    }

    public static FileSystemService createFSService(String className) {
        return new FileSystemService();
    }

    /**
     * Converts a string of hex bytes to a byte array.
     *
     * @param hexBytes the hex bytes to decode, e.g. "00 F8 EC".
     * @return the byte array.
     */
    public static byte[] toByteArray(String hexBytes) {
        String[] parts = hexBytes.replace("\n", " ").trim().split(" ");
        byte[] bytes = new byte[parts.length];

        for (int i = 0; i < parts.length; i++) {
            bytes[i] = (byte) (Integer.parseInt(parts[i], 16) & 0xff);
        }

        return bytes;
    }

    /**
     * Converts a string of signed ints to a byte array.
     *
     * @param signedInts the signed ints, e.g. "-64 0" is equivalent to hex value "C0 00".
     * @return the byte array.
     */
    public static byte[] intsToByteArray(String signedInts) {
        String[] parts = signedInts.replace("\n", " ").trim().split(" ");
        byte[] bytes = new byte[parts.length];

        for (int i = 0; i < parts.length; i++) {
            bytes[i] = Byte.parseByte(parts[i]);
        }

        return bytes;
    }

    /**
     * Reads a file and write content into a byte array.
     *
     * @param filePath the file path.
     * @return the byte array
     * @throws IOException if any error occurs.
     */
    @Nonnull
    public static byte[] readFileToByteArray(String filePath) throws IOException {
        filePath = getTestFile(filePath).getAbsolutePath();
        return Files.readAllBytes(Paths.get(filePath));
    }
}