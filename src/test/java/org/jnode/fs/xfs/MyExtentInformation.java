package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;
import org.jnode.util.BigEndian;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class MyExtentInformation extends MyXfsBaseAccessor {

    private final long blockCount;
    private final long startOffset;
    private final long startBlock;

    private final long extentOffset;
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
    public MyExtentInformation(FSBlockDeviceAPI devApi, long superBlockStart,MyXfsFileSystem fs) throws IOException {
        super(devApi, superBlockStart,fs);
        final ByteBuffer extentBytes = ByteBuffer.allocate(16);
        devApi.read(superBlockStart,extentBytes);
        long value_128bit_upper = BigEndian.getInt64(extentBytes.array(), 0);
        long value_128bit_lower = BigEndian.getInt64(extentBytes.array(), 8);
        blockCount = value_128bit_lower & 0x1fffffL;
        value_128bit_lower = value_128bit_lower >>> 21;
        startBlock = value_128bit_lower | ( value_128bit_upper & 0x1ffL );
        value_128bit_upper = value_128bit_upper >>> 9;
        startOffset = value_128bit_upper & 0x3fffffffffffffL;
        extentOffset = calcExtentOffset();
    }

    public static long calcFsBlockOffset(long block,MyXfsFileSystem fs) throws IOException {
        final MySuperblock sb = fs.getMainSuperBlock();
        final long agSizeLog2 = sb.getAGSizeLog2();
        long allocationGroupIndex = block >> agSizeLog2;
        long relativeBlockNumber  = block & ( ( (long) 1 << agSizeLog2 ) - 1 );
        long allocationGroupBlockNumber = allocationGroupIndex * sb.getAGSize();
        return (allocationGroupBlockNumber + relativeBlockNumber) * sb.getBlockSize();

    }

    private long calcExtentOffset() throws IOException {
        return calcFsBlockOffset(startBlock,fs);
    }

    @Override
    protected List<Long> validSignatures() {
        return Collections.singletonList(0L);
    }

    @Override
    public long getSignature() throws IOException {
        return 0L;
    }

    public long getBlockCount() {
        return blockCount;
    }
    public long getStartOffset() {
        return startOffset;
    }
    public long getStartBlock() {
        return startBlock;
    }
    public long getState() throws IOException {
        return read(0,1);
    }

    public long getExtentOffset(){
        return extentOffset;
    }

    public void read(ByteBuffer buffer,int offset) throws IOException {
        devApi.read(extentOffset + offset,buffer);
    }

    @Override
    public String toString() {
        return "MyExtentInformation{" +
                "blockCount=" + blockCount +
                ", startOffset=" + startOffset +
                ", startBlock=" + startBlock +
                '}';
    }
}
