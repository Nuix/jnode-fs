package org.jnode.fs.btrfs;

import java.util.Arrays;
import java.util.List;

import org.jnode.fs.FSAttribute;
import org.jnode.util.LittleEndian;

/**
 * One extended attribute (xattr) of a btrfs inode. On disk an {@code XATTR_ITEM} reuses the
 * {@code btrfs_dir_item} layout — header, then the attribute <em>name</em>, then the <em>value</em>
 * bytes immediately after — and one item can hold <b>several</b> attributes back-to-back when their
 * names hash to the same key offset, so {@link #parseItem} loops until the item is consumed.
 * Values are always inline in the tree (bounded by the node size); there is no out-of-line form.
 *
 * <pre>
 * struct btrfs_dir_item {
 *     struct btrfs_disk_key location;    0x00   (unused for xattrs)
 *     __le64 transid;                    0x11
 *     __le16 data_len;                   0x19   (value length; 0 for directory entries)
 *     __le16 name_len;                   0x1b
 *     u8 type;                           0x1d
 *     // name bytes at 0x1e, then data_len value bytes
 * } __attribute__ ((__packed__));
 * </pre>
 *
 * @author David Baird
 */
public class BtrfsAttribute implements FSAttribute {

    private final String name;
    private final byte[] value;

    BtrfsAttribute(String name, byte[] value) {
        this.name = name;
        this.value = value;
    }

    @Override
    public String getName() {
        return name;
    }

    /**
     * {@inheritDoc}
     *
     * @return the raw value bytes exactly as stored (e.g. {@code security.selinux} values keep
     *         their trailing NUL).
     */
    @Override
    public byte[] getValue() {
        return value;
    }

    @Override
    public String toString() {
        return name + " (" + value.length + " bytes)";
    }

    /**
     * Parses every attribute in one {@code XATTR_ITEM}'s data, appending to {@code out}. Multiple
     * attributes share an item when their names collide in the key hash; each entry is a dir_item
     * header + name + value, packed consecutively.
     *
     * @param data the item's data bytes.
     * @param out  receives one {@link BtrfsAttribute} per entry.
     */
    static void parseItem(byte[] data, List<FSAttribute> out) {
        int pos = 0;
        while (pos + BtrfsConstants.DIR_NAME <= data.length) {
            int dataLen = LittleEndian.getUInt16(data, pos + BtrfsConstants.DIR_DATA_LEN);
            int nameLen = LittleEndian.getUInt16(data, pos + BtrfsConstants.DIR_NAME_LEN);
            int nameStart = pos + BtrfsConstants.DIR_NAME;
            long end = (long) nameStart + nameLen + dataLen;
            if (nameLen <= 0 || end > data.length) {
                break; // corrupt entry — stop rather than mis-parse
            }
            String name = new String(data, nameStart, nameLen, java.nio.charset.StandardCharsets.UTF_8);
            byte[] value = Arrays.copyOfRange(data, nameStart + nameLen, (int) end);
            out.add(new BtrfsAttribute(name, value));
            pos = (int) end;
        }
    }
}
