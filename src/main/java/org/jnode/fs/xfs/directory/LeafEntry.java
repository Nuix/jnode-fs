package org.jnode.fs.xfs.directory;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;

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
     * The Hash value of the name of the directory entry. This is used to speed up entry lookups.
     */
    private final long hashval;

    /**
     * The Block offset of the entry, in eight byte units.
     */
    private final long address;

    /**
     * Creates a Leaf entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the leaf directory.
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
