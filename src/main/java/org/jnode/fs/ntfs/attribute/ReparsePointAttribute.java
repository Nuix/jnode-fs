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
     * Gets the attribute's file record.
     *
     * @return the file record.
     */
    FileRecord getFileRecord();
}
