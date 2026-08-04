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
 
package org.jnode.fs.ntfs.index;

import org.jnode.fs.ntfs.NTFSStructure;



/**
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 */
final class IndexRoot extends NTFSStructure {

    public static final int SIZE = 0x10;

    /**
     * Initialize this instance.
     * @param attr
     */
    public IndexRoot(IndexRootAttribute attr) {
        super(attr, attr.getAttributeOffset());
    }

    /**
     * Gets the attribute type.
     * @return
     */
    public int getAttributeType() {
        return getUInt32AsInt(0x00);
    }

    /**
     * Gets the collation rule.
     * @return
     */
    public int getCollationRule() {
        return getUInt32AsInt(0x04);
    }

    /**
     * Size of each index block in bytes (in the index allocation attribute).
     * @return
     */
    public int getIndexBlockSize() {
        return getUInt32AsInt(0x08);
    }

    /**
     * Gets the number of cluster blocks per index record.
     *
     * <p>When the cluster is larger than the index block this is expressed in sectors rather than clusters, which
     * is how ntfs-3g and Windows write it, and it is always positive. The negative form documented for the volume
     * header ({@code 2^(-n)} bytes) has not been seen here on any volume: every image in the test corpus stores
     * index block size divided by whichever of the cluster or sector size is smaller.</p>
     *
     * @return the number of cluster blocks per index record.
     */
    public int getClustersPerIndexBlock() {
        final int v = getInt8(0x0C);
        if (v < 0) {
            // Fall back to treating the VCN unit as the whole index block. Log it, because if this ever fires the
            // encoding needs working out properly against a real volume.
            log.warn("Negative clusters per index block value: {}, falling back to 1", v);
            return 1;
        } else {
            return v;
        }
    }
}
