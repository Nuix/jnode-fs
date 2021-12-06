package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MyExtentInformation extends MyXfsBaseAccessor {

    public MyExtentInformation(FSBlockDeviceAPI devApi, long superBlockStart) {
        super(devApi, superBlockStart);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    // TODO: Understand where the formula comes from currently copied from XFS DataExtent class
    public long getStartOffset() throws IOException {
        return ((read(0,4) & 0x7fffffff) << 22) | ((read(4,4) & 0xfffffcL) >> 9);
    }
    public long getStartBlock() throws IOException {
        return ((read(12,4) & 0xfffc0000L) >> 21) | (read(8,4) << 14) | ((read(4,4) & 0x3fL) << 38);
    }
    public long getBlockCount() throws IOException {
        return read(0xc,4) & 0x3ffffL;
    }
    public long getState() throws IOException {
        return read(0,1);
    }

}
