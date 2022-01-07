package org.jnode.fs.xfs.extent;

import org.jnode.fs.xfs.XfsFileSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class DataExtentOffsetManager {

    private final List<DataExtent> extents;
    private final List<ExtentOffsetLimitData> limits;
    private final XfsFileSystem fs;
    private final long blockSize;

    public DataExtentOffsetManager(List<DataExtent> extents, XfsFileSystem fs) {
        this.extents = extents;
        this.fs = fs;
        this.blockSize = fs.getSuperblock().getBlockSize();

        limits = new ArrayList<>(extents.size());
        long accumulator = 0;
        for (DataExtent extent : extents) {
            final long extentSize = extent.getBlockCount() * blockSize;
            limits.add(new ExtentOffsetLimitData(accumulator, extentSize + accumulator, extent));
            accumulator += extentSize;
        }
    }

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

        public ExtentOffsetLimitData(long start, long end, DataExtent extent) {
            this.start = start;
            this.end = end;
            this.extent = extent;
        }

        public long getStart() {
            return start;
        }

        public long getEnd() {
            return end;
        }

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
