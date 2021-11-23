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

    public static FileSystemService createFSService(String className) throws Exception
    {
        // TODO: stub function
        return new FileSystemService();
    }
}