package org.jnode.fs.xfs.attribute;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

/**
 * <pre>
 *     typedef struct xfs_attr_leafblock {
 *         xfs_attr_leaf_hdr_t hdr;
 *         xfs_attr_leaf_entry_t entries[1];
 *         xfs_attr_leaf_name_local_t namelist;
 *         xfs_attr_leaf_name_remote_t valuelist;
 *     } xfs_attr_leafblock_t;
 * </pre>
 *
 * and
 *
 * <pre>
 *     typedef struct xfs_attr3_leafblock {
 *         xfs_attr3_leaf_hdr_t hdr;
 *         xfs_attr_leaf_entry_t entries[1];
 *         xfs_attr_leaf_name_local_t namelist;
 *         xfs_attr_leaf_name_remote_t valuelist;
 *     } xfs_attr3_leafblock_t;
 * </pre>
 */
@Getter
public class XfsLeafAttributeBlock extends XfsObject {
    private final XfsLeafAttributeHeader header;

    public XfsLeafAttributeBlock(byte[] data, int offset, boolean isV5) throws IOException {
        super(data, offset);

        header = isV5 ?
                new XfsLeafAttributeHeaderV3(data, offset) :
                new XfsLeafAttributeHeader(data, offset);

        skipBytes(header.getSize());
    }

    /**
     * Gets the leaf attributes.
     *
     * @return the leaf attributes.
     */
    public List<XfsLeafAttributeNameLocal> getAttributes() {
        int originalOffset = getOffset() - header.getSize();

        int leafEntryOffset = getOffset();
        List<XfsLeafAttributeNameLocal> attributes = new ArrayList<>(header.getEntryCount());
        for (int i = 0; i < header.getEntryCount(); i++) {
            //get an entry from "xfs_attr_leaf_entry_t entries[1]"
            XfsAttributeLeafEntry leafEntry = new XfsAttributeLeafEntry(getData(), leafEntryOffset);
            leafEntryOffset += XfsAttributeLeafEntry.PACKED_LENGTH;

            int attributeBlockOffset = originalOffset + leafEntry.getNameIndex();

            // get the actual attribute in the "xfs_attr_leaf_name_local_t namelist"
            XfsLeafAttributeNameLocal attribute = new XfsLeafAttributeNameLocal(getData(), attributeBlockOffset);

            // TODO, xfs_attr_leaf_name_remote needs to be supported as well.
            //  But how can we know if the attribute is local or remote?
            //  leafEntry.getAttributeFlags() doesn't contain XFS_ATTR_LOCAL?
            //  We don't have any data that flags doesn't contain local.. May need to get some data first.
            //  see XfsLeafAttributeNameRemote

            attributes.add(attribute);
        }
        return attributes;
    }
}
