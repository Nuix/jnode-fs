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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jnode.fs.ntfs.NTFSStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Items related to the USN journal file ($Extend\$UsnJrnl).
 *
 * @author Luke Quinane
 */
public class UsnJournal {

    private static final Logger log = LoggerFactory.getLogger(UsnJournal.class);

    /**
     * Reads the file name out of a USN record.
     *
     * <p>The stored offset and size are taken straight off disk. Records recovered from the unallocated tail of
     * $UsnJrnl:$J routinely carry junk in these fields, which would otherwise splice a neighbouring record's bytes
     * into the name or read past the end of the journal buffer.</p>
     *
     * @param record            the record to read from.
     * @param recordSize        the size the record records for itself.
     * @param fixedHeaderLength the length of the record's fixed part, which the name has to start after.
     * @param nameOffset        the stored offset to the name, relative to the start of the record.
     * @param nameSize          the stored size of the name in bytes.
     * @return the file name, or an empty string if the stored offset or size is not usable.
     */
    static String readFileName(NTFSStructure record, long recordSize, int fixedHeaderLength, int nameOffset,
                               int nameSize) {
        long available = Math.min(recordSize, record.getBuffer().length - (long) record.getOffset());

        if (nameOffset < fixedHeaderLength || nameSize < 0 || nameOffset > available ||
            nameSize > available - nameOffset) {
            log.debug("Ignoring a USN record file name at offset {} of size {}; only {} bytes are readable",
                nameOffset, nameSize, available);
            return "";
        }

        byte[] buffer = new byte[nameSize];
        record.getData(nameOffset, buffer, 0, buffer.length);

        try {
            return new String(buffer, "UTF-16LE");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-16LE charset missing from JRE", e);
        }
    }

    /**
     * Gets the major version for a USN record entry.
     *
     * @param structure the structure to read from.
     * @return the major version number.
     */
    public static int getMajorVersion(NTFSStructure structure) {
        return structure.getUInt16(0x4);
    }

    /**
     * Gets the minor version for a USN record entry.
     *
     * @param structure the structure to read from.
     * @return the minor version number.
     */
    public static int getMinorVersion(NTFSStructure structure) {
        return structure.getUInt16(0x6);
    }

    /**
     * File attribute constants
     */
    public static class FileAttribute {
        /**
         * The lookup map for file attributes.
         */
        private static final Map<Long, String> attributeMap = new LinkedHashMap<Long, String>();

        /**
         * Registers a value in the map.
         *
         * @param value the value to register.
         * @param name  the name.
         * @return the value.
         */
        private static long register(long value, String name) {
            attributeMap.put(value, name);
            return value;
        }

        /**
         * Looks up the attributes for the given value.
         *
         * @param value the value to lookup.
         * @return the attributes or "unknown-xXYZ" if the value is not known.
         */
        public static List<String> lookupAttributes(long value) {
            List<String> reasons = new ArrayList<String>();

            for (Map.Entry<Long, String> entry : attributeMap.entrySet()) {
                if ((value & entry.getKey()) != 0) {
                    reasons.add(entry.getValue());
                    value &= ~entry.getKey();
                }
            }

            if (value != 0) {
                reasons.add("unknown-x" + Long.toHexString(value));
            }

            return reasons;
        }

        public static final long READONLY = register(0x1, "read-only");
        public static final long HIDDEN = register(0x2, "hidden");
        public static final long SYSTEM = register(0x4, "system");
        public static final long DIRECTORY = register(0x10, "directory");
        public static final long ARCHIVE = register(0x20, "archive");
        public static final long DEVICE = register(0x40, "device");
        public static final long NORMAL = register(0x80, "normal");
        public static final long TEMPORARY = register(0x100, "temporary");
        public static final long SPARSE = register(0x200, "sparse");
        public static final long REPARSE_POINT = register(0x400, "reparse-point");
        public static final long COMPRESSED = register(0x800, "compressed");
        public static final long OFFLINE = register(0x1000, "offline");
        public static final long NOT_INDEXED = register(0x2000, "not-indexed");
        public static final long ENCRYPTED = register(0x4000, "encrypted");
        public static final long VIRTUAL = register(0x10000, "virtual");
    }

    /**
     * Reason constants
     */
    public static class Reason {
        /**
         * The lookup map for reasons.
         */
        private static final Map<Long, String> reasonMap = new LinkedHashMap<Long, String>();

        /**
         * Registers a value in the map.
         *
         * @param value the value to register.
         * @param name  the name.
         * @return the value.
         */
        private static long register(long value, String name) {
            reasonMap.put(value, name);
            return value;
        }

        /**
         * Looks up the reasons for the given value.
         *
         * @param value the value to lookup.
         * @return the reasons or "unknown-xXYZ" if the value is not known.
         */
        public static List<String> lookupReasons(long value) {
            List<String> reasons = new ArrayList<String>();

            for (Map.Entry<Long, String> entry : reasonMap.entrySet()) {
                if ((value & entry.getKey()) != 0) {
                    reasons.add(entry.getValue());
                    value &= ~entry.getKey();
                }
            }

            if (value != 0) {
                reasons.add("unknown-x" + Long.toHexString(value));
            }

            return reasons;
        }

        /**
         * The data in the file or directory was overwritten (USN_REASON_DATA_OVERWRITE).
         */
        public static final long DATA_OVERWRITE = register(0x1, "data-overwrite");

        /**
         * The file or directory was extended, i.e. added to (USN_REASON_DATA_EXTEND).
         */
        public static final long DATA_EXTEND = register(0x2, "data-extend");

        /**
         * The file or directory was truncated (USN_REASON_DATA_TRUNCATION).
         */
        public static final long DATA_TRUNCATION = register(0x4, "data-truncation");

        /**
         * The data in one or more named data streams was overwritten (USN_REASON_NAMED_DATA_OVERWRITE).
         */
        public static final long NAMED_DATA_OVERWRITE = register(0x10, "named-data-overwrite");

        /**
         * One or more named data streams were extended, i.e. added to (USN_REASON_NAMED_DATA_EXTEND).
         */
        public static final long NAMED_DATA_EXTEND = register(0x20, "named-data-extend");

        /**
         * One or more named data streams were truncated (USN_REASON_NAMED_DATA_TRUNCATION).
         */
        public static final long NAMED_DATA_TRUNCATION = register(0x40, "named-data-truncation");

        /**
         * File or directory created.
         */
        public static final long FS_ENTRY_CREATED = register(0x100, "fs-entry-created");

        /**
         * File or directory deleted.
         */
        public static final long FS_ENTRY_DELETED = register(0x200, "fs-entry-deleted");

        /**
         * User changed a file or directory's extended attributes.
         */
        public static final long FS_ENTRY_EX_ATTR_CHANGED = register(0x400, "fs-entry-ex-attr-changed");

        /**
         * File or directory access changes.
         */
        public static final long FS_ENTRY_ACCESS_CHANGED = register(0x800, "fs-entry-access-changed");

        /**
         * File or directory renamed. USN journal entry has the old name.
         */
        public static final long FS_ENTRY_RENAMED_OLD = register(0x1000, "fs-entry-rename-old");

        /**
         * File or directory renamed. USN journal entry has the new name.
         */
        public static final long FS_ENTRY_RENAMED_NEW = register(0x2000, "fs-entry-rename-new");

        /**
         * User changed whether the contents of a file was indexed or not.
         */
        public static final long FS_ENTRY_INDEXING_CHANGED = register(0x4000, "fs-entry-indexing-changed");

        /**
         * User changed a file or directories attributes or timestamps.
         */
        public static final long FS_ENTRY_ATTR_CHANGED = register(0x8000, "fs-entry-attr-changed");

        /**
         * A hard link was removed from a directory.
         */
        public static final long HARD_LINK_REMOVED = register(0x10000, "hard-link-removed");

        /**
         * User changed whether a file or directory is compressed.
         */
        public static final long FS_ENTRY_COMPRESSION_CHANGED = register(0x20000, "fs-entry-compression-changed");

        /**
         * User changed whether a file or directory is encrypted.
         */
        public static final long FS_ENTRY_ENCRYPTION_CHANGED = register(0x40000, "fs-entry-encryption-changed");

        /**
         * The user changed an object identifier for a file or directory.
         */
        public static final long FS_ENTRY_OBJ_IDENTIFIER_CHANGED = register(0x80000, "fs-entry-obj-identifier-changed");

        /**
         * A reparse point was altered, added or deleted in the directory.
         */
        public static final long REPARSE_POINT_ALTERED = register(0x100000, "reparse-point-altered");

        /**
         * A data stream for a file has been added, removed or renamed.
         */
        public static final long DATA_STREAM_ALTERED = register(0x200000, "data-stream-altered");

        /**
         * The stream was modified through a TxF transaction (USN_REASON_TRANSACTED_CHANGE).
         */
        public static final long TRANSACTED_CHANGE = register(0x400000, "transacted-change");

        /**
         * The state of the FILE_ATTRIBUTE_INTEGRITY_STREAM attribute changed (USN_REASON_INTEGRITY_CHANGE).
         */
        public static final long INTEGRITY_CHANGE = register(0x800000, "integrity-change");

        /**
         * The file or directory was closed.
         */
        public static final long FS_ENTRY_CLOSED = register(0x80000000L, "fs-entry-closed");

        /**
         * @deprecated renamed to {@link #DATA_OVERWRITE} to match USN_REASON_DATA_OVERWRITE.
         */
        @Deprecated
        public static final long DATA_WRITE = DATA_OVERWRITE;

        /**
         * @deprecated renamed to {@link #DATA_EXTEND} to match USN_REASON_DATA_EXTEND. The old name described this
         *     as the file being added to the file system, which is what {@link #FS_ENTRY_CREATED} means.
         */
        @Deprecated
        public static final long FS_ENTRY_ADDED = DATA_EXTEND;

        /**
         * @deprecated renamed to {@link #DATA_TRUNCATION} to match USN_REASON_DATA_TRUNCATION.
         */
        @Deprecated
        public static final long FS_ENTRY_TRUNCATED = DATA_TRUNCATION;

        /**
         * @deprecated renamed to {@link #NAMED_DATA_OVERWRITE} to match USN_REASON_NAMED_DATA_OVERWRITE.
         */
        @Deprecated
        public static final long DATA_WRITE_ALT = NAMED_DATA_OVERWRITE;

        /**
         * @deprecated renamed to {@link #NAMED_DATA_EXTEND} to match USN_REASON_NAMED_DATA_EXTEND.
         */
        @Deprecated
        public static final long DATA_APPEND = NAMED_DATA_EXTEND;

        /**
         * @deprecated renamed to {@link #NAMED_DATA_TRUNCATION} to match USN_REASON_NAMED_DATA_TRUNCATION.
         */
        @Deprecated
        public static final long DATA_TRUNCATED = NAMED_DATA_TRUNCATION;
    }

    /**
     * Source info constants
     */
    public static class SourceInfo {
        /**
         * The file or directory was changed by the operating system.
         */
        public static final int CHANGE_FROM_OS = 0x1;

        /**
         * Private stream data was added but the modifications did not change application data.
         */
        public static final int CHANGE_FROM_AUX = 0x2;

        /**
         * The change was related to the replication service.
         */
        public static final int REPLICATION = 0x4;

        /**
         * The operation is modifying a file on a client system to match the copy in the cloud.
         */
        public static final int CLIENT_REPLICATION = 0x8;
    }
}
