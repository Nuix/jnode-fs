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
 
package org.jnode.fs.ntfs.attribute;

import org.jetbrains.annotations.TestOnly;
import org.jnode.fs.ntfs.FileNameAttribute;
import org.jnode.fs.ntfs.FileRecord;
import org.jnode.fs.ntfs.NTFSStructure;
import org.jnode.fs.ntfs.StandardInformationAttribute;
import org.jnode.fs.ntfs.index.IndexAllocationAttribute;
import org.jnode.fs.ntfs.index.IndexRootAttribute;
import org.jnode.fs.util.FSUtils;

/**
 * @author Chira
 * @author Ewout Prangsma (epr@users.sourceforge.net)
 */
public abstract class NTFSAttribute extends NTFSStructure {

    /**
     * NTFS attribute types.
     *
     * @see <a href="https://docs.microsoft.com/en-us/openspecs/windows_protocols/ms-fscc/a82e9105-2405-4e37-b2c3-28c773902d85">NTFS Attribute Types</a>
     * @see <a href="https://github.com/tuxera/ntfs-3g/blob/a4a837025b6ac2b0c44c93e34e22535fe9e95b27/include/ntfs-3g/layout.h#L488">ntfs-3g</a>
     */
    public enum Types {

        STANDARD_INFORMATION(0x10),

        ATTRIBUTE_LIST(0x20),

        FILE_NAME(0x30),

        OBJECT_ID(0x40),

        SECURITY_DESCRIPTOR(0x50),

        VOLUME_NAME(0x60),

        VOLUME_INFORMATION(0x70),

        DATA(0x80),

        INDEX_ROOT(0x90),

        INDEX_ALLOCATION(0xA0),

        BITMAP(0xB0),

        REPARSE_POINT(0xC0),

        EA_INFORMATION(0xD0),

        EA(0xE0),

        PROPERTY_SET(0xF0),

        LOGGED_UTILITY_STREAM(0x100);

        private final int value;

        private Types(int value) {
            this.value = value;
        }

        public final int getValue() {
            return value;
        }

        public static Types fromValue(int value) {
            for (Types type : Types.values()) {
                if (type.getValue() == value) {
                    return type;
                }
            }

            return null;
        }
    }

    private final Types type;

    private final int flags;

    private final FileRecord fileRecord;

    /**
     * Initialize this instance.
     */
    public NTFSAttribute(FileRecord fileRecord, int offset) {
        super(fileRecord, offset);
        this.fileRecord = fileRecord;
        this.type = Types.fromValue(getUInt32AsInt(0));
        this.flags = getUInt16(0x0C);
    }

    @TestOnly
    public NTFSAttribute(NTFSStructure ntfsStructure, int offset) {
        super(ntfsStructure, offset);
        this.fileRecord = null;
        this.type = Types.fromValue(getUInt32AsInt(0));
        this.flags = getUInt16(0x0C);
    }

    /**
     * @return Returns the attributeType.
     */
    public Types getAttributeType() {
        return type;
    }

    /*
     * Flag |Description ------------------- 0x0001 |Compressed 0x4000
     * |Encrypted 0x8000 |Sparse
     */
    public int getFlags() {
        return flags;
    }

    /**
     * Checks whether this attribute contains compressed data runs.
     *
     * @return {@code true} if the attribute contains compressed runs, {@code false} otherwise.
     */
    public boolean isCompressedAttribute() {
        return (getFlags() & 0x0001) != 0;
    }

    /**
     * @return Returns the nameLength.
     */
    public int getNameLength() {
        return getUInt8(0x09);
    }

    /**
     * @return Returns the nameOffset.
     */
    public int getNameOffset() {
        return getUInt16(0x0A);
    }

    /**
     * @return Returns the attributeID.
     */
    public int getAttributeID() {
        return getUInt16(0x0E);
    }

    /**
     * @return Returns the attributeName.
     */
    public String getAttributeName() {
        // if it is named fill the attribute name
        final int nameLength = getNameLength();
        if (nameLength > 0) {
            final char[] namebuf = new char[nameLength];
            final int nameOffset = getNameOffset();
            for (int i = 0; i < nameLength; i++) {
                namebuf[i] = getChar16(nameOffset + (i * 2));
            }
            return new String(namebuf);
        }
        return null;
    }

    /**
     * @return Returns the fileRecord.
     */
    public FileRecord getFileRecord() {
        return this.fileRecord;
    }

    /**
     * @return Returns the resident.
     */
    public boolean isResident() {
        return (getUInt8(0x08) == 0);
    }

    /**
     * Gets the length of this attribute in bytes.
     * 
     * @return the length
     */
    public int getSize() {
        return getUInt32AsInt(4);
    }

    /**
     * Generates a hex dump of the attribute's data.
     *
     * @return the hex dump.
     */
    public String hexDump() {
        int length = getBuffer().length - getOffset();
        byte[] data = new byte[length];
        getData(0, data, 0, data.length);
        return FSUtils.toString(data);
    }

    /**
     * Generates a debug string for the attribute.
     *
     * @return the debug string.
     */
    public abstract String toDebugString();

    /**
     * Create an NTFSAttribute instance suitable for the given attribute data.
     *
     * @param fileRecord the containing file record.
     * @param offset the offset to read from.
     * @return the attribute
     */
    public static NTFSAttribute getAttribute(FileRecord fileRecord, int offset) {
        final boolean resident = (fileRecord.getUInt8(offset + 0x08) == 0);
        final Types type = Types.fromValue(fileRecord.getUInt32AsInt(offset));

        if (type != null) {
            switch (type) {
                case STANDARD_INFORMATION:
                    return new StandardInformationAttribute(fileRecord, offset);

                case ATTRIBUTE_LIST:
                    if (resident) {
                        return new AttributeListAttributeRes(fileRecord, offset);
                    } else {
                        return new AttributeListAttributeNonRes(fileRecord, offset);
                    }

                case FILE_NAME:
                    return new FileNameAttribute(fileRecord, offset);

                case INDEX_ROOT:
                    return new IndexRootAttribute(fileRecord, offset);

                case INDEX_ALLOCATION:
                    return new IndexAllocationAttribute(fileRecord, offset);

                case REPARSE_POINT:
                    if (resident) {
                        return new ReparsePointAttribute(fileRecord, offset);
                    } else {
                        // When the length exceeds some limit (less than 200), the attribute will be non-resident.

                        // It is reproduced easily by running the command below
                        // mklink /j "path200" c:\temp\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\0123456789\01234

                        // TODO:
                        //  We haven't figured out how to extract it yet.
                        //  All the doc we found so far are saying the reparse point attribute is stored as a resident.
                        //  e.g.
                        //  https://github.com/libyal/libfsntfs/blob/main/documentation/New%20Technologies%20File%20System%20(NTFS).asciidoc#615-the-reparse-point-attribute
                        //  "The reparse point attribute ($REPARSE_POINT) contains information about a file system-level link. It is stored as a resident MFT attribute."
                        //  or
                        //  http://ftp.kolibrios.org/users/Asper/docs/NTFS/ntfsdoc.html#attribute_reparse_point which describes the structure like a resident one.
                        //  So we just pass NTFSNonResidentAttribute to handle the case that "it is a reparse point but also a non-resident attribute."
                        //  And unfortunately, neither AttributeListAttributeNonRes nor NTFSNonResidentAttribute could help to get the targetName/printName of the reparse point.
                    }

                default:
                    // check the resident flag
                    if (resident) {
                        // resident
                        return new NTFSResidentAttribute(fileRecord, offset);
                    } else {
                        // non resident
                        return new NTFSNonResidentAttribute(fileRecord, offset);
                    }
            }
        }

        return null;
    }
}
