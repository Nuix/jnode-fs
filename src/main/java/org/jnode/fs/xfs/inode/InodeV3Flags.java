package org.jnode.fs.xfs.inode;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * Extended flags associated with a v3 inode.
 * Flags from di_flags2.
 */
public enum InodeV3Flags implements Flags {
    /**
     * For a file, enable DAX to increase performance on
     * persistent-memory storage. If set on a directory, files
     * created in the directory will inherit this flag.
     */
    DAX(0x01),

    /**
     * This inode shares (or has shared) data blocks with another
     * inode.
     */
    REFLINK(0x02),

    /**
     * For files, this is the extent size hint for copy on write
     * operations; see di_cowextsize for details. For
     * directories, the value in di_cowextsize will be copied
     * to all newly created files and directories.
     */
    COWEXTSIZE(0x04);

    private final FlagUtil flagUtil;

    InodeV3Flags(long flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<InodeV3Flags> fromValue(long value) {
        return FlagUtil.fromValue(values(), value);
    }
}
