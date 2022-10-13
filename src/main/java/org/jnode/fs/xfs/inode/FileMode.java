package org.jnode.fs.xfs.inode;

import java.util.List;

import org.jnode.fs.xfs.Flags;

/**
 * File modes.
 * Specifies the mode access bits and type of file using the standard S_Ixxx values defined in stat.h.
 *
 * @see <a href="https://github.com/torvalds/linux/blob/master/include/uapi/linux/stat.h"> S_Ixxx in stat.h</a>
 * @see <a href="https://en.wikibooks.org/wiki/C_Programming/POSIX_Reference/sys/stat.h#Member_constants">st_mode</a>
 */
public enum FileMode implements Flags {
    // FILE PERMISSIONS
    OTHER_X(0x0001), // S_IXOTH, Execute or search permission bit for other users.
    OTHER_W(0x0002), // S_IWOTH, Write permission bit for other users.
    OTHER_R(0x0004), // S_IROTH, Read permission bit for other users

    GROUP_X(0x0008), // S_IXGRP, Execute or search permission bit for the group owner of the file.
    GROUP_W(0x0010), // S_IWGRP, Write permission bit for the group owner of the file.
    GROUP_R(0x0020), // S_IRGRP, Read permission bit for the group owner of the file.

    USER_X(0x0040), // S_IXUSR, Execute (for ordinary files) or search (for directories) permission bit for the owner of the file.
    USER_W(0x0080), // S_IWUSR, Write permission bit for the owner of the file.
    USER_R(0x0100), // S_IRUSR, Read permission bit for the owner of the file.

    /**
     * The sticky bit (S_ISVTX) on a directory means that a file in that directory can be renamed or deleted only by the owner of the file,
     * by the owner of the directory, and by a privileged process.
     */
    STICKY_BIT(0x0200), // S_ISVTX
    SET_GID(0x0400), // S_ISGID, The set-group-ID bit
    SET_UID(0x0800), // S_ISUID, set-user-ID bit

    // FILE TYPE
    NAMED_PIPE(0x1000), // S_IFIFO, FIFO (named pipe)
    CHARACTER_DEVICE(0x2000), // S_IFCHR, character device
    DIRECTORY(0x4000), // S_IFDIR
    BLOCK_DEVICE(0x6000), // S_IFBLK
    FILE(0x8000), // S_IFREG
    SYM_LINK(0xa000), // S_IFLNK
    SOCKET(0xc000); // S_IFSOCK

    private final FlagUtil flagUtil;

    FileMode(int flags) {
        this.flagUtil = new FlagUtil(flags);
    }

    public boolean isSet(long value) {
        return flagUtil.isSet(value);
    }

    public static List<FileMode> fromValue(long value) {
        return FlagUtil.fromValue(values(), value);
    }
}
