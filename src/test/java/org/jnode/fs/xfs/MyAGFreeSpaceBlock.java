package org.jnode.fs.xfs;

import org.jnode.driver.block.FSBlockDeviceAPI;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MyAGFreeSpaceBlock extends MyXfsBaseAccessor{

        public static final long MAGIC_NUMBER = AsciiToHex("XAGF");

        public MyAGFreeSpaceBlock(FSBlockDeviceAPI devApi, long freeBlockStart) {
            super(devApi, freeBlockStart);
        }

        @Override
        public boolean isValidSignature() throws IOException {
            return getSignature() == MAGIC_NUMBER;
        }

    enum SecondaryFeatureFlags {
        //Unknown (reserved)
        RESERVED1BIT(0x00000001),
        //Has lazy global counters
        //Free space and inode values are only tracked in the primary superblock
        LAZYSBCOUNTBIT(0x00000002),
        //Unknown (reserved)
        RESERVED4BIT(0x00000004),
        //Version 2 extended attributes are used
        ATTR2BIT(0x00000008),
        //Inodes have a parent pointer
        PARENTBIT(0x00000010),
        //Has 32-bit project identifiers
        PROJID32BIT(0x00000080),
        //Has metadata checksums
        CRCBIT(0x00000100),
        //Directory entries contain a file type
        FTYPE(0x00000200);

        private final int value;

        SecondaryFeatureFlags(int val) {
            this.value = val;
        }

        public static List<MyAGFreeSpaceBlock.SecondaryFeatureFlags> getFlags(long flagsBinary) {
            return Arrays.stream(MyAGFreeSpaceBlock.SecondaryFeatureFlags.values())
                    .filter(flag -> (flag.value & flagsBinary) > 0)
                    .collect(Collectors.toList());
        }

    }

        public String getXfsDbInspectionString() throws IOException {
            String str = "";

            str += "Magicnum = " + Long.toOctalString(getMagicNum()) + "\n";
            str += "Version = " + getversion() + "\n";
            str += "Sequence number = " + getSequence_Number() + "\n";
            str += "Allocation group size = " + getAG_Size() + "\n";
            str += "Free space counts B+ tree root block number = " + getcountsBplustree() + "\n";
            str += "Free space sizes B+ tree root block number = " + getsizeBplustree() + "\n";
            str += "Reserved = " + getReserved() + "\n";
            str += "Free space counts B+ tree height/depth = " + getFScountsBplustree() + "\n";
            str += "Free space size B+ tree height/depth = " + getFSsizeBplustree() + "\n";
            str += "Reserved = " + getReserved2() + "\n";
            str += "Index of the first free list block = " + getIndex_first_flb() + "\n";
            str += "Index of the last free list block = " + getIndex_last_flb() + "\n";
            str += "Free list size = " + getFLSize() + "\n";
            str += "Number of free blocks in the allocation group = " + get_fb_number() + "\n";
            str += "Longest contiguous free space in the allocation group = " + getAGFS() + "\n";

            return str;
        }

        public long getMagicNum() throws IOException {
            return read(256, 4);
        }

        public long getversion() throws IOException {
            return read(260, 4);
        }

        public long getSequence_Number() throws IOException {
            return read(265, 4);
        }

        public long getAG_Size() throws IOException {
            return read(269, 4);
        }

        public long getcountsBplustree() throws IOException {
            return read(273, 4);
        }

        public long getsizeBplustree() throws IOException {
            return read(277, 4);
        }

        public long getReserved() throws IOException {
            return read(281, 4);
        }

        public long getFScountsBplustree() throws IOException {
            return read(285, 4);
        }

        public long getFSsizeBplustree() throws IOException {
            return read(289, 4);
        }

        public long getReserved2() throws IOException {
            return read(293, 4);
        }

        public long getIndex_first_flb() throws IOException {
            return read(297, 4);
        }

        public long getIndex_last_flb() throws IOException {
            return read(301, 4);
        }

        public long getFLSize() throws IOException {
            return read(305, 4);
        }

        public long get_fb_number() throws IOException {
            return read(309, 4);
        }

        public long getAGFS() throws IOException {
            return read(313, 4);
        }
}
