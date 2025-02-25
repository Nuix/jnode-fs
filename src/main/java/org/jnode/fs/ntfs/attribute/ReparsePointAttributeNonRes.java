package org.jnode.fs.ntfs.attribute;

import org.jnode.fs.ntfs.FileRecord;
import org.jnode.fs.ntfs.datarun.DataRunInterface;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A non-resident NTFS reparse point (symbolic link).
 *
 * @author Luke Quinane
 */
public class ReparsePointAttributeNonRes extends NTFSNonResidentAttribute implements ReparsePointAttribute {

    /**
     * Whether the non-resident cluster, which contains the actual data, has been read out.
     */
    AtomicBoolean isActualClusterReadOut = new AtomicBoolean(false);

    /**
     * Constructs the attribute.
     *
     * @param fileRecord the containing file record.
     * @param offset     offset of the attribute within the file record.
     */
    public ReparsePointAttributeNonRes(FileRecord fileRecord, int offset) {
        super(fileRecord, offset);
    }

    private void readActualCluster() {
        if (isActualClusterReadOut.get()) {
            return;
        }

        try {
            List<NTFSAttribute> ntfsAttributes = getFileRecord().readStoredAttributes();
            for (NTFSAttribute ntfsAttribute : ntfsAttributes) {
                if (ntfsAttribute instanceof ReparsePointAttributeNonRes) {
                    ReparsePointAttributeNonRes attribute = (ReparsePointAttributeNonRes) ntfsAttribute;
                    List<DataRunInterface> attributeDataRuns = attribute.getDataRunDecoder().getDataRuns();

                    // Calculate the total number of clusters of the data runs.
                    int totalClusterCount = 0;
                    for (DataRunInterface attributeDataRun : attributeDataRuns) {
                        totalClusterCount += (int) attributeDataRun.getLength();
                    }
                    final byte[] data = new byte[totalClusterCount * getFileRecord().getClusterSize()];
                    int index = 0;

                    // Read the data runs.
                    for (DataRunInterface attributeDataRun : attributeDataRuns) {
                        try {
                            int readClusterNumber = readVCN(attributeDataRun.getFirstVcn(), data, index, (int) attributeDataRun.getLength());
                            index += readClusterNumber * getFileRecord().getClusterSize();
                        } catch (IOException e) {
                            log.error("Failed to read data run", e);
                        }
                    }

                    // reset the data runs that contains the actual attribute data.
                    reset(data, 0);

                    break;
                }
            }
        } finally {
            isActualClusterReadOut.set(true);
        }
    }

    @Override
    public int getReparseTag() {
        readActualCluster(); // Lazy load the actual cluster.
        return getInt32(0);
    }

    @Override
    public int getReparseDataLength() {
        readActualCluster(); // Lazy load the actual cluster.
        return getUInt32AsInt(0x4);
    }
}