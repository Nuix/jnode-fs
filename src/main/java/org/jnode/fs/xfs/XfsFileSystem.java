package org.jnode.fs.xfs;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import com.google.common.base.Splitter;
import org.jnode.driver.Device;
import org.jnode.driver.block.BlockDeviceAPI;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FSFile;
import org.jnode.fs.FileSystem;
import org.jnode.fs.FileSystemException;
import org.jnode.fs.FileSystemType;
import org.jnode.fs.spi.AbstractFileSystem;
import org.jnode.fs.xfs.inode.INodeBTree;
import org.jnode.fs.xfs.inode.INode;
/**
 * An XFS file system.
 *
 * @author Luke Quinane
 */
public class XfsFileSystem extends AbstractFileSystem<XfsEntry> {

    /**
     * The superblock.
     */
    private Superblock superblock;

    /**
     * The allocation group for inodes.
     */
    private AllocationGroupINode agINode;

    /**
     * The inode b-tree.
     */
    private INodeBTree inodeBTree;

    private INode inode;

    /**
     * The inode size.
     */
    private int iNodeSize;

    /**
     * The allocation group size.
     */
    private long allocationGroupSize;

    /**
     * The allocation group block size.
     */
    private long blockSize;

    /**
     * The allocation group count.
     */
    private int aGCount;


    /**
     * Construct an XFS file system.
     *
     * @param device device contains file system.
     * @param type the file system type.
     * @throws FileSystemException device is null or device has no {@link BlockDeviceAPI} defined.
     */
    public XfsFileSystem(Device device, FileSystemType<? extends FileSystem<XfsEntry>> type)
        throws FileSystemException {

        super(device, true, type);
    }

    public static String HexToAscii(String hexString) {
        try {
            StringBuilder ascii = new StringBuilder();
            final Iterable<String> chars = Splitter.fixedLength(2).split(hexString);
            for (String c : chars) {
                ascii.append((char) Byte.parseByte(c, 16));
            }
            return ascii.toString();
        } catch (Throwable t) {
            return "INVALID";
        }
    }

    /**
     * Reads in the file system from the block device.
     *
     * @throws FileSystemException if an error occurs reading the file system.
     */
    public final void read() throws FileSystemException, IOException {
        superblock = new Superblock(this);
        agINode = new AllocationGroupINode(this);
        iNodeSize = superblock.getInodeSize();
        blockSize = superblock.getBlockSize();
        aGCount = (int) superblock.getAGCount();
        allocationGroupSize = blockSize * superblock.getTotalBlocks() / aGCount;

        try {
            inodeBTree = new INodeBTree(this, agINode);
        } catch (IOException e) {
            throw new FileSystemException(e);
        }
    }



    public INode getINode(long absoluteINodeNumber) throws IOException {

        final long numberOfRelativeINodeBits = getSuperblock().getAGSizeLog2() + getSuperblock().getINodePerBlockLog2();
        int allocationGroupIndex = (int) (absoluteINodeNumber >> numberOfRelativeINodeBits);
        long allocationGroupBlockNumber = (long) allocationGroupIndex * getSuperblock().getAGSize();
        long relativeINodeNumber  = absoluteINodeNumber & (((long)1 << numberOfRelativeINodeBits) - 1);
        // Calculate the offset of the iNode number.
        long offset = (allocationGroupBlockNumber * getSuperblock().getBlockSize()) + (relativeINodeNumber * getSuperblock().getInodeSize());
        // Reserve the space to read the iNode
        ByteBuffer allocate = ByteBuffer.allocate(getSuperblock().getInodeSize());
        // Read the iNode data
        this.getApi().read(offset, allocate);
        return new INode(absoluteINodeNumber, allocate.array(), 0);
    }

//    public String getSymLinkText() throws IOException {
//        ByteBuffer buffer = ByteBuffer.allocate((int) getSuperblock().getInodeSize());
//        this.getApi().read(getOffset() + getINodeSizeForOffset(),buffer);
//        return new String(buffer.array(), StandardCharsets.US_ASCII);
//    }


    @Override
    public long getTotalSpace() throws IOException {
        //TOTAL NUMBER OF BLOCKS..........
        return superblock.getBlockSize() * superblock.getTotalBlocks();
    }

    @Override
    public long getFreeSpace() throws IOException {
        return superblock.getBlockSize() * superblock.getFreeBlocks();
    }

    @Override
    public long getUsableSpace() throws IOException {
        return superblock.getBlockSize() * (superblock.getTotalBlocks() - superblock.getFreeBlocks());
    }

    @Override
    public String getVolumeName() throws IOException {
        return superblock.getName();
    }

    @Override
    protected FSFile createFile(FSEntry entry) throws IOException {
        return new XfsFile((XfsEntry) entry);
    }

    @Override
    protected FSDirectory createDirectory(FSEntry entry) throws IOException {
        return new XfsDirectory((XfsEntry) entry);
    }

    @Override
    protected XfsEntry createRootEntry() throws IOException {
        long rootIno = superblock.getRootInode();
        return new XfsEntry(this.getINode(rootIno), "/", 0, this, null);
    }

    /**
     * Reads block from the file system.
     *
     * @param startBlock the start block.
     * @param dest the destination to read into.
     * @throws IOException if an error occurs.
     */
    public void readBlocks(long startBlock, ByteBuffer dest) throws IOException {
        readBlocks(startBlock, 0, dest);
    }

    /**
     * Reads block from the file system.
     *
     * @param startBlock the start block.
     * @param blockOffset the offset within the block to start reading from.
     * @param dest the destination to read into.
     * @throws IOException if an error occurs.
     */
    public void readBlocks(long startBlock, int blockOffset, ByteBuffer dest) throws IOException {
        getApi().read(superblock.getBlockSize() * startBlock + blockOffset, dest);
    }

    /**
     * Gets the superblock.
     *
     * @return the superblock.
     */
    public Superblock getSuperblock() {
        return superblock;
    }

    /**
     * Gets the inode b-tree.
     *
     * @return the b-tree.
     */
    public INodeBTree getInodeBTree() {
        return inodeBTree;
    }
}
