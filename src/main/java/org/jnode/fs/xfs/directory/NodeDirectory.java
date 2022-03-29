package org.jnode.fs.xfs.directory;

import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.xfs.XfsFileSystem;
import org.jnode.fs.xfs.XfsObject;
import org.jnode.fs.xfs.extent.DataExtent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A XFS Node Directory.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class NodeDirectory extends XfsObject {

    // TODO: check where these values need to be used and the name of the class.
    /**
     * v3 directory block magic number header "XDF3".
     */
    private static final long NODE_FREE_SPACE_V5 = asciiToHex("XDF3");

    /**
     * directory block magic number header "XD2F".
     */
    private static final long NODE_FREE_SPACE = asciiToHex("XD2F");

    /**
     * The list of extents of this block directory.
     */
    private final List<DataExtent> extents;

    /**
     * The filesystem.
     */
    private final XfsFileSystem fs;

    /**
     * The number of the inode.
     */
    private final long iNodeNumber;

    /**
     * The extent index of the leaf.
     */
    private final int leafExtentIndex;

    /**
     * Creates a Leaf directory entry.
     *
     * @param data            of the inode.
     * @param offset          of the inode's data
     * @param fileSystem      of the image
     * @param iNodeNumber     of the inode
     * @param extents         of the block directory
     * @param leafExtentIndex of the block directory
     */
    public NodeDirectory(byte[] data, long offset, XfsFileSystem fileSystem, long iNodeNumber, List<DataExtent> extents, long leafExtentIndex) {
        super(data, (int) offset);
        this.extents = extents;
        this.fs = fileSystem;
        this.iNodeNumber = iNodeNumber;
        this.leafExtentIndex = (int) leafExtentIndex;
    }

    public long getiNodeNumber() {
        return iNodeNumber;
    }

    public int getLeafExtentIndex() {
        return leafExtentIndex;
    }

    /**
     * Get the node block entries
     *
     * @return a list of inode entries
     */
    public List<FSEntry> getEntries(FSDirectory parentDirectory) throws IOException {
        List<FSEntry> entries = new ArrayList<>(extents.size() * 120);
        for (DataExtent dataExtent : extents) {
            LeafDirectory.extractEntriesFromExtent(fs, dataExtent, entries, parentDirectory);
        }
        return entries;
    }
}


