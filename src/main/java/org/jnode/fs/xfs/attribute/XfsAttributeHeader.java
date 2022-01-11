package org.jnode.fs.xfs.attribute;

import org.jnode.fs.xfs.XfsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * A XFS Attribute header.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */

public class XfsAttributeHeader extends XfsObject {

    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(XfsAttributeHeader.class);

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
    public XfsAttributeHeader(byte [] data, long offset) {
        super(data, (int) offset);
        toSize = getUInt8(0);
        count = getUInt8(2);
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

