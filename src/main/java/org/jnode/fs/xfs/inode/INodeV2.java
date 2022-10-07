package org.jnode.fs.xfs.inode;

import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.superblock.AdditionalVersionFlags;

/**
 * An XFS v2 inode ('xfs_dinode_core'). Structure definition in {@link INode}.
 */
public class INodeV2 extends INode {

    INodeV2(long inodeNumber, byte[] data, int offset, XfsFileSystem fs) {
        super(inodeNumber, data, offset, fs);
    }

    @Override
    public long getLinkCount() {
        // Link count stored in di_nlink
        return getUInt32(16);
    }

    /**
     * Gets the project ID for the inode.
     *
     * @return the project ID.
     */
    public int getProjectId() {
        int result = getUInt16(20);
        if (AdditionalVersionFlags.PROJID32BIT.isSet(fs.getSuperblock().getFeature2())) {
            result |= getUInt16(22) << 16;
        }

        return result;
    }
}
