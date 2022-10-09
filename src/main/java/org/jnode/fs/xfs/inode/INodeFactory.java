package org.jnode.fs.xfs.inode;

import java.io.IOException;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;

/**
 * Creates inodes based on the version value read from the data.
 */
public class INodeFactory {
    /**
     * Prevent instantiation.
     */
    private INodeFactory() {
        // prevent instantiation
    }

    /**
     * Creates an appropriate inode instance, depending on the version value.
     *
     * @param inodeNumber the inode number.
     * @param data        the inode data.
     * @param offset      the offset to this inode in the data.
     * @param fs          the {@link XfsFileSystem}.
     * @return an inode instance.
     * @throws IOException if there is an error creating the inode.
     */
    public static INode create(long inodeNumber, byte[] data, int offset, XfsFileSystem fs) throws IOException {
        XfsObject obj = new XfsObject(data, offset);

        int signature = obj.getUInt16(0);
        if (signature != INode.MAGIC) {
            throw new IOException("Wrong magic number for XFS INODE: " + obj.getAsciiSignature(signature));
        }

        int version = obj.getUInt8(4);
        switch (version) {
            case 1:
                return new INode(inodeNumber, data, offset, fs);
            case 2:
                return new INodeV2(inodeNumber, data, offset, fs);
            default:
                return new INodeV3(inodeNumber, data, offset, fs);
        }
    }
}
