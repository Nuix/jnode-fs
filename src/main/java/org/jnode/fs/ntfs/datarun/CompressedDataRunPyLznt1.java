package org.jnode.fs.ntfs.datarun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.jnode.util.LittleEndian;

//The code translated from https://github.com/you0708/lznt1/blob/master/lznt1.py.
public class CompressedDataRunPyLznt1 {

    public static byte[] decompress(byte[] buf, boolean lengthCheck) throws IOException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int index = 0;

        while (index < buf.length) {
            // Read the header
            // DO NOT use short here, as it is an unsigned int.
            int header = LittleEndian.getUInt16(buf, index);
            index += 2;

            int length = (header & 0xFFF) + 1;
            if (lengthCheck && length > buf.length - index) {
                throw new IllegalArgumentException("invalid chunk length");
            }

            byte[] chunk = new byte[length];
            System.arraycopy(buf, index, chunk, 0, length);
            index += length;

            if ((header & 0x8000) != 0) {
                out.write(decompressChunk(chunk));
            } else {
                out.write(chunk);
            }
        }

        return out.toByteArray();
    }

    private static byte[] decompressChunk(byte[] compressed) {
        byte[] uncompressed = new byte[0x1000];

        int index = 0;
        int uIndex = 0;

        while (index < compressed.length) {
            int flags = compressed[index] & 0xFF; // Get the flags byte
            index++;

            for (int i = 0; i < 8; i++) {
                if ((flags >> i & 1) == 0) {
                    // Copy single byte
                    uncompressed[uIndex++] = compressed[index++];
                } else {
                    // Decompress using offset and length
                    // DO NOT use short here, as it is an unsigned int.
                    int flag = LittleEndian.getUInt16(compressed, index);
                    index += 2;

                    int pos = uIndex - 1;
                    int lMask = 0xFFF;
                    int oShift = 12;

                    while (pos >= 0x10) {
                        lMask >>= 1;
                        oShift--;
                        pos >>= 1;
                    }

                    int length = (flag & lMask) + 3;
                    int offset = (flag >> oShift) + 1;

                    for (int j = 0; j < length; j++) {
                        uncompressed[uIndex] = uncompressed[uIndex - offset];
                        uIndex++;
                    }
                }

                if (index >= compressed.length) {
                    break;
                }
            }
        }

        byte[] result = new byte[uIndex];
        System.arraycopy(uncompressed, 0, result, 0, uIndex);

        return result;
    }
}