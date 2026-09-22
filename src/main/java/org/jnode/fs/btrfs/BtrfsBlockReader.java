package org.jnode.fs.btrfs;

import java.io.IOException;

/**
 * Reads raw bytes at a <em>physical</em> device offset — the one thing the btrfs reader needs from
 * its backing store. Implemented over jnode's {@code BlockDeviceAPI} in production and over a plain
 * file in tests, so the format parser is decoupled from the device layer.
 *
 * @author David Baird
 */
public interface BtrfsBlockReader {

    /**
     * Reads exactly {@code length} bytes at physical {@code offset} into {@code dst[off..]}.
     *
     * @param offset the physical device offset to read at.
     * @param dst    the destination buffer.
     * @param off    the offset within {@code dst} to write to.
     * @param length the exact number of bytes to read.
     * @throws IOException if the underlying device read fails or is short.
     */
    void read(long offset, byte[] dst, int off, int length) throws IOException;

    default byte[] read(long offset, int length) throws IOException {
        byte[] buf = new byte[length];
        read(offset, buf, 0, length);
        return buf;
    }
}
