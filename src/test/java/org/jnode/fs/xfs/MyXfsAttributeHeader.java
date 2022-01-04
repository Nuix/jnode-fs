package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MyXfsAttributeHeader extends MyXfsBaseAccessor {

    private final long toSize;
    private final long count;

    public MyXfsAttributeHeader(FSBlockDeviceAPI devApi, long superBlockStart, MyXfsFileSystem fs) throws IOException {
        super(devApi, superBlockStart, fs);
        toSize = read(0,2);
        count = read(2,1);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    public long getToSize() {
        return toSize;
    }

    public long getCount() {
        return count;
    }
}
