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
 
package org.jnode.fs.hfsplus.catalog;

import org.jnode.fs.hfsplus.tree.AbstractIndexNode;
import org.jnode.fs.hfsplus.tree.NodeDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CatalogIndexNode extends AbstractIndexNode<CatalogKey> {
    private static final Logger log = LoggerFactory.getLogger(CatalogIndexNode.class);

    /**
     * Create a new node.
     *
     * @param descriptor
     * @param nodeSize
     */
    public CatalogIndexNode(NodeDescriptor descriptor, final int nodeSize) {
        super(descriptor, nodeSize);
    }

    /**
     * Create node from existing data.
     *
     * @param nodeData
     * @param nodeSize
     */
    public CatalogIndexNode(final byte[] nodeData, final int nodeSize) {
        super(nodeData, nodeSize);

    }

    @Override
    protected CatalogKey createKey(byte[] nodeData, int offset) {
        return new CatalogKey(nodeData, offset);
    }
}
