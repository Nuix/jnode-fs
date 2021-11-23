package org.jnode.fs.service;

import java.util.Map;

import com.google.common.collect.ImmutableMap;
import org.jnode.fs.FileSystemException;
import org.jnode.fs.FileSystemType;
import org.jnode.fs.apfs.ApfsFileSystemType;
import org.jnode.fs.ext2.Ext2FileSystemType;
import org.jnode.fs.hfsplus.HfsPlusFileSystemType;
import org.jnode.fs.ntfs.NTFSFileSystemType;

public class FileSystemService
{
    private static final Map<Class<?>, FileSystemType<?>> typeMap = ImmutableMap.<Class<?>, FileSystemType<?>>builder()
                                                                                .put(Ext2FileSystemType.class, new Ext2FileSystemType())
                                                                                .put(ApfsFileSystemType.class, new ApfsFileSystemType())
                                                                                .put(HfsPlusFileSystemType.class, new HfsPlusFileSystemType())
                                                                                .put(NTFSFileSystemType.class, new NTFSFileSystemType())
                                                                                .build();

    public <T extends FileSystemType<?>> T getFileSystemType(Class<T> name)
        throws FileSystemException
    {
        FileSystemType<?> type = typeMap.get(name);
        if (type != null)
        {
            return (T) type;
        }
        else
        {
            throw new FileSystemException("Unhandled Filesystem type");
        }
    }
}
