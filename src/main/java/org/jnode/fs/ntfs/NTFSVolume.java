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
 
package org.jnode.fs.ntfs;

import java.io.IOException;
import java.nio.ByteBuffer;

import lombok.Getter;
import org.jnode.driver.block.BlockDeviceAPI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Chira
 */
public class NTFSVolume {

    private static final Logger log = LoggerFactory.getLogger(NTFSVolume.class);

    public static final byte LONG_FILE_NAMES = 0x01;

    public static final byte DOS_8_3 = 0x02;

    /**
     * The current name space.
     */
    @Getter
    private byte currentNameSpace = LONG_FILE_NAMES;

    private final BlockDeviceAPI api;

    /**
     * The size of a cluster.
     */
    @Getter
    private final int clusterSize;

    private final BootRecord bootRecord;

    private MasterFileTable mftFileRecord;

    private FileRecord rootDirectory;

    /**
     * Initialize this instance.
     */
    public NTFSVolume(BlockDeviceAPI api) throws IOException {
        // I hope this is enaugh..should be
        this.api = api;

        // Read the boot sector
        final ByteBuffer buffer = ByteBuffer.allocate(512);
        api.read(0, buffer);
        this.bootRecord = new BootRecord(buffer.array());
        this.clusterSize = bootRecord.getClusterSize();
    }

    /**
     * @return Returns the bootRecord.
     */
    public final BootRecord getBootRecord() {
        return bootRecord;
    }

    /**
     * Read a single cluster.
     *
     * @param cluster
     */
    public void readCluster(long cluster, byte[] dst, int dstOffset) throws IOException {
        final int clusterSize = getClusterSize();
        final long clusterOffset = cluster * clusterSize;
        if (log.isDebugEnabled()) {
            log.debug("readCluster({}) {}", cluster, readClusterCount++);
        }
        api.read(clusterOffset, ByteBuffer.wrap(dst, dstOffset, clusterSize));
    }

    private int readClusterCount;
    private int readClustersCount;

    /**
     * Read a number of clusters.
     *
     * @param firstCluster
     * @param nrClusters   The number of clusters to read.
     * @param dst          Must have space for (nrClusters * getClusterSize())
     * @param dstOffset
     * @throws IOException
     */
    public void readClusters(long firstCluster, byte[] dst, int dstOffset, int nrClusters) throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("readClusters({}, {}) {}", firstCluster, nrClusters, readClustersCount++);
        }
        final int clusterSize = getClusterSize();
        final long clusterOffset = firstCluster * clusterSize;
        api.read(clusterOffset, ByteBuffer.wrap(dst, dstOffset, nrClusters * clusterSize));
    }

    /**
     * Gets the MFT.
     *
     * @return Returns the mTFRecord.
     */
    public MasterFileTable getMFT() throws IOException {
        if (mftFileRecord == null) {
            final BootRecord bootRecord = getBootRecord();
            final int bytesPerFileRecord = bootRecord.getFileRecordSize();
            final int clusterSize = getClusterSize();

            final int nrClusters;
            if (bytesPerFileRecord < clusterSize) {
                nrClusters = 1;
            } else {
                nrClusters = (bytesPerFileRecord + clusterSize - 1) / clusterSize;
            }
            final byte[] data = new byte[nrClusters * clusterSize];
            readClusters(bootRecord.getMftLcn(), data, 0, nrClusters);
            mftFileRecord = new MasterFileTable(this, data, 0);
            mftFileRecord.checkIfValid();
        }
        return mftFileRecord;

    }

    /**
     * Gets the root directory on this volume.
     *
     * @return the root directory record
     * @throws IOException
     */
    public FileRecord getRootDirectory() throws IOException {
        if (rootDirectory == null) {
            // Read the root directory
            final MasterFileTable mft = getMFT();
            rootDirectory = mft.getRecord(MasterFileTable.SystemFiles.ROOT);
            // found that the parent in the file name attribute in the root directory entry
            // referred to the root directory entry itself.
            log.info("getRootDirectory: " + rootDirectory.getFileName(rootDirectory.getReferenceNumber()));
        }
        return rootDirectory;
    }
}
