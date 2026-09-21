package org.jnode.fs.ntfs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.jnode.fs.ntfs.attribute.NTFSNonResidentAttribute;
import org.jnode.util.LittleEndian;

/**
 * Builders for the synthetic MFT records and attributes that the NTFS unit tests run against.
 *
 * <p>Attributes are built through a real {@link FileRecord} rather than straight off a buffer, so that they carry
 * the containing record that production code reads back out of them.</p>
 */
public final class NTFSTestRecords {

    /**
     * The offset of the first attribute within the records built here.
     */
    public static final int FIRST_ATTRIBUTE_OFFSET = 0x38;

    /**
     * The smallest record built here, which is also the usual size on a real volume.
     */
    private static final int MINIMUM_RECORD_SIZE = 1024;

    /**
     * The cluster size reported by the records built here.
     */
    private static final int CLUSTER_SIZE = 4096;

    private NTFSTestRecords() {
    }

    /**
     * Wraps a single attribute in an MFT record.
     *
     * @param attribute the attribute bytes, placed at {@link #FIRST_ATTRIBUTE_OFFSET} and followed by the end of
     *                  list marker.
     * @return the record.
     * @throws IOException if the record cannot be built.
     */
    public static FileRecord record(byte[] attribute) throws IOException {
        return record(null, CLUSTER_SIZE, attribute);
    }

    /**
     * Wraps a single attribute in an MFT record belonging to a volume, for tests that read the attribute's data.
     *
     * @param volume      the volume the record belongs to.
     * @param clusterSize the cluster size, which has to match the volume's.
     * @param attribute   the attribute bytes, placed at {@link #FIRST_ATTRIBUTE_OFFSET}.
     * @return the record.
     * @throws IOException if the record cannot be built.
     */
    public static FileRecord record(NTFSVolume volume, int clusterSize, byte[] attribute) throws IOException {
        int used = FIRST_ATTRIBUTE_OFFSET + attribute.length + 4;
        int size = Math.max(MINIMUM_RECORD_SIZE, (used + 511) / 512 * 512);
        byte[] buffer = new byte[size];

        System.arraycopy("FILE".getBytes(StandardCharsets.US_ASCII), 0, buffer, 0, 4);
        LittleEndian.setInt16(buffer, 0x04, 0x30);          // update sequence array offset
        LittleEndian.setInt16(buffer, 0x06, 1);             // no fix-ups, so an attribute can span whole sectors
        LittleEndian.setInt16(buffer, 0x14, FIRST_ATTRIBUTE_OFFSET);
        LittleEndian.setInt16(buffer, 0x16, 0x01);          // in use
        LittleEndian.setInt32(buffer, 0x18, used);          // used entry size
        LittleEndian.setInt32(buffer, 0x1c, size);          // allocated entry size

        System.arraycopy(attribute, 0, buffer, FIRST_ATTRIBUTE_OFFSET, attribute.length);
        LittleEndian.setInt32(buffer, FIRST_ATTRIBUTE_OFFSET + attribute.length, 0xFFFFFFFF);

        return new FileRecord(volume, clusterSize, false, 1, buffer, 0);
    }

    /**
     * Builds a single attribute, wrapped in an MFT record.
     *
     * @param attribute the attribute bytes.
     * @return the attribute.
     * @throws IOException if the record cannot be built.
     */
    public static NTFSAttribute attribute(byte[] attribute) throws IOException {
        return NTFSAttribute.getAttribute(record(attribute), FIRST_ATTRIBUTE_OFFSET);
    }

    /**
     * Builds a non-resident $DATA attribute header.
     *
     * @param firstVcn the value for offset 0x10.
     * @param lastVcn  the value for offset 0x18.
     * @return the attribute.
     */
    public static byte[] nonResidentAttribute(long firstVcn, long lastVcn) {
        return nonResidentAttribute(firstVcn, lastVcn, 0, 0, new byte[0]);
    }

    /**
     * Builds a non-resident $DATA attribute header with data runs.
     *
     * @param firstVcn        the value for offset 0x10.
     * @param lastVcn         the value for offset 0x18.
     * @param flags           the attribute data flags for offset 0x0c.
     * @param compressionUnit the stored compression unit size for offset 0x22.
     * @param dataRuns        the runlist to place at offset 0x40.
     * @return the attribute.
     */
    public static byte[] nonResidentAttribute(long firstVcn, long lastVcn, int flags, int compressionUnit,
                                              byte[] dataRuns) {
        return nonResidentAttribute(firstVcn, lastVcn, flags, compressionUnit, dataRuns, 0x1000, 0x1000, 0x1000);
    }

    /**
     * Builds a non-resident $DATA attribute header with data runs and explicit sizes.
     *
     * @param firstVcn         the value for offset 0x10.
     * @param lastVcn          the value for offset 0x18.
     * @param flags            the attribute data flags for offset 0x0c.
     * @param compressionUnit  the stored compression unit size for offset 0x22.
     * @param dataRuns         the runlist to place at offset 0x40.
     * @param allocatedSize    the allocated size for offset 0x28.
     * @param dataSize         the data size for offset 0x30.
     * @param initialisedSize  the initialised size, a.k.a. the valid data length, for offset 0x38.
     * @return the attribute.
     */
    public static byte[] nonResidentAttribute(long firstVcn, long lastVcn, int flags, int compressionUnit,
                                              byte[] dataRuns, long allocatedSize, long dataSize,
                                              long initialisedSize) {
        byte[] buffer = new byte[0x40 + Math.max(dataRuns.length, 0x10)];
        LittleEndian.setInt32(buffer, 0x00, 0x80);      // $DATA
        LittleEndian.setInt32(buffer, 0x04, buffer.length);
        buffer[0x08] = 1;                               // non-resident
        LittleEndian.setInt16(buffer, 0x0a, 0x40);      // name offset
        LittleEndian.setInt16(buffer, 0x0c, flags);
        LittleEndian.setInt64(buffer, 0x10, firstVcn);
        LittleEndian.setInt64(buffer, 0x18, lastVcn);
        LittleEndian.setInt16(buffer, 0x20, 0x40);      // data runs offset
        LittleEndian.setInt16(buffer, 0x22, compressionUnit);
        LittleEndian.setInt64(buffer, 0x28, allocatedSize);
        LittleEndian.setInt64(buffer, 0x30, dataSize);
        LittleEndian.setInt64(buffer, 0x38, initialisedSize);
        System.arraycopy(dataRuns, 0, buffer, 0x40, dataRuns.length);
        return buffer;
    }

    /**
     * Builds a non-resident $DATA attribute and returns it wrapped in an MFT record.
     *
     * @param firstVcn        the value for offset 0x10.
     * @param lastVcn         the value for offset 0x18.
     * @param flags           the attribute data flags for offset 0x0c.
     * @param compressionUnit the stored compression unit size for offset 0x22.
     * @param dataRuns        the runlist to place at offset 0x40.
     * @return the attribute.
     * @throws IOException if the record cannot be built.
     */
    public static NTFSNonResidentAttribute nonResident(long firstVcn, long lastVcn, int flags, int compressionUnit,
                                                      byte[] dataRuns) throws IOException {
        return (NTFSNonResidentAttribute)
            attribute(nonResidentAttribute(firstVcn, lastVcn, flags, compressionUnit, dataRuns));
    }
}
