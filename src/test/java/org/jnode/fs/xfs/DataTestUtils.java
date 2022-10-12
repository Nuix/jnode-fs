package org.jnode.fs.xfs;

import java.io.IOException;

/**
 * Utilities for traversing data.
 */
public class DataTestUtils {
    public static XfsEntry getDescendantData(XfsFileSystem fs, String... names) throws IOException {
        XfsEntry descendantData = fs.getRootEntry();

        for (String descendantName : names) {
            descendantData = getChildData(descendantData, descendantName);
        }

        return descendantData;
    }

    public static XfsEntry getChildData(XfsEntry parent, String childName) throws IOException {
        return (XfsEntry) (new XfsDirectory(parent).getEntry(childName));
    }
}
