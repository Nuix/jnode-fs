package org.jnode.fs.xfs.attribute;

import org.jnode.fs.xfs.XfsObject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class XfsAttributeHeader extends XfsObject {

    /**
     * The size value of the value.
     */
    private final long toSize;

    /**
     * The number of attributes to read.
     */
    private final long count;

    /**
     * Create a XFS SELinux attribute header.
     *
     */
    public XfsAttributeHeader(byte [] data, long offset) throws IOException {
        super(data, (int) offset);
        toSize = read(0,2);
        count = read(2,1);
    }

    /**
     * Gets the validSignatures.
     *
     * @return the valid magic values.
     */
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    /**
     * Gets the magic signature.
     *
     * @return the magic signature.
     */
    @Override
    public long getMagicSignature() throws IOException {
        return 0L;
    }

    /**
     * Gets the stored inode number if this is a v3 inode.
     *
     * @return the number.
     */
    public long getToSize() {
        return toSize;
    }

    /**
     * Gets the stored inode number if this is a v3 inode.
     *
     * @return the number.
     */
    public long getCount() {
        return count;
    }
}

