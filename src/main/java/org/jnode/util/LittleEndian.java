/*
 * $Id$
 *
 * Copyright (C) 2003-2015 JNode.org
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation; either version 2.1 of the License, or
 * (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful, but 
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public 
 * License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; If not, write to the Free Software Foundation, Inc., 
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
 
package org.jnode.util;


/**
 * Little endian (LSB first) conversion methods.
 *
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 */
public class LittleEndian {

    /**
     * Prevent instantiation.
     */
    private LittleEndian() {
    }

    /**
     * Gets an 8-bit unsigned integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 8-bit unsigned value.
     */
    public static int getUInt8(byte[] src, int offset) {
        return src[offset] & 0xFF;
    }

    /**
     * Gets an 8-bit signed integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 8-bit signed value.
     */
    public static int getInt8(byte[] src, int offset) {
        return src[offset];
    }

    /**
     * Gets a 16-bit unsigned integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 16-bit unsigned value.
     */
    public static int getUInt16(byte[] src, int offset) {
        final int v0 = src[offset] & 0xFF;
        final int v1 = src[offset + 1] & 0xFF;
        return ((v1 << 8) | v0);
    }

    /**
     * Gets a 16-bit signed integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 16-bit signed value.
     */
    public static int getInt16(byte[] src, int offset) {
        final int v0 = src[offset] & 0xFF;
        final int v1 = src[offset + 1] & 0xFF;
        return (short) ((v1 << 8) | v0);
    }

    /**
     * Gets a 24-bit unsigned integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 24-bit unsigned value.
     */
    public static int getUInt24(byte[] src, int offset) {
        final int v0 = src[offset] & 0xFF;
        final int v1 = src[offset + 1] & 0xFF;
        final int v2 = src[offset + 2] & 0xFF;
        return ((v2 << 16) | (v1 << 8) | v0);
    }

    /**
     * Gets a 24-bit signed integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 24-bit signed value.
     */
    public static int getInt24(byte[] src, int offset) {
        int result = getUInt24(src, offset) << 8;
        return result >> 8;
    }

    /**
     * Gets a 32-bit unsigned integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 32-bit unsigned value.
     */
    public static long getUInt32(byte[] src, int offset) {
        final long v0 = src[offset] & 0xFF;
        final long v1 = src[offset + 1] & 0xFF;
        final long v2 = src[offset + 2] & 0xFF;
        final long v3 = src[offset + 3] & 0xFF;
        return ((v3 << 24) | (v2 << 16) | (v1 << 8) | v0);
    }

    /**
     * Gets a 32-bit signed integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 32-bit signed value.
     */
    public static int getInt32(byte[] src, int offset) {
        final int v0 = src[offset] & 0xFF;
        final int v1 = src[offset + 1] & 0xFF;
        final int v2 = src[offset + 2] & 0xFF;
        final int v3 = src[offset + 3] & 0xFF;
        return ((v3 << 24) | (v2 << 16) | (v1 << 8) | v0);
    }

    /**
     * Gets a 40-bit signed integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 40-bit signed value.
     */
    public static long getInt40(byte[] src, int offset) {
        long result = getUInt40(src, offset) << 24;
        return result >> 24;
    }

    /**
     * Gets a 40-bit unsigned integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 40-bit unsigned value.
     */
    public static long getUInt40(byte[] src, int offset) {
        final long v0 = src[offset] & 0xFF;
        final long v1 = src[offset + 1] & 0xFF;
        final long v2 = src[offset + 2] & 0xFF;
        final long v3 = src[offset + 3] & 0xFF;
        final long v4 = src[offset + 4] & 0xFF;
        return (v4 << 32) | (v3 << 24) | (v2 << 16) | (v1 << 8) | v0;
    }

    /**
     * Gets a 48-bit unsigned integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 48-bit unsigned value.
     */
    public static long getUInt48(byte[] src, int offset) {
        final long v0 = src[offset] & 0xFF;
        final long v1 = src[offset + 1] & 0xFF;
        final long v2 = src[offset + 2] & 0xFF;
        final long v3 = src[offset + 3] & 0xFF;
        final long v4 = src[offset + 4] & 0xFF;
        final long v5 = src[offset + 5] & 0xFF;
        return ((v5 << 40) | (v4 << 32) | (v3 << 24) | (v2 << 16) | (v1 << 8) | v0);
    }

    /**
     * Gets a 48-bit signed integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 48-bit signed value.
     */
    public static long getInt48(byte[] src, int offset) {
        long result = getUInt48(src, offset) << 16;
        return result >> 16;
    }

    /**
     * Gets a 56-bit signed integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 56-bit signed value.
     */
    public static long getInt56(byte[] src, int offset) {
        long result = getUInt56(src, offset) << 8;
        return result >> 8;
    }

    /**
     * Gets a 56-bit unsigned integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 56-bit unsigned value.
     */
    public static long getUInt56(byte[] src, int offset) {
        final long v0 = src[offset] & 0xFF;
        final long v1 = src[offset + 1] & 0xFF;
        final long v2 = src[offset + 2] & 0xFF;
        final long v3 = src[offset + 3] & 0xFF;
        final long v4 = src[offset + 4] & 0xFF;
        final long v5 = src[offset + 5] & 0xFF;
        final long v6 = src[offset + 6] & 0xFF;
        return ((v6 << 48) | (v5 << 40) | (v4 << 32) | (v3 << 24) | (v2 << 16) | (v1 << 8) | v0);
    }

    /**
     * Gets a 64-bit signed integer from the given byte array at the given offset.
     *
     * @param src the source byte array.
     * @param offset the offset in the byte array.
     * @return the 64-bit signed value.
     */
    public static long getInt64(byte[] src, int offset) {
        final long v0 = src[offset] & 0xFF;
        final long v1 = src[offset + 1] & 0xFF;
        final long v2 = src[offset + 2] & 0xFF;
        final long v3 = src[offset + 3] & 0xFF;
        final long v4 = src[offset + 4] & 0xFF;
        final long v5 = src[offset + 5] & 0xFF;
        final long v6 = src[offset + 6] & 0xFF;
        final long v7 = src[offset + 7] & 0xFF;
        return ((v7 << 56) | (v6 << 48) | (v5 << 40) | (v4 << 32) | (v3 << 24) | (v2 << 16) | (v1 << 8) | v0);
    }

    /**
     * Sets an 8-bit integer in the given byte array at the given offset.
     *
     * @param dst the destination byte array.
     * @param offset the offset to write to in the byte array.
     * @param value the value to write.
     */
    public static void setInt8(byte[] dst, int offset, int value) {
        dst[offset] = (byte) value;
    }

    /**
     * Sets a 16-bit integer in the given byte array at the given offset.
     *
     * @param dst the destination byte array.
     * @param offset the offset to write to in the byte array.
     * @param value the value to write.
     */
    public static void setInt16(byte[] dst, int offset, int value) {
        dst[offset] = (byte) (value & 0xFF);
        dst[offset + 1] = (byte) ((value >>> 8) & 0xFF);
    }

    /**
     * Sets a 24-bit integer in the given byte array at the given offset.
     *
     * @param dst the destination byte array.
     * @param offset the offset to write to in the byte array.
     * @param value the value to write.
     */
    public static void setInt24(byte[] dst, int offset, int value) {
        dst[offset] = (byte) (value & 0xFF);
        dst[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        dst[offset + 2] = (byte) ((value >>> 16) & 0xFF);
    }

    /**
     * Sets a 32-bit integer in the given byte array at the given offset.
     *
     * @param dst the destination byte array.
     * @param offset the offset to write to in the byte array.
     * @param value the value to write.
     */
    public static void setInt32(byte[] dst, int offset, int value) {
        dst[offset] = (byte) (value & 0xFF);
        dst[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        dst[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        dst[offset + 3] = (byte) ((value >>> 24) & 0xFF);
    }

    /**
     * Sets a 40-bit integer in the given byte array at the given offset.
     *
     * @param dst the destination byte array.
     * @param offset the offset to write to in the byte array.
     * @param value the value to write.
     */
    public static void setInt40(byte[] dst, int offset, long value) {
        dst[offset] = (byte) (value & 0xFF);
        dst[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        dst[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        dst[offset + 3] = (byte) ((value >>> 24) & 0xFF);
        dst[offset + 4] = (byte) ((value >>> 32) & 0xFF);
    }

    /**
     * Sets a 48-bit integer in the given byte array at the given offset.
     *
     * @param dst the destination byte array.
     * @param offset the offset to write to in the byte array.
     * @param value the value to write.
     */
    public static void setInt48(byte[] dst, int offset, long value) {
        dst[offset] = (byte) (value & 0xFF);
        dst[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        dst[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        dst[offset + 3] = (byte) ((value >>> 24) & 0xFF);
        dst[offset + 4] = (byte) ((value >>> 32) & 0xFF);
        dst[offset + 5] = (byte) ((value >>> 40) & 0xFF);
    }

    /**
     * Sets a 56-bit integer in the given byte array at the given offset.
     *
     * @param dst the destination byte array.
     * @param offset the offset to write to in the byte array.
     * @param value the value to write.
     */
    public static void setInt56(byte[] dst, int offset, long value) {
        dst[offset] = (byte) (value & 0xFF);
        dst[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        dst[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        dst[offset + 3] = (byte) ((value >>> 24) & 0xFF);
        dst[offset + 4] = (byte) ((value >>> 32) & 0xFF);
        dst[offset + 5] = (byte) ((value >>> 40) & 0xFF);
        dst[offset + 6] = (byte) ((value >>> 48) & 0xFF);
    }

    /**
     * Sets a 64-bit integer in the given byte array at the given offset.
     *
     * @param dst the destination byte array.
     * @param offset the offset to write to in the byte array.
     * @param value the value to write.
     */
    public static void setInt64(byte[] dst, int offset, long value) {
        dst[offset] = (byte) (value & 0xFF);
        dst[offset + 1] = (byte) ((value >>> 8) & 0xFF);
        dst[offset + 2] = (byte) ((value >>> 16) & 0xFF);
        dst[offset + 3] = (byte) ((value >>> 24) & 0xFF);
        dst[offset + 4] = (byte) ((value >>> 32) & 0xFF);
        dst[offset + 5] = (byte) ((value >>> 40) & 0xFF);
        dst[offset + 6] = (byte) ((value >>> 48) & 0xFF);
        dst[offset + 7] = (byte) ((value >>> 56) & 0xFF);
    }
}
