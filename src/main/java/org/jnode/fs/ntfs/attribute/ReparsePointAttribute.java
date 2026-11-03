package org.jnode.fs.ntfs.attribute;

import org.jnode.fs.ntfs.FileRecord;

/**
 * The interface for a reparse point attribute.
 *
 * @author Luke Quinane
 */
public interface ReparsePointAttribute {
    /**
     * Gets the reparse point tag. The type identifies the type of data stored in the reparse point attribute.
     *
     * @return the tag.
     */
    int getReparseTag();

    /**
     * Gets the length of the reparse point data.
     *
     * @return the length.
     */
    int getReparseDataLength();

    /**
     * Gets the reparse data: the {@link #getReparseDataLength()} bytes after the tag and length, cut short if the
     * attribute does not hold that many. Windows caps reparse data at 16 KiB ({@code MAXIMUM_REPARSE_DATA_BUFFER_SIZE}).
     *
     * @return the data.
     */
    byte[] getReparseData();

    /**
     * Gets the attribute's file record.
     *
     * @return the file record.
     */
    FileRecord getFileRecord();
}
