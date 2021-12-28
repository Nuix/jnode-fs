package org.jnode.fs.xfs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MyExtentOffsetManager {

    private final List<MyExtentInformation> extents;
    private final List<ExtentOffsetLimitData> limits;
    private final MyXfsFileSystem fs;
    private final long blockSize;

    public MyExtentOffsetManager(List<MyExtentInformation> extents, MyXfsFileSystem fs) {
        this.extents = extents;
        this.fs = fs;
        this.blockSize = fs.getBlockSize();

        limits = new ArrayList<>(extents.size());
        long accumulator = 0;
        for (MyExtentInformation extent : extents) {
            final long extentSize = extent.getBlockCount() * blockSize;
            limits.add(new ExtentOffsetLimitData(accumulator, extentSize + accumulator, extent));
        }
    }

    public ExtentOffsetLimitData getExtentDataForOffset(long offset){
        final Optional<ExtentOffsetLimitData> limitData = limits.stream()
                .filter(d -> d.start <= offset && d.end > offset)
                .findFirst();
        return limitData.orElseThrow(() -> new RuntimeException("Offset out of bounds"));
    }

    public static class ExtentOffsetLimitData {
        private final long start;
        private final long end;
        private final MyExtentInformation extent;

        public ExtentOffsetLimitData(long start, long end, MyExtentInformation extent) {
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

        public MyExtentInformation getExtent() {
            return extent;
        }
    }
}
