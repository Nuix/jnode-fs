package org.jnode.fs.xfs.directory;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

/**
 * <p>The tail of the block.</p>
 *
 * <pre>
 *     typedef struct xfs_dir2_block_tail {
 *         __uint32_t count;
 *         __uint32_t stale;
 *     } xfs_dir2_block_tail_t;
 * </pre>
 */
@Getter
public class BlockDirectoryTail extends XfsObject {
    /**
     * Number of leaf entries.
     */
    private final long count;

    /**
     * Number of free leaf entries.
     */
    private final long stale;

    /**
     * Creates a new block directory entry.
     *
     * @param data   the data.
     * @param offset the offset.
     */
    public BlockDirectoryTail(byte[] data, int offset) {
        super(data, offset);

        int blockSize = getData().length;

        count = getUInt32(blockSize - 8);
        stale = getUInt32(blockSize - 4);
    }
}
