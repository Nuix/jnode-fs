package org.jnode.fs.btrfs;

import com.google.common.hash.Hashing;

/**
 * btrfs metadata checksums. Every tree block (and the superblock) stores its checksum in the first
 * bytes of the block, over the block body that follows. btrfs supports several algorithms; the
 * default and by far most common is crc32c, which is all we verify — for any other type the caller
 * skips verification rather than reject the volume.
 *
 * <p>btrfs computes the crc32c the standard (Castagnoli) way — init {@code ~0}, then a final
 * {@code ~} — which is exactly what Guava's {@link Hashing#crc32c()} produces, so the stored little
 * -endian value equals Guava's {@code asInt()} over the body.
 *
 * @author David Baird
 */
final class BtrfsChecksum {

    private BtrfsChecksum() {
    }

    /** btrfs_super_block.csum_type value for crc32c (the mkfs default). */
    static final int CSUM_TYPE_CRC32C = 0;
    /** BTRFS_CSUM_SIZE: the checksum field occupies the first 32 bytes of every block. */
    static final int CSUM_SIZE = 32;

    /** True if the crc32c stored in the first 4 bytes matches the body {@code block[32..len]}. */
    static boolean crc32cValid(byte[] block, int len) {
        if (len <= CSUM_SIZE || block.length < len) {
            return false;
        }
        int computed = Hashing.crc32c().hashBytes(block, CSUM_SIZE, len - CSUM_SIZE).asInt();
        int stored = (block[0] & 0xFF) | ((block[1] & 0xFF) << 8)
                | ((block[2] & 0xFF) << 16) | ((block[3] & 0xFF) << 24);
        return computed == stored;
    }
}
