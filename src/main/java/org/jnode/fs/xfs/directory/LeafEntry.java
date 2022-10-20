package org.jnode.fs.xfs.directory;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

/**
 * Leaf entry.
 *
 * <pre>
 *     typedef struct xfs_dir2_leaf_entry {
 *         xfs_dahash_t hashval;
 *         xfs_dir2_dataptr_t address;
 *     } xfs_dir2_leaf_entry_t;
 * </pre>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
@Getter
public class LeafEntry extends XfsObject {

    /**
     * The unit of the address is 8 bytes.
     * So when reading data (by offset), the address needs to be converted to offset first.
     */
    public static final int ADDRESS_TO_OFFSET = 8;

    /**
     * The Hash value of the name of the directory entry. This is used to speed up entry lookups.
     */
    private final long hashval;

    /**
     * The Block offset of the entry, in eight byte units {@link #ADDRESS_TO_OFFSET}.
     */
    private final long address;

    /**
     * Creates a Leaf entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data.
     */
    public LeafEntry(byte[] data, long offset) {
        super(data, (int) offset);
        hashval = readUInt32();
        address = readUInt32();
    }

    /**
     * Gets the string information of the leaf entry.
     *
     * @return a string
     */
    @Override
    public String toString() {
        return "LeafEntry{hashval=" + Long.toHexString(hashval) +
                ", address=" + Long.toHexString(address) + '}';
    }
}
