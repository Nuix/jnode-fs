package org.jnode.driver.block;

import java.io.File;
import java.io.IOException;

/**
 * A {@link FileDevice} over a test image that has been decompressed to a temporary file, which it deletes when it
 * is closed.
 *
 * <p>Test images are held gzipped and have to be expanded before they can be read, and the expanded copies are
 * large - the NTFS volumes here run to 400 and 512 MB. Leaving the caller to delete them means every test carries
 * a try/finally whose only job is cleanup, and one that forgets it fills the temp filesystem on a machine running
 * the suite repeatedly. Tying the copy's life to the device removes both problems:</p>
 *
 * <pre>
 * try (TestImageDevice device = FileSystemTestUtils.openImage("org/jnode/fs/ntfs/some-image.dd")) {
 *     ...
 * }
 * </pre>
 */
public class TestImageDevice extends FileDevice {

    private final File image;

    /**
     * Creates a device over a temporary copy of an image.
     *
     * @param image the temporary file, which is deleted when this device is closed.
     * @throws IOException if the file cannot be opened.
     */
    public TestImageDevice(File image) throws IOException {
        super(image, "r");
        this.image = image;
    }

    /**
     * Gets the temporary file backing this device, for the rare test that needs the path itself.
     *
     * @return the file.
     */
    public File getImage() {
        return image;
    }

    @Override
    public void close() {
        try {
            super.close();
        } finally {
            if (!image.delete() && image.exists()) {
                image.deleteOnExit();
            }
        }
    }
}
