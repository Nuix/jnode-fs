package org.jnode.fs.xfs.directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsEntry;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.inode.INode;

/**
 * <p>A XFS block directory inode.</p>
 *
 * <p>When the shortform directory space exceeds the space in an inode, the
 * directory data is moved into a new single directory block outside the inode.
 * The inode’s format is changed from “local” to “extent”.</p>
 *
 * <pre>
 *     struct xfs_dir3_data_hdr {
 *         struct xfs_dir3_blk_hdr hdr;
 *         xfs_dir2_data_free_t best_free[XFS_DIR2_DATA_FD_COUNT];
 *         __be32 pad;
 *     };
 * </pre>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class BlockDirectory extends XfsObject {
    /**
     * The filesystem.
     */
    XfsFileSystem fs;

    @Getter
    private final BlockDirectoryHeader header;

    @Getter
    private final BlockDirectoryTail tail;

    /**
     * Creates a new block directory entry.
     *
     * @param data   the data.
     * @param offset the offset.
     * @param fs     the filesystem instance.
     */
    public BlockDirectory(byte[] data, int offset, XfsFileSystem fs) throws IOException {
        super(data, offset);

        header = new BlockDirectoryHeader(data, offset);
        tail = new BlockDirectoryTail(data, offset);
        this.fs = fs;
    }

    /**
     * Get the inode's entries
     *
     * @return a list of inode entries
     */
    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        int blockSize = getData().length;

        int activeDirs = (int) (tail.getCount() - tail.getStale());

        List<FSEntry> data = new ArrayList<>(activeDirs);
        int leafOffset = blockSize - ((activeDirs + 1) * LeafEntry.ADDRESS_TO_OFFSET);
        for (int i = 0; i < activeDirs; i++) {
            LeafEntry leafEntry = new LeafEntry(getData(), leafOffset + (i * (long) LeafEntry.ADDRESS_TO_OFFSET));
            if (leafEntry.getAddress() == 0) {
                continue;
            }
            if (!BlockDirectoryEntry.isFreeTag(getData(), leafEntry.getAddress() * LeafEntry.ADDRESS_TO_OFFSET)) {
                BlockDirectoryDataEntry entry = new BlockDirectoryDataEntry(getData(), leafEntry.getAddress() * LeafEntry.ADDRESS_TO_OFFSET, fs.isV5());

                INode iNode = fs.getINode(entry.getINodeNumber());
                data.add(new XfsEntry(iNode, entry.getName(), i, fs, parentDirectory));
            }
        }
        return data;
    }
}

