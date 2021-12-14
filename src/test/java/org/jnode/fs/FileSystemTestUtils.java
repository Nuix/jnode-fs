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
     * Create a file .
     *
     * @param fileName Name of the file  to be created.
     * @return void.
     */
    public static File createFile(String fileName) throws IOException {

        File f = new File("src/test/resources/"+fileName).getAbsoluteFile();
        if(f.createNewFile()) {
            System.out.println("File created: " + f.getAbsoluteFile());
        }
        return f;

    }

    /**
     * Create a directory.
     *
     * @param directoryName Name of the directory to be created.
     * @return void.
     */
    public static void createDirectory( String directoryName){
        if (directoryName.isEmpty()) {
            return;
        }
        if (directoryName.indexOf('/') ==0 ) {
            directoryName = directoryName.substring(1,directoryName.length());
        }
        if (! new File("src/test/resources/"+directoryName).getAbsoluteFile().mkdirs()) {
            return;
        }
        System.out.println("Directory created: " + "../src/test/resources/"+directoryName );
    }

    /**
     * Test if the file or directory exists
     *
     * @param item  Directory Name or File Name to test if it exists.
     * @return boolean.
     */
    public static boolean testIfExists(String item){
        boolean exists = false;
        File f = new File("src/test/resources/"+item).getAbsoluteFile();
        if(f.exists()) {
            exists = true;
        }
        return exists;
    }

    /**
     * Delete the file or directory if they exists
     *
     * @param file  Directory Name or File Name to delete.
     * @return void.
     */
    public static void deleteDirectory( File file ) throws IOException {

        if (file.isDirectory()) {
            File[] entries = file.listFiles();
            if (entries != null) {
                for (File entry : entries) {
                    deleteDirectory(entry);
                }
            }
        }
        if(!file.exists()) {
            return;
        }
        if (!file.delete()) {
            throw new IOException("Failed to delete " + file);
        }
    }

}