package org.jnode.fs.ntfs.attribute;

import org.jnode.fs.ntfs.FileRecord;
import org.jnode.fs.util.FSUtils;

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

                    long length = getFileRecord().getAttributeTotalSize(NTFSAttribute.Types.REPARSE_POINT, null);
                    byte[] tempBuffer = new byte[FSUtils.checkedCast(length)];
                    getFileRecord().readData(NTFSAttribute.Types.REPARSE_POINT, null, 0, tempBuffer, 0, (int) length, true);

                    // reset the data runs that contains the actual attribute data.
                    reset(tempBuffer, 0);

                    break;
                }
            }
        } catch (IOException e) {
            log.error("Failed to read stored attributes", e);
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