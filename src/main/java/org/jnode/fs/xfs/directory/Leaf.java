package org.jnode.fs.xfs.directory;

import org.jnode.fs.xfs.XfsFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A XFS Leaf directory.
 *
 * Leaf Directories
 * Once a Block Directory has filled the block, the directory data is changed into a new format called leaf.
 *
 * @author
 */
public class Leaf {

    /**
     * The leaf information of this block directory.
     */
    private final LeafInfo leafInfo;

    /**
     * The list of leaf entries.
     */
    private final List<LeafEntry> leafEntries;

    /**
     * The Number of best free entries.
     */
    private final long bestCount;

    /**
     * Creates a leaf directory.
     *
     * @param data of the inode.
     * @param offset of the inode's data.
     * @param fileSystem of the inode.
     * @param extentCount of the leaf entries.
     *
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public Leaf(byte[] data, long offset, XfsFileSystem fileSystem, int extentCount) throws IOException {
        leafInfo = new LeafInfo(data,offset,fileSystem);
        final int infoCount = (int) leafInfo.getCount();
        bestCount = extentCount;
        leafEntries = new ArrayList<>(infoCount);
        long leafEntryOffset = offset + 64;
        for (int i=0;i<infoCount;i++) {
            final LeafEntry entry = new LeafEntry(data, leafEntryOffset, fileSystem);
            leafEntries.add(entry);
            leafEntryOffset += 8; // Add LeafEntry Size
        }
    }

    /**
     * Gets the Leaf information header
     *
     * @return a leaf block information
     */
    public LeafInfo getLeafInfo() {
        return leafInfo;
    }

    /**
     * Gets the leaf block entries.
     *
     * @return a list of leaf entries.
     */
    public List<LeafEntry> getLeafEntries() {
        return leafEntries;
    }

    /**
     * Gets the number of leaf entries of the block.
     *
     * @return the number of leaf entries
     */
    public long getBestCount() {
        return bestCount;
    }
}
