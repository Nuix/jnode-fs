package org.jnode.fs.service;

import org.jnode.fs.FileSystemException;
import org.jnode.fs.FileSystemType;
import org.jnode.fs.ext2.Ext2FileSystemType;

public class FileSystemService
{
    public <T extends FileSystemType<?>> T getFileSystemType(Class<T> name)
        throws FileSystemException
    {
        // TODO: stub function. Currently only called from Ext4FileSystemTest.
        return (T) new Ext2FileSystemType();
    }
}
