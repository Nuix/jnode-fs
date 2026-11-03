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
 
package org.jnode.fs.ntfs.usnjrnl;

import java.util.Date;
import org.jnode.fs.ntfs.NTFSStructure;
import org.jnode.fs.ntfs.NTFSUTIL;

/**
 * A v2 USN record entry in the USN journal file ($Extend\$UsnJrnl).
 *
 * @author Luke Quinane
 */
public class UsnRecordV2 extends NTFSStructure implements UsnRecordV2V3<Long> {

    /**
     * The length of the fixed part of the record; the file name starts after it.
     */
    private static final int FIXED_HEADER_LENGTH = 0x3c;

    /**
     * Creates a new journal entry at the given offset.
     *
     * @param buffer the buffer containing the journal data.
     * @param offset the offset in the buffer to read from.
     */
    public UsnRecordV2(byte[] buffer, int offset) {
        super(buffer, offset);
    }

    @Override
    public long getSize() {
        return getUInt32(0x0);
    }

    @Override
    public int getMajorVersion() {
        return getUInt16(0x4);
    }

    @Override
    public int getMinorVersion() {
        return getUInt16(0x6);
    }

    @Override
    public Long getMftReference() {
        return getInt48(0x8);
    }

    @Override
    public Long getParentMtfReference() {
        return getInt48(0x10);
    }

    @Override
    public long getUsn() {
        return getInt64(0x18);
    }

    @Override
    public long getTimestamp() {
        return NTFSUTIL.filetimeToMillis(getInt64(0x20));
    }

    @Override
    public long getReason() {
        return getUInt32(0x28);
    }

    @Override
    public int getSourceInfo() {
        return getInt32(0x2c);
    }

    @Override
    public int getSecurityId() {
        return getInt32(0x30);
    }

    @Override
    public int getFileAttributes() {
        return getInt32(0x34);
    }

    @Override
    public int getFileNameSize() {
        return getUInt16(0x38);
    }

    /**
     * Gets the offset of the file name, relative to the start of the record.
     *
     * @return the offset.
     */
    public int getFileNameOffset() {
        return getUInt16(0x3a);
    }

    @Override
    public String getFileName() {
        return UsnJournal.readFileName(this, getSize(), FIXED_HEADER_LENGTH, getFileNameOffset(), getFileNameSize());
    }

    @Override
    public String toString() {
        return String.format("MFT: 0x%x parent MFT: 0x%x, %s version: %d.%d, size: %d source: 0x%x security: 0x%x "
            + "attributes: %s time: %s, name:%s", getMftReference(), getParentMtfReference(),
            UsnJournal.Reason.lookupReasons(getReason()), getMajorVersion(), getMinorVersion(), getSize(), getSourceInfo(),
            getSecurityId(), UsnJournal.FileAttribute.lookupAttributes(getFileAttributes()), new Date(getTimestamp()),
            getFileName());
    }
}
