package org.jnode.fs.xfs.directory;

import java.io.IOException;

import lombok.Getter;
import org.jnode.fs.xfs.common.DirectoryOrAttributeBlockInfoV3;

/**
 * A XFS V3 dir leaf header.
 * <pre>
 *     struct xfs_dir3_leaf_hdr {
 *         struct xfs_da3_blkinfo info;
 *         __uint16_t count;
 *         __uint16_t stale;
 *         __be32 pad;
 *     };
 * </pre>
 */
@Getter
public class LeafHeaderV3 extends LeafHeaderV2 {
    /**
     * The magic signature of a leaf directory entry v5.
     */
    private static final long LEAF_DIR_MAGIC_V5 = 0x3DF1;

    /**
     * The magic signature of the node directory entry v5.
     */
    private static final long NODE_DIR_MAGIC_V5 = 0x3dff;

    /**
     * Creates a Leaf block information entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public LeafHeaderV3(byte[] data, long offset) throws IOException {
        super(data, (int) offset);
    }

    public void readBlockInfo() throws IOException {
        blockInfo = new DirectoryOrAttributeBlockInfoV3(getData(), getOffset());
        skipBytes(DirectoryOrAttributeBlockInfoV3.SIZE);
    }

    /**
     * Gets the string information of the leaf header.
     *
     * @return a string
     */
    @Override
    public String toString() {
        DirectoryOrAttributeBlockInfoV3 blockInfoV3 = (DirectoryOrAttributeBlockInfoV3) blockInfo;
        return "LeafInfo{forward=" + blockInfoV3.getForward() +
                ", backward=" + blockInfoV3.getBackward() +
                ", magic=" + blockInfoV3.getMagic() +
                ", crc=0x" + Long.toHexString(blockInfoV3.getCrc()) +
                ", blockNumber=" + blockInfoV3.getBlockNumber() +
                ", logSequenceNumber=0x" + Long.toHexString(blockInfoV3.getLogSequenceNumber()) +
                ", uuid='" + blockInfoV3.getUuid() + '\'' +
                ", owner=" + blockInfoV3.getOwner() +
                '}';
    }
}
