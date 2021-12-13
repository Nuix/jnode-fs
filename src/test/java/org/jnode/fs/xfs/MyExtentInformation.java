package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.util.BigEndian;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class MyExtentInformation extends MyXfsBaseAccessor {

    private final long blockCount;
    private final long startOffset;
    private final long startBlock;
    private static final int EXTENT_BIT_LENGTH = 128;
    private static final int BLOCK_COUNT_BIT_LENGTH = 21;
    private static final int START_OFFSET_BIT_LENGTH = 52;
    private static final int START_BLOCK_BIT_LENGTH = 54;

    private static String byteArrToBinary(byte[] arr){
        StringBuilder sb = new StringBuilder(arr.length * 8);
        for (byte b :arr){
            sb.append(byteToBinary(b));
        }
        return sb.toString();
    }

    private static String byteToBinary(byte b){
        return String.format("%8s",Integer.toString(b,2)).replace(" ","0");
    }

    // Reference for implementation https://github.com/libyal/libfsxfs/blob/main/libfsxfs/libfsxfs_extent.c
    // https://mirrors.edge.kernel.org/pub/linux/utils/fs/xfs/docs/xfs_filesystem_structure.pdf page 115
    public MyExtentInformation(FSBlockDeviceAPI devApi, long superBlockStart) throws IOException {
        super(devApi, superBlockStart);
        final ByteBuffer extentBytes = ByteBuffer.allocate(16);
        devApi.read(superBlockStart,extentBytes);
        long value_128bit_upper = BigEndian.getInt64(extentBytes.array(), 0);
        long value_128bit_lower = BigEndian.getInt64(extentBytes.array(), 8);
        blockCount = value_128bit_lower & 0x1fffffL;
        value_128bit_lower = value_128bit_lower >>> 21;
        startBlock = value_128bit_lower | ( value_128bit_upper & 0x1ffL );
        value_128bit_upper = value_128bit_upper >>> 9;
        startOffset = value_128bit_upper & 0x3fffffffffffffL;
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    public long getBlockCount() throws IOException {
        return blockCount;
    }
    public long getStartOffset() throws IOException {
        return startOffset;
    }
    public long getStartBlock() throws IOException {
        return startBlock;
    }
    public long getState() throws IOException {
        return read(0,1);
    }

}
