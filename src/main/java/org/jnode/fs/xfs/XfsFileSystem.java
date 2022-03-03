package org.jnode.fs.xfs;

import org.jnode.driver.Device;
import org.jnode.driver.block.BlockDeviceAPI;
import org.jnode.fs.*;
import org.jnode.fs.spi.AbstractFileSystem;
import org.jnode.fs.xfs.inode.INode;

import java.io.IOException;
import java.nio.ByteBuffer;
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
     * Xfs File System version current support for 4/5
     */
    private int xfsVersion;

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

    /**
     * Reads in the file system from the block device.
     *
     * @throws FileSystemException if an error occurs reading the file system.
     */
    public final void read() throws FileSystemException {
        superblock = new Superblock(this);
        agINode = new AllocationGroupINode(this);
        iNodeSize = superblock.getInodeSize();
        blockSize = superblock.getBlockSize();
        aGCount = (int) superblock.getAGCount();
        allocationGroupSize = blockSize * superblock.getTotalBlocks() / aGCount;
        xfsVersion = superblock.getVersion() & 0xF;
    }

    /**
     * Reads in the file system from the block device.
     *
     * * @throws IOException if an error occurs reading the file system.
     */
    public INode getINode(long absoluteINodeNumber) throws IOException {
        long offset = getINodeAbsoluteOffset(absoluteINodeNumber);
        // Reserve the space to read the iNode
        ByteBuffer allocate = ByteBuffer.allocate(getSuperblock().getInodeSize());
        // Read the iNode data
        getApi().read(offset, allocate);
        return new INode(absoluteINodeNumber, allocate.array(), 0,this);
    }

    public long getINodeAbsoluteOffset(long absoluteINodeNumber) {
        final long numberOfRelativeINodeBits = getSuperblock().getAGSizeLog2() + getSuperblock().getINodePerBlockLog2();
        int allocationGroupIndex = (int) (absoluteINodeNumber >> numberOfRelativeINodeBits);
        long allocationGroupBlockNumber = (long) allocationGroupIndex * getSuperblock().getAGSize();
        long relativeINodeNumber  = absoluteINodeNumber & (((long)1 << numberOfRelativeINodeBits) - 1);
        // Calculate the offset of the iNode number.
        return (allocationGroupBlockNumber * getSuperblock().getBlockSize()) + (relativeINodeNumber * getSuperblock().getInodeSize());
    }

    /**
     * Gets the total space value stored in the superblock.
     *
     */
    @Override
    public long getTotalSpace() {
        return superblock.getBlockSize() * superblock.getTotalBlocks();
    }

    /**
     * Gets the total free space value stored in the superblock.
     *
     */
    @Override
    public long getFreeSpace() {
        return superblock.getBlockSize() * superblock.getFreeBlocks();
    }

    /**
     * Gets the total usable space value.
     *
     */
    @Override
    public long getUsableSpace() {
        return superblock.getBlockSize() * (superblock.getTotalBlocks() - superblock.getFreeBlocks());
    }

    public boolean isV5(){
        return xfsVersion == 5;
    }

    /**
     * Gets the valume name.
     *
     */
    @Override
    public String getVolumeName() {
        return superblock.getName();
    }

    /**
     * @see org.jnode.fs.spi.AbstractFileSystem#createFile(FSEntry entry)
     */
    @Override
    protected FSFile createFile(FSEntry entry) throws IOException {
        return new XfsFile((XfsEntry) entry);
    }

    /**
     * @see org.jnode.fs.spi.AbstractFileSystem#createDirectory(FSEntry entry)
     */
    @Override
    protected FSDirectory createDirectory(FSEntry entry) throws IOException {
        return new XfsDirectory((XfsEntry) entry);
    }

    /**
     * @see org.jnode.fs.spi.AbstractFileSystem#createRootEntry()
     */
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

}
