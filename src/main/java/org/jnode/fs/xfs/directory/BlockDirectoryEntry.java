package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

/**
 * Classname: BlockDirectoryEntry
 *
 * Date: Jan/07/2022
 *
 */
public class BlockDirectoryEntry extends XfsObject {

    /**
     * Length of the name, in bytes
     */
    private final long nameSize;

    /**
     * Magic number signifying that this is an unused entry. Must be 0xFFFF.
     */
    private final boolean isFreeTag;

    /**
     * The inode number that this entry points to.
     */
    private final long iNodeNumber;

    /**
     * The name associated with this entry.
     */
    private final String name;

    /**
     * The file system instance.
     */
    private XfsFileSystem fileSystem;

    public BlockDirectoryEntry(byte [] data, long offset, XfsFileSystem fileSystem) throws IOException {
        super(data , (int) offset);
        this.fileSystem = fileSystem;
        isFreeTag = read(0,2) == 0xFFFF;
        if (!isFreeTag()) {
            nameSize = read(8, 1);
            iNodeNumber = read(0, 8);
            byte [] buffer = new byte[(int)nameSize];
            System.arraycopy(data, (int)offset + 9 , buffer, 0 , (int) nameSize);
            name = new String(buffer, StandardCharsets.US_ASCII);
        } else {
            nameSize = 0;
            iNodeNumber=0;
            name = "";
        }
    }

    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    public long getSignature() throws IOException {
        return 0L;
    }

    public long getINodeNumber() throws IOException {
        return iNodeNumber;
    }

    public long getNameSize() throws IOException {
        return nameSize;
    }

    public String getName() throws IOException {
        return name;
    }

    public long getOffsetFromBlock() throws IOException {
        return read(getNameSize() + 9, 2);
    }

    public boolean isFreeTag() {
        return isFreeTag;
    }

    public long getOffsetSize() throws IOException {
        if (!isFreeTag) {
            final long l = 12 + nameSize;
            final double v = l / 8.0;
            return (long) Math.ceil(v) * 8;
        } else {
            return read(2,2);
        }
    }

    @Override
    public String toString() {
        return "BlockDirectoryEntry{" +
                "name='" + name + '\'' +
                ", iNodeNumber=" + iNodeNumber +
                ", isFreeTag=" + isFreeTag +
                '}';
    }
}

