package org.jnode.driver.block;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import org.jnode.driver.Device;
import org.jnode.partitions.PartitionTableEntry;

public class FileDevice extends Device implements FSBlockDeviceAPI, Closeable
{
    private final RandomAccessFile randomAccessFile;

    public FileDevice(File backingFile, String mode) throws IOException
    {
        randomAccessFile = new RandomAccessFile(backingFile, mode);
    }

    @Override
    public void close()
    {
        try
        {
            randomAccessFile.close();
        }
        catch (IOException ignore)
        {
        }
    }

    @Override
    public long getLength() throws IOException
    {
        return randomAccessFile.length();
    }

    @Override
    public void read(long devOffset, ByteBuffer dest) throws IOException
    {
        // TODO: bounds checking
        byte[] buffer = dest.array();
        randomAccessFile.seek(devOffset);
        randomAccessFile.read(buffer, 0, buffer.length);
    }

    @Override
    public void write(long devOffset, ByteBuffer src)
    {
        throw new UnsupportedOperationException("no support for write");
    }

    @Override
    public void flush() throws IOException
    {

    }

    @Override
    public int getSectorSize() throws IOException
    {
        // value copied over from jnode. Not sure if this will need to be updated.
        return 512;
    }

    @Override
    public PartitionTableEntry getPartitionTableEntry()
    {
        return null;
    }
}
