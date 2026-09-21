package org.jnode.driver.block;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * A block device backed by a byte array, for tests that need a volume without writing an image to disk.
 *
 * <p>Unlike {@link FileDevice} this honours the position and limit of the buffer it is handed, so a read into the
 * middle of a caller's array lands where the caller asked for it.</p>
 */
public class ByteArrayDevice implements BlockDeviceAPI {

    private final byte[] data;

    public ByteArrayDevice(byte[] data) {
        this.data = data;
    }

    @Override
    public long getLength() {
        return data.length;
    }

    @Override
    public void read(long devOffset, ByteBuffer dest) throws IOException {
        int length = dest.remaining();

        if (devOffset < 0 || devOffset + length > data.length) {
            throw new IOException("Read of " + length + " bytes at " + devOffset + " is outside the device");
        }

        dest.put(data, (int) devOffset, length);
    }

    @Override
    public void write(long devOffset, ByteBuffer src) throws IOException {
        int length = src.remaining();

        if (devOffset < 0 || devOffset + length > data.length) {
            throw new IOException("Write of " + length + " bytes at " + devOffset + " is outside the device");
        }

        src.get(data, (int) devOffset, length);
    }

    @Override
    public void flush() {
    }
}
