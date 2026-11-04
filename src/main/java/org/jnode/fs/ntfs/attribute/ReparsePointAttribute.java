package org.jnode.fs.ntfs.attribute;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.jnode.fs.ntfs.FileRecord;

/**
 * The interface for a reparse point attribute.
 *
 * @author Luke Quinane
 */
public interface ReparsePointAttribute {

    /**
     * The size of the tag, the data length and a reserved field that precede the reparse data.
     */
    int REPARSE_HEADER_SIZE = 8;

    /**
     * Gets the reparse point tag. The type identifies the type of data stored in the reparse point attribute.
     *
     * @return the tag.
     * @throws UncheckedIOException if the value cannot be read, or its header lies outside the attribute.
     */
    int getReparseTag();

    /**
     * Gets the length of the reparse point data.
     *
     * @return the length.
     * @throws UncheckedIOException if the value cannot be read, or its header lies outside the attribute.
     */
    int getReparseDataLength();

    /**
     * Gets the reparse data: the {@link #getReparseDataLength()} bytes after the tag and length, cut short if the
     * attribute does not hold that many. Windows caps reparse data at 16 KiB ({@code MAXIMUM_REPARSE_DATA_BUFFER_SIZE}).
     *
     * @return the data.
     * @throws IOException if the value cannot be read, or its header lies outside the attribute.
     */
    byte[] getReparseData() throws IOException;

    /**
     * Gets the attribute's file record.
     *
     * @return the file record.
     */
    FileRecord getFileRecord();
}
