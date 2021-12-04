package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class MyInodeHeader extends MyXfsBaseAccessor {
    public MyInodeHeader(FSBlockDeviceAPI devApi, long superBlockStart) {
        super(devApi, superBlockStart);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    @Override
    public long getSignature() throws IOException {
        return 0;
    }

    /**
     * Number of directory entries.
     *
     * @return
     */
    public long getCount() throws IOException {
        return read(0, 1);
    }

    /**
     * Number of directory entries requiring 64-bit entries, if any inode numbers require 64-bits. Zero otherwise.
     *
     * @return
     */
    public long getI8Count() throws IOException {
        return read(1, 1);
    }

    public long getParentInode() throws IOException {
        return read(2, getI8Count() > 0 ? 8 : 4);
    }

    public long getFirstEntryAbsoluteOffset() throws IOException {
        return getOffset() + (getI8Count() > 0 ? 10 : 6);
    }
}
