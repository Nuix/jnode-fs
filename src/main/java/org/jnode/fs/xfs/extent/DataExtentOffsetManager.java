package org.jnode.fs.xfs.extent;

import org.jnode.fs.xfs.XfsFileSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Data extent offset manager
 * Provides the infrastructure to handle list of extents
 *
 * @author
 */
public class DataExtentOffsetManager {

    /**
     * The list of extents found in the block.
     */
    private final List<DataExtent> extents;

    /**
     * The list of extent's limits.
     */
    private final List<ExtentOffsetLimitData> limits;

    /**
     * The filesystem.
     */
    private final XfsFileSystem fs;

    /**
     * The filesystem block size.
     */
    private final long blockSize;


    /**
     * Creates a DataExtentOffsetManager.
     *
     * @param extents of the block.
     * @param fileSystem of the image
     *
     */
    public DataExtentOffsetManager(List<DataExtent> extents, XfsFileSystem fileSystem) {
        this.extents = extents;
        this.fs = fileSystem;
        this.blockSize = fs.getSuperblock().getBlockSize();

        limits = new ArrayList<>(extents.size());
        long accumulator = 0;
        for (DataExtent extent : extents) {
            final long extentSize = extent.getBlockCount() * blockSize;
            limits.add(new ExtentOffsetLimitData(accumulator, extentSize + accumulator, extent));
            accumulator += extentSize;
        }
    }

    /**
     * Gets the extent data for the current offset.
     *
     * @param offset of the data.
     * @return a extent entity with the limits of the data
     */
    public ExtentOffsetLimitData getExtentDataForOffset(long offset){
        final Optional<ExtentOffsetLimitData> limitData = limits.stream()
                .filter(d -> d.start <= offset && d.end > offset)
                .findFirst();
        return limitData.orElse(null);
    }

    public static class ExtentOffsetLimitData {
        private final long start;
        private final long end;
        private final DataExtent extent;

        /**
         * Creates a DataExtentOffsetManager.
         *
         * @param start of the data.
         * @param end of the data
         * @param extent of the data.
         *
         */
        public ExtentOffsetLimitData(long start, long end, DataExtent extent) {
            this.start = start;
            this.end = end;
            this.extent = extent;
        }

        /**
         * Gets the start of the block extent data
         *
         * @return a start offset of the data.
         */
        public long getStart() {
            return start;
        }

        /**
         * Gets the end of the block extent data
         *
         * @return a end offset of the data
         */
        public long getEnd() {
            return end;
        }

        /**
         * Get the extent block of data
         *
         * @return a extent data
         */
        public DataExtent getExtent() {
            return extent;
        }

        @Override
        public String toString() {
            return "ExtentOffsetLimitData{" +
                    "start=" + start +
                    ", end=" + end +
                    ", extent=" + extent +
                    '}';
        }
    }
}
