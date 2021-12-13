package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

public class MyShortFormDirectory extends MyXfsBaseAccessor implements IMyDirectory {

    private final boolean is8BitInodeNumber;
    private final long nameSize;

    public MyShortFormDirectory(FSBlockDeviceAPI devApi, long superBlockStart, boolean is8BitInodeNumber) throws IOException {
        super(devApi, superBlockStart);
        this.is8BitInodeNumber = is8BitInodeNumber;
        this.nameSize = read(0, 1);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    public long getNameSize() throws IOException {
        return nameSize;
    }

    public long getTagOffset() throws IOException {
        return read(1, 2);
    }

    public String getName() throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate((int) nameSize);
        devApi.read(getOffset() + 3, buffer);
        return new String(buffer.array(), StandardCharsets.UTF_8);
    }

    public long getFileType() throws IOException {
        return read(getNameSize() + 3, 1);
    }

    public long getINodeNumber() throws IOException {
        return read(getNameSize() + 4, is8BitInodeNumber ? 8 : 4);
    }

    public long getOffsetSize() {
        return/* Name Size and Tag Offset and File Type */ 4 + nameSize + (is8BitInodeNumber ? 8 : 4);
    }


}
