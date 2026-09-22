package org.jnode.fs.btrfs;

import java.io.IOException;
import java.nio.ByteBuffer;

import org.jnode.driver.Device;
import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.fs.BlockDeviceFileSystemType;
import org.jnode.fs.FileSystemException;
import org.jnode.partitions.PartitionTableEntry;

/**
 * The file system type for btrfs (read-only). Detected by the {@code _BHRfS_M} magic at offset 0x40
 * within the superblock, which lives at physical offset {@link BtrfsConstants#SUPERBLOCK_OFFSET}.
 *
 * @author David Baird
 */
public class BtrfsFileSystemType implements BlockDeviceFileSystemType<BtrfsFileSystem> {

    public static final Class<BtrfsFileSystemType> ID = BtrfsFileSystemType.class;

    @Override
    public String getName() {
        return "BTRFS";
    }

    @Override
    public BtrfsFileSystem create(Device device, boolean readOnly) throws FileSystemException {
        return new BtrfsFileSystem(device, this);
    }

    @Override
    public boolean supports(PartitionTableEntry pte, byte[] firstSector, FSBlockDeviceAPI devApi) {
        // the superblock (and its magic) is at 0x10000, past firstSector -- read it directly
        ByteBuffer buf = ByteBuffer.allocate(8);
        try {
            devApi.read(BtrfsConstants.SUPERBLOCK_OFFSET + BtrfsConstants.SB_MAGIC, buf);
        } catch (IOException e) {
            return false;
        }
        byte[] magic = buf.array();
        for (int i = 0; i < BtrfsConstants.MAGIC.length; i++) {
            if (magic[i] != BtrfsConstants.MAGIC[i]) {
                return false;
            }
        }
        return true;
    }
}
