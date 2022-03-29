package org.jnode.fs.xfs.directory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>A XFS Leaf directory.</p>
 *
 * <p>Once a Block Directory has filled the block, the directory data is changed into a new format called leaf.</p>
 *
 * @author Ricardo Garza
 * @author Julio Parra
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
     * @param data        of the inode.
     * @param offset      of the inode's data.
     * @param v5          is filesystem on v5
     * @param extentCount of the leaf entries.
     * @throws IOException if an error occurs reading in the leaf directory.
     */
    public Leaf(byte[] data, long offset, boolean v5, int extentCount) throws IOException {
        leafInfo = new LeafInfo(data, offset, v5);
        int infoCount = leafInfo.getCount() - leafInfo.getStale();
        bestCount = extentCount;
        leafEntries = new ArrayList<>(infoCount);
        long leafEntryOffset = offset + (v5 ? 64 : 16);
        for (int i = 0; i < infoCount; i++) {
            LeafEntry entry = new LeafEntry(data, leafEntryOffset);
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
