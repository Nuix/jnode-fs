package org.jnode.fs.xfs.directory;

import java.io.IOException;

import lombok.Getter;
import org.jnode.fs.xfs.common.DirectoryOrAttributeBlockInfo;

/**
 * A XFS V2 leaf info.
 * <pre>
 *     typedef struct xfs_dir2_leaf_hdr {
 *         xfs_da_blkinfo_t info;
 *         __uint16_t count;
 *         __uint16_t stale;
 *     } xfs_dir2_leaf_hdr_t;
 * </pre>
 */
@Getter
public class LeafHeaderV2 extends LeafHeader {
    DirectoryOrAttributeBlockInfo blockInfo;

    /**
     * Creates a Leaf block information entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public LeafHeaderV2(byte[] data, long offset) throws IOException {
        super(data, (int) offset);
    }

    public void readBlockInfo() throws IOException {
        blockInfo = new DirectoryOrAttributeBlockInfo(getData(), getOffset());
        skipBytes(DirectoryOrAttributeBlockInfo.SIZE);
    }

    /**
     * Gets the string information of the leaf header.
     *
     * @return a string
     */
    @Override
    public String toString() {
        return "LeafInfo{forward=" + blockInfo.getForward() +
                ", backward=" + blockInfo.getBackward() +
                ", magic=" + blockInfo.getMagic() +
                '}';
    }
}
