package org.jnode.fs.xfs.inode;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * File modes.
 * Specifies the mode access bits and type of file using the standard S_Ixxx values defined in stat.h.
 *
 * @see <a href="https://github.com/torvalds/linux/blob/master/include/uapi/linux/stat.h"> S_Ixxx in stat.h</a>
 * @see <a href="https://en.wikibooks.org/wiki/C_Programming/POSIX_Reference/sys/stat.h#Member_constants">st_mode</a>
 */
public enum FileMode {
    // FILE PERMISSIONS
    OTHER_X(0x0007, 0x0001), // S_IXOTH
    OTHER_W(0x0007, 0x0002), // S_IWOTH
    OTHER_R(0x0007, 0x0004), // S_IROTH
    GROUP_X(0x0038, 0x0008), // S_IXGRP
    GROUP_W(0x0038, 0x0010), // S_IWGRP
    GROUP_R(0x0038, 0x0020), // S_IRGRP
    USER_X(0x01c0, 0x0040), // S_IXUSR
    USER_W(0x01c0, 0x0080), // S_IWUSR
    USER_R(0x01c0, 0x0100), // S_IRUSR
    //TODO: Check mask
//        STICKY_BIT(0xFFFF,0x0200), // S_ISVTX
//        SET_GID(0xFFFF,0x0400), // S_ISGID
//        SET_UID(0xFFFF,0x0800), // S_ISUID
    // FILE TYPE
    NAMED_PIPE(0xf000, 0x1000), // S_IFIFO
    CHARACTER_DEVICE(0xf000, 0x2000), // S_IFCHR
    DIRECTORY(0xf000, 0x4000), // S_IFDIR
    BLOCK_DEVICE(0xf000, 0x6000), // S_IFBLK
    FILE(0xf000, 0x8000), // S_IFREG
    SYM_LINK(0xf000, 0xa000), // S_IFLNK
    SOCKET(0xf000, 0xc000); // S_IFSOCK

    /**
     * The mask.
     */
    private final int mask;

    /**
     * The value.
     */
    private final int val;

    FileMode(int mask, int val) {
        this.mask = mask;
        this.val = val;
    }

    public boolean isSet(int data) {
        return (data & mask) == val;
    }

    public static List<FileMode> getModes(int data) {
        return Arrays.stream(FileMode.values()).filter(mode -> mode.isSet(data)).collect(Collectors.toList());
    }
}
