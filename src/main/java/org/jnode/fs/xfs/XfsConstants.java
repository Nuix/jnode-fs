package org.jnode.fs.xfs;

/**
 * XFS constants.
 *
 * @author Luke Quinane.
 */
public class XfsConstants {

    /**
     * Prevent instantiation.
     */
    private XfsConstants() {
        // prevent instantiation.
    }

    /**
     * {@link UnsupportedOperationException} message.
     */
    public static final String XFS_IS_READ_ONLY = "XFS is read only";

    /**
     * The number of bytes on 32 GiB.
     */
    public static final long BYTES_IN_32G = 0x8_0000_0000L;

}
