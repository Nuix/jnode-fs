package org.jnode.fs;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;

import org.jnode.fs.service.FileSystemService;

public class FileSystemTestUtils
{
    /**
     * Gets a copy of the test file from the resources folder. If the test file is gzipped,
     * the decompressed version of the test file is returned. It is up to the caller to
     * delete the file when complete.
     *
     * @param path the path to the test file.
     * @return a copy of the test file.
     * @throws IOException if any unexpected file operations occur.
     */
    public static File getTestFile(String path) throws IOException
    {
        File tempFile = File.createTempFile("testFile", ".tmp");
        File resourceFile = new File("src/test/resources/", path).getAbsoluteFile();

        File gzipFile = new File(resourceFile.getParent(), resourceFile.getName() + ".gz");
        try (InputStream in = new GZIPInputStream(new FileInputStream(gzipFile)))
        {
            try (OutputStream out = new FileOutputStream(tempFile))
            {
                byte[] buffer = new byte[32768];
                int length;
                while ((length = in.read(buffer)) > 0)
                {
                    out.write(buffer, 0, length);
                }
                out.flush();
            }

            return tempFile;
        }
        catch (FileNotFoundException ignore)
        {
            tempFile.delete();
        }

        try
        {
            // test file is not gzipped. Just create a copy and return.
            Files.copy(resourceFile.toPath(), tempFile.toPath());

            return tempFile;
        }
        catch (IOException e)
        {
            tempFile.delete();
            throw e;
        }
    }

    public static FileSystemService createFSService(String className)
    {
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
            bytes[i] = (byte)(Integer.parseInt(parts[i], 16) & 0xff);
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
}