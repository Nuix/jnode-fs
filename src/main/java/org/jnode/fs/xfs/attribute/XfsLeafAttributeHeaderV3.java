package org.jnode.fs.xfs.attribute;

import java.io.IOException;

import org.jnode.fs.xfs.common.DirectoryOrAttributeBlockInfoV3;

/**
 * V3 version of {@link XfsLeafAttributeHeader},
 * the difference is that
 * {@link XfsLeafAttributeHeader} uses xfs_da_blkinfo_t at the beginning and there is no pad at the end,
 * whereas this class uses xfs_da3_blkinfo_t at the beginning and there is a pad2 at the end.
 * <pre>
 *     typedef struct xfs_attr3_leaf_hdr {
 *         xfs_da3_blkinfo_t info;
 *         __be16 count;
 *         __be16 usedbytes;
 *         __be16 firstused;
 *         __u8 holes;
 *         __u8 pad1;
 *         xfs_attr_leaf_map_t freemap[3];
 *         __be32 pad2;
 *     } xfs_attr3_leaf_hdr_t;
 * </pre>
 */
public class XfsLeafAttributeHeaderV3 extends XfsLeafAttributeHeader {

    /**
     * The size of this structure.
     */
    public static final int SIZE = 0x50;

    public XfsLeafAttributeHeaderV3(byte[] data, int offset) throws IOException {
        super(data, offset);

        //skip __be32 pad2;
        skipBytes(4);
    }

    @Override
    protected void readBlockInfo() throws IOException {
        blockInfo = new DirectoryOrAttributeBlockInfoV3(getData(), getOffset());
        skipBytes(blockInfo.getSize());
    }

    @Override
    public int getSize() {
        return XfsLeafAttributeHeaderV3.SIZE;
    }
}
