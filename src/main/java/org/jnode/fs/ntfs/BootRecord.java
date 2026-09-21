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

/**
 * @author Chira
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 *
 * @see <a href="https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#4-the-volume-header">NTFS volume header</a>
 */
public final class BootRecord extends NTFSStructure {

    private final String systemID;

    private final int bytesPerSector;

    private final int sectorsPerCluster;

    /**
     * The logical cluster number of the Master File Table.
     */
    private final long mftLcn;

    /**
     * The logical cluster number of the copy of the Master File Table.
     */
    private final long mftLcnMirror;

    private final int mediaDescriptor;

    private final int sectorsPerTrack;

    private final long totalSectors;

    /**
     * The volume serial number.
     */
    private final String serialNumber;

    /**
     * Size of a file record in bytes.
     */
    private final int fileRecordSize;

    /**
     * Size of an index record in bytes.
     */
    private final int indexRecordSize;

    /**
     * Size of a cluster in bytes.
     */
    private final int clusterSize;

    /**
     * Initialize this instance.
     *
     * @param buffer the byte buffer to base this instance from.
     * @throws IOException if the volume header does not describe a usable geometry.
     */
    public BootRecord(byte[] buffer) throws IOException {
        super(buffer, 0);
        this.systemID = new String(buffer, 0x03, 8);
        this.bytesPerSector = getUInt16(0x0B);
        this.sectorsPerCluster = decodeSectorsPerCluster(getUInt8(0x0D));
        this.mediaDescriptor = getUInt8(0x15);
        this.sectorsPerTrack = getUInt16(0x18);
        this.totalSectors = getInt64(0x28);
        this.mftLcn = getInt64(0x30);
        this.mftLcnMirror = getInt64(0x38);
        final int clustersPerMFTRecord = getInt8(0x40);
        final int clustersPerIndexRecord = getInt8(0x44);
        this.serialNumber = String.format("%02X%02X-%02X%02X", buffer[0x4b], buffer[0x4a], buffer[0x49], buffer[0x48]);

        this.clusterSize = sectorsPerCluster * bytesPerSector;
        this.fileRecordSize = calcByteSize(clustersPerMFTRecord);
        this.indexRecordSize = calcByteSize(clustersPerIndexRecord);

        // Both of these are divisors and array sizes further in, so a degenerate value has to be rejected here
        // rather than surfacing as a division by zero or an empty MFT buffer somewhere deep in the record code.
        if (clusterSize <= 0) {
            throw new IOException(String.format(
                "Invalid NTFS volume header: %d bytes per sector and %d sectors per cluster give a cluster size of %d",
                bytesPerSector, sectorsPerCluster, clusterSize));
        }

        if (fileRecordSize <= 0) {
            throw new IOException(
                "Invalid NTFS volume header: file record size is " + fileRecordSize + " (stored value " +
                    clustersPerMFTRecord + ")");
        }

        log.debug("ClusterSize     = {}", clusterSize);
        log.debug("FileRecordSize  = {}", fileRecordSize);
        log.debug("IndexRecordSize = {}", indexRecordSize);
        log.debug("TotalSectors    = {}", totalSectors);
    }

    /**
     * Gets the bytes per sector.
     *
     * @return the bytes per sector.
     */
    public int getBytesPerSector() {
        return this.bytesPerSector;
    }

    /**
     * Gets the media descriptor.
     *
     * @return the media descriptor.
     */
    public int getMediaDescriptor() {
        return this.mediaDescriptor;
    }

    /**
     * Gets the logical cluster number of the MFT.
     *
     * @return the logical cluster number of the MFT.
     */
    public long getMftLcn() {
        return mftLcn;
    }

    /**
     * Gets the logical cluster number of the MFT mirror.
     *
     * @return the logical cluster number of the MFT mirror.
     */
    public long getMftLcnMirror() {
        return mftLcnMirror;
    }

    /**
     * Gets the number of sectors per cluster.
     *
     * @return the number of sectors per cluster.
     */
    public int getSectorsPerCluster() {
        return this.sectorsPerCluster;
    }

    /**
     * Gets the number of sectors per track.
     *
     * @return the number of sectors per track.
     */
    public int getSectorsPerTrack() {
        return this.sectorsPerTrack;
    }

    /**
     * Gets the system ID.
     *
     * @return the system ID.
     */
    public String getSystemID() {
        return this.systemID;
    }

    /**
     * Gets the system serial number.
     *
     * @return the serial number.
     */
    public String getSerialNumber() {
        return this.serialNumber;
    }

    /**
     * Gets the total sectors.
     *
     * @return the total sectors.
     */
    public long getTotalSectors() {
        return this.totalSectors;
    }

    /**
     * Gets the size of a file record in bytes.
     *
     * @return the size of a file record in bytes.
     */
    public int getFileRecordSize() {
        return fileRecordSize;
    }

    /**
     * Gets the size of an index record in bytes.
     *
     * @return the size of an index record in bytes.
     */
    public int getIndexRecordSize() {
        return indexRecordSize;
    }

    /**
     * Gets the size of a cluster in bytes.
     *
     * @return the size of a cluster in bytes.
     */
    public int getClusterSize() {
        return clusterSize;
    }

    /**
     * Decodes the sectors per cluster block value stored at offset 0x0d.
     *
     * <p>Values 0 to 128 are the number of sectors directly. Values 244 to 255 represent {@code 2^(256-n)} sectors,
     * which is how cluster sizes above 128 sectors are stored - Windows 10 (1903) and mkntfs use this form for the
     * 128 KiB to 2 MiB cluster sizes. Other values are not defined.</p>
     *
     * @param raw the raw byte value.
     * @return the number of sectors per cluster block.
     */
    private static int decodeSectorsPerCluster(int raw) {
        if (raw <= 128) {
            return raw;
        }

        if (raw >= 244) {
            return 1 << (256 - raw);
        }

        log.warn("Undefined sectors per cluster block value: {}, using it as-is", raw);
        return raw;
    }

    /**
     * Converts an MFT entry or index entry size, as stored in the volume header, into bytes.
     *
     * <p>Values 0 to 127 are a number of cluster blocks. Values 128 to 255, i.e. negative when read as a signed
     * byte, represent {@code 2^(-n)} bytes.</p>
     *
     * @param clusters the raw signed byte value.
     * @return the size in bytes.
     */
    private int calcByteSize(int clusters) {
        if (clusters >= 0) {
            return clusters * clusterSize;
        } else {
            return (1 << -clusters);
        }
    }
}
