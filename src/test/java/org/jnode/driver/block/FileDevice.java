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
        // Honour the buffer's position and limit. Reading dest.array() from index 0 ignores both, so a caller
        // filling part of a larger array - which NTFSVolume.readClusters does for every data run after the
        // first - had the previous run's data overwritten and got back more bytes than it asked for.
        int length = dest.remaining();
        randomAccessFile.seek(devOffset);

        if (dest.hasArray())
        {
            randomAccessFile.readFully(dest.array(), dest.arrayOffset() + dest.position(), length);
            dest.position(dest.position() + length);
        }
        else
        {
            byte[] buffer = new byte[length];
            randomAccessFile.readFully(buffer);
            dest.put(buffer);
        }
    }

    @Override
    public void write(long devOffset, ByteBuffer src) throws IOException
    {
        int length = src.remaining();
        randomAccessFile.seek(devOffset);

        if (src.hasArray())
        {
            randomAccessFile.write(src.array(), src.arrayOffset() + src.position(), length);
            src.position(src.position() + length);
        }
        else
        {
            byte[] buffer = new byte[length];
            src.get(buffer);
            randomAccessFile.write(buffer);
        }
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
