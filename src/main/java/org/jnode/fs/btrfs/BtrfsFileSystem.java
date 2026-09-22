package org.jnode.fs.btrfs;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jnode.driver.Device;
import org.jnode.driver.block.BlockDeviceAPI;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FSFile;
import org.jnode.fs.FileSystem;
import org.jnode.fs.FileSystemException;
import org.jnode.fs.FileSystemType;
import org.jnode.fs.spi.AbstractFileSystem;

/**
 * A read-only btrfs file system, backed by a {@link BtrfsVolume} over the device's
 * {@link BlockDeviceAPI}.
 *
 * @author David Baird
 */
public class BtrfsFileSystem extends AbstractFileSystem<BtrfsEntry> {

    private final BtrfsVolume volume;

    public BtrfsFileSystem(Device device, FileSystemType<? extends FileSystem<BtrfsEntry>> type)
            throws FileSystemException {
        super(device, true, type);
        try {
            final BlockDeviceAPI api = getApi();
            BtrfsBlockReader reader = (offset, dst, off, len) -> api.read(offset, ByteBuffer.wrap(dst, off, len));
            volume = new BtrfsVolume(reader);
        } catch (IOException e) {
            throw new FileSystemException("Failed to read btrfs volume: " + e.getMessage(), e);
        }
    }

    public BtrfsVolume getVolume() {
        return volume;
    }

    /**
     * Descend into snapshot subvolumes (default: skipped, so a snapper/openSUSE root isn't inflated
     * by dozens of near-identical snapshots). Call before walking the tree.
     */
    public void setDescendSnapshots(boolean descend) {
        volume.setDescendSnapshots(descend);
    }

    /**
     * Verify each metadata block's crc32c as it's read (off by default). A mismatch raises an
     * {@code IOException} instead of returning corrupt structure.
     */
    public void setVerifyChecksums(boolean verify) {
        volume.setVerifyChecksums(verify);
    }

    @Override
    protected BtrfsEntry createRootEntry() throws IOException {
        return new BtrfsEntry(volume.getRoot(), "/", this, null);
    }

    @Override
    protected FSFile createFile(FSEntry entry) throws IOException {
        return new BtrfsFile((BtrfsEntry) entry);
    }

    @Override
    protected FSDirectory createDirectory(FSEntry entry) throws IOException {
        return new BtrfsDirectory((BtrfsEntry) entry);
    }

    @Override
    public long getTotalSpace() {
        return volume.getSuperblock().getTotalBytes();
    }

    @Override
    public long getFreeSpace() {
        return volume.getSuperblock().getTotalBytes() - volume.getSuperblock().getBytesUsed();
    }

    @Override
    public long getUsableSpace() {
        return getFreeSpace();
    }

    /**
     * {@inheritDoc}
     *
     * @return the superblock label ({@code mkfs.btrfs -L}), or an empty string when unlabelled.
     */
    @Override
    public String getVolumeName() {
        return volume.getSuperblock().getLabel();
    }
}
