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
        registerAPI(FSBlockDeviceAPI.class, this);
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
        byte[] buffer = dest.array();
        randomAccessFile.seek(devOffset);
        randomAccessFile.read(buffer, 0, buffer.length);
    }

    @Override
    public void write(long devOffset, ByteBuffer src) throws IOException
    {
        randomAccessFile.seek(devOffset);
        randomAccessFile.write(src.array());
    }

    @Override
    public void flush()
    {
    }

    @Override
    public int getSectorSize()
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
