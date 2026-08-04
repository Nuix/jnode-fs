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
 
package org.jnode.fs.ntfs.security;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.jnode.fs.ntfs.NTFSFile;
import org.jnode.util.LittleEndian;

/**
 * A security descriptor stream, '$Secure:$SDS', that holds the security descriptor entries.
 *
 * @author Luke Quinane
 */
public class SecurityDescriptorStream {

    /**
     * The size of the blocks that the security descriptor entries are laid out in. No entry crosses one of these
     * boundaries; the remainder of a block is padded with zeros.
     */
    private static final long BLOCK_SIZE = 0x40000;

    /**
     * The size of the fixed part of an entry: hash, security id, offset and size.
     */
    private static final int HEADER_SIZE = 0x14;

    /**
     * The stream that holds the security descriptors.
     */
    private final NTFSFile.StreamFile sdsFile;

    /**
     * The list of entries in the stream.
     */
    private List<SecurityDescriptorStreamEntry> entries;

    /**
     * Creates a new instance.
     *
     * @param sdsFile the stream that holds the security descriptors.
     */
    public SecurityDescriptorStream(NTFSFile.StreamFile sdsFile) {
        this.sdsFile = sdsFile;
    }

    /**
     * Gets the security descriptor stream entries.
     *
     * @return the list of stream entries.
     * @throws java.io.IOException if an error occurs reading the entries.
     */
    public List<SecurityDescriptorStreamEntry> getEntries() throws IOException {
        if (entries == null) {
            entries = new ArrayList<SecurityDescriptorStreamEntry>();
            long offset = 0;
            long streamLength = sdsFile.getLength();

            while (offset < streamLength) {
                SecurityDescriptorStreamEntry entry = readOneEntry(offset);

                if (entry == null) {
                    // No entry crosses a 256 KiB boundary, so the stream is padded with zeros up to the next one.
                    // Skip the padding and carry on, rather than treating it as the end of the stream.
                    long nextBlock = (offset / BLOCK_SIZE + 1) * BLOCK_SIZE;

                    if (nextBlock >= streamLength) {
                        break;
                    }

                    offset = nextBlock;
                    continue;
                }

                entries.add(entry);
                offset += entry.getLength();
            }
        }

        return entries;
    }

    /**
     * Reads in a single stream entry.
     *
     * @param offset the offset to read from.
     * @return the entry or {@code null} if the end of the entries is reached.
     * @throws java.io.IOException if an error occurs reading the entry.
     */
    public SecurityDescriptorStreamEntry readOneEntry(long offset) throws IOException {
        long streamLength = sdsFile.getLength();

        // Not enough room left for the fixed part of an entry header
        if (offset + HEADER_SIZE > streamLength) {
            return null;
        }

        // First read in the size of the entry
        byte[] sizeBuffer = new byte[0x4];
        sdsFile.read(offset + 0x10, ByteBuffer.wrap(sizeBuffer));
        int size = LittleEndian.getInt32(sizeBuffer, 0);

        // A zero size is the padding at the end of a block. A size that is negative, smaller than the header or
        // longer than what is left of the stream means the entry is not usable either.
        if (size <= HEADER_SIZE || offset + size > streamLength) {
            return null;
        }

        // Read in the entire entry
        byte[] buffer = new byte[size];
        sdsFile.read(offset, ByteBuffer.wrap(buffer));
        return new SecurityDescriptorStreamEntry(buffer);
    }
}
