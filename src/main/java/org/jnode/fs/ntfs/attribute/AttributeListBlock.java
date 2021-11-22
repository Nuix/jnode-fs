/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc., 
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
 
package org.jnode.fs.ntfs.attribute;

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.jnode.fs.ntfs.NTFSStructure;

/**
 * Data structure containing a list of {@link AttributeListEntry} entries.
 *
 * @author Daniel Noll (daniel@noll.id.au)
 */
public final class AttributeListBlock extends NTFSStructure {

    /**
     * The length of the block.
     */
    private final long blockLength;

    /**
     * @param data   binary data for the block.
     * @param offset the offset into the binary data.
     * @param length the length of the attribute list block, or 0 if unknown.
     */
    public AttributeListBlock(byte[] data, int offset, long length) {
        super(data, offset);
        blockLength = length;
    }

    /**
     * Gets an iterator over all the entries in the attribute list.
     *
     * @return an iterator of all attribute list entries.
     */
    public Iterator<AttributeListEntry> getAllEntries() {
        return new AttributeListEntryIterator();
    }

    /**
     * Iteration of attribute list entries.
     */
    private class AttributeListEntryIterator implements Iterator<AttributeListEntry> {

        /**
         * The next element to return.
         */
        private AttributeListEntry nextElement;

        /**
         * Current offset being looked at.
         */
        private int offset = 0;

        /**
         * Returns {@code true} if there are more elements in the iteration.
         *
         * @return {@code true} if there are more elements in the iteration.
         */
        public boolean hasNext() {
            // Safety check in case hasNext is called twice without calling next.
            if (nextElement != null) {
                return true;
            }

            // Ensure that the remaining data length contains a minimum length to encapsulate
            // a list entry record.
            if (offset + 0x1A > blockLength) {
                return false;
            }

            AttributeListEntry entry = new AttributeListEntry(AttributeListBlock.this, offset);

            int typeValue = entry.getType();
            if (NTFSAttribute.Types.fromValue(typeValue) == null) {
                log.debug(String.format("Invalid attribute type found: 0x%x", typeValue));
                return false;
            }

            int elementLength = entry.getSize();
            if (elementLength <= 0 || elementLength + offset > blockLength) {
                log.debug("Invalid attribute length, preventing infinite loop. Data on disk may be corrupt.");
                return false;
            }

            nextElement = entry;

            log.debug(nextElement.toString());
            offset += elementLength;
            return true;
        }

        /**
         * Gets the next entry from the iteration.
         *
         * @return the next entry from the iteration.
         */
        public AttributeListEntry next() {
            if (hasNext()) {
                AttributeListEntry result = nextElement;
                nextElement = null;
                return result;
            } else {
                throw new NoSuchElementException("Iterator has no more entries");
            }
        }

        /**
         * @throws UnsupportedOperationException always.
         */
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
