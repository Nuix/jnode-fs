package org.jnode.fs.xfs.attribute;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.common.DirectoryOrAttributeBlockInfo;
import org.jnode.fs.xfs.extent.DataExtent;
import org.jnode.fs.xfs.inode.INode;

public class XfsLeafOrNodeAttributeReader extends XfsObject {

    private final int extentBlockCount;
    private final List<DataExtent> extents;
    private final int extentCount;
    private final XfsFileSystem fs;

    public XfsLeafOrNodeAttributeReader(byte[] data, int offset, INode iNode, XfsFileSystem fileSystem) {
        super(data, offset);
        extentCount = iNode.getAttributeExtentCount();
        extents = new ArrayList<>(extentCount);
        fs = fileSystem;
        int extentBlockCountTmp = 0;
        for (int i = 0; i < extentCount; i++) {
            DataExtent dataExtent = new DataExtent(getData(), offset);
            extents.add(dataExtent);
            offset += DataExtent.PACKED_LENGTH;
            extentBlockCountTmp += dataExtent.getBlockCount();
        }
        this.extentBlockCount = extentBlockCountTmp;
    }

    public List<FSAttribute> getAttributes() throws IOException {
        if (extentCount < 1) return Collections.emptyList();
        long blockSize = fs.getSuperblock().getBlockSize();
        int attributeCount = 0;
        List<XfsLeafAttributeBlock> attributeBlocks = new ArrayList<>(extentBlockCount);
        for (DataExtent extent : extents) {
            long blockCount = extent.getBlockCount();
            int bufferSize = (int) (blockCount * blockSize);
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
            long blockAbsoluteOffset = extent.getExtentOffset(fs);
            fs.getApi().read(blockAbsoluteOffset, buffer);
            byte[] bytes = buffer.array();
            // NOTE: this code ignores Node type extents as they work as a root btree when searching
            // while contiguous in the same extent definition each block has its own header
            for (int i = 0; i < blockCount; i++) {
                int bufferOffset = (int) (blockSize * i);
                if (DirectoryOrAttributeBlockInfo.isLeafAttribute(bytes, bufferOffset)) {
                    XfsLeafAttributeBlock leafAttributeBlock = new XfsLeafAttributeBlock(bytes, bufferOffset,fs.isV5());
                    attributeBlocks.add(leafAttributeBlock);
                    attributeCount += leafAttributeBlock.getHeader().getEntryCount();
                }
            }
        }

        List<FSAttribute> attributes = new ArrayList<>(attributeCount);
        for (XfsLeafAttributeBlock attributeBlock : attributeBlocks) {
            attributes.addAll(attributeBlock.getAttributes());
        }
        return attributes;
    }
}
