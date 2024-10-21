package org.jnode.fs.ntfs.datarun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

//The code translated from https://github.com/you0708/lznt1/blob/master/lznt1.py.
public class CompressedDataRunPyLznt1 {

    public static byte[] decompress(byte[] buf, boolean lengthCheck) throws IOException{
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int index = 0;

        while (index < buf.length) {
            // Read the header
            short header = ByteBuffer.wrap(buf, index, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
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

    private static byte[] decompressChunk(byte[] chunk) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int index = 0;

        while (index < chunk.length) {
            int flags = chunk[index] & 0xFF; // Get the flags byte
            index++;

            for (int i = 0; i < 8; i++) {
                if ((flags >> i & 1) == 0) {
                    // Copy single byte
                    out.write(chunk[index] & 0xFF);
                    index++;
                } else {
                    // Decompress using offset and length
                    short flag = ByteBuffer.wrap(chunk, index, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();
                    index += 2;

                    int pos = out.size() - 1;
                    int lMask = 0xFFF;
                    int oShift = 12;

                    while (pos >= 0x10) {
                        lMask >>= 1;
                        oShift--;
                        pos >>= 1;
                    }

                    int length = (flag & lMask) + 3;
                    int offset = (flag >> oShift) + 1;

                    if (length >= offset) {
                        byte[] tmp = new byte[0xFFF];
                        System.arraycopy(out.toByteArray(), out.size() - offset, tmp, 0, Math.min(offset, tmp.length));
                        out.write(tmp, 0, length);
                    } else {
                        for (int j = 0; j < length; j++) {
                            out.write(out.toByteArray()[out.size() - offset + j] & 0xFF);
                        }
                    }
                }

                if (index >= chunk.length) {
                    break;
                }
            }
        }

        return out.toByteArray();
    }
}