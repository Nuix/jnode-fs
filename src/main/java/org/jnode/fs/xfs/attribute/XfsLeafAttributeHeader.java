package org.jnode.fs.xfs.attribute;

import java.io.IOException;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.common.DirectoryOrAttributeBlockInfo;

/**
 * <pre>
 *     typedef struct xfs_attr_leaf_hdr {
 *         xfs_da_blkinfo_t info;
 *         __be16 count;
 *         __be16 usedbytes;
 *         __be16 firstused;
 *         __u8 holes;
 *         __u8 pad1;
 *         xfs_attr_leaf_map_t freemap[3];
 *     } xfs_attr_leaf_hdr_t;
 * </pre>
 */
@Getter
public class XfsLeafAttributeHeader extends XfsObject {
    /**
     * The size of this structure.
     */
    public static final int SIZE = 0x20;

    /**
     * Directory/attribute block header.
     */
    DirectoryOrAttributeBlockInfo blockInfo;

    /**
     * Number of entries.
     */
    private final int entryCount;

    /**
     * Number of bytes used in the leaf block.
     */
    private final int usedBytes;

    /**
     * Block offset of the first entry in use, in bytes.
     */
    private final int firstUsed;

    /**
     * Set to 1 if block compaction is necessary.
     */
    private final int holes;

    /**
     * The hash/index elements in the entries[] array are packed from the top of the block. Name/values grow from the
     * bottom but are not packed. The freemap contains run-length-encoded entries for the free bytes after the entries[]
     * array, but only the three largest runs are stored (smaller runs are dropped). When the freemap doesn’t show enough
     * space for an allocation, the name/value area is compacted and allocation is tried again. If there still isn’t enough
     * space, then the block is split. The name/value structures (both local and remote versions) must be 32-bit aligned.
     */
    private final XfsLeafAttributeMap[] freeMap;

    public XfsLeafAttributeHeader(byte[] data, int offset) throws IOException {
        super(data, offset);

        readBlockInfo();

        entryCount = readUInt16();
        usedBytes = readUInt16();
        firstUsed = readUInt16();
        holes = readUInt8();

        // Padding to maintain alignment to 64-bit boundaries.
        // skip __u8 pad1.
        skipBytes(1);

        // xfs_attr_leaf_map_t freemap[3]
        freeMap = new XfsLeafAttributeMap[3];
        for (int i = 0; i < 3; i++) {
            freeMap[i] = new XfsLeafAttributeMap(data, getOffset());
            skipBytes(XfsLeafAttributeMap.SIZE);
        }
    }

    protected void readBlockInfo() throws IOException {
        blockInfo = new DirectoryOrAttributeBlockInfo(getData(), getOffset());
        skipBytes(blockInfo.getSize());
    }

    /**
     * Gets the size of this structure.
     *
     * @return the size of this structure.
     */
    public int getSize() {
        return XfsLeafAttributeHeader.SIZE;
    }
}
