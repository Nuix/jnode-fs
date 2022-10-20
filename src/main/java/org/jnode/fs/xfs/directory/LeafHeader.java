package org.jnode.fs.xfs.directory;

import java.io.IOException;

import lombok.Getter;
import org.jnode.fs.xfs.XfsObject;

/**
 * A XFS leaf info.
 * It is either
 * <pre>
 *     typedef struct xfs_dir2_leaf_hdr {
 *         xfs_da_blkinfo_t info;
 *         __uint16_t count;
 *         __uint16_t stale;
 *     } xfs_dir2_leaf_hdr_t;
 * </pre>
 * <p>
 * or
 * <pre>
 *     struct xfs_dir3_leaf_hdr {
 *         struct xfs_da3_blkinfo info;
 *         __uint16_t count;
 *         __uint16_t stale;
 *         __be32 pad;
 *     };
 * </pre>
 * <p>
 * There are two versions of the blkinfo
 * Version before v5:
 * <pre>
 *     typedef struct xfs_da_blkinfo {
 *         __be32 forw;
 *         __be32 back;
 *         __be16 magic;
 *         __be16 pad;
 *     } xfs_da_blkinfo_t;
 * </pre>
 * <p>
 * Version v5:
 * <pre>
 *     struct xfs_da3_blkinfo {
 *         __be32 forw;
 *         __be32 back;
 *         __be16 magic;
 *         __be16 pad;
 *         __be32 crc;
 *         __be64 blkno;
 *         __be64 lsn;
 *         uuid_t uuid;
 *         __be64 owner;
 *     };
 * </pre>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
@Getter
public abstract class LeafHeader extends XfsObject {
    /**
     * The Number of leaf entries.
     */
    private final int count;

    /**
     * The Number of free leaf entries.
     */
    private final int stale;

    /**
     * Creates a Leaf block information entry.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    LeafHeader(byte[] data, long offset) throws IOException {
        super(data, (int) offset);

        readBlockInfo();
        count = readUInt16();
        stale = readUInt16();
    }

    protected abstract void readBlockInfo() throws IOException;
}
