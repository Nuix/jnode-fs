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
 
package org.jnode.fs.ntfs.security;

import java.util.ArrayList;
import java.util.List;
import org.jnode.fs.ntfs.NTFSStructure;

/**
 * An access control entry as stored inside a {@link AccessControlList}.
 *
 * @author Luke Quinane
 */
public class AccessControlEntry extends NTFSStructure {

    /**
     * Creates a new ACE from the given ACL data.
     *
     * @param parent the parent ACL.
     * @param offset the offset to this ACE entry.
     */
    public AccessControlEntry(AccessControlList parent, int offset) {
        super(parent, offset);
    }

    /**
     * Gets the type for this entry.
     *
     * @return the type.
     */
    public int getType() {
        return getInt8(0);
    }

    /**
     * Gets the flags for this entry.
     *
     * @return the flags.
     */
    public int getFlags() {
        return getInt8(1);
    }

    /**
     * Gets the size of this entry.
     *
     * @return the size.
     */
    public int getSize() {
        return getInt16(2);
    }

    /**
     * Gets the access mask.
     *
     * @return the access mask.
     */
    public int getAccessMask() {
        return getInt32(4);
    }

    /**
     * Gets the SID associated with this entry.
     *
     * @return the SID.
     */
    public SecurityIdentifier getSid() {
        return SecurityUtils.readSid(this, getSidOffset());
    }

    /**
     * Checks whether this is an object ACE, which holds object flags and up to two GUIDs before its SID.
     *
     * @return {@code true} for the object ACE types.
     */
    public boolean isObjectAce() {
        switch (getType()) {
            case 0x05:
            case 0x06:
            case 0x07:
            case 0x08:
            case 0x0b:
            case 0x0c:
            case 0x0f:
            case 0x10:
                return true;
            default:
                return false;
        }
    }

    /**
     * Gets the offset of the SID: after the object flags and the object type and inherited object type GUIDs that
     * the flags say are present, for an object ACE.
     */
    private int getSidOffset() {
        if (!isObjectAce()) {
            return 8;
        }

        int objectFlags = getInt32(8);
        int offset = 12;
        if ((objectFlags & 0x1) != 0) {
            offset += 16;
        }
        if ((objectFlags & 0x2) != 0) {
            offset += 16;
        }
        return offset;
    }

    /**
     * ACE types.
     */
    public static enum Type {
        ALLOW(0x00, "Allow"),
        DENY(0x01, "Deny"),
        AUDIT(0x02, "Audit"),
        ALARM(0x03, "Alarm"),
        ALLOW_COMPOUND(0x04, "Allow Compound"),
        ALLOW_OBJECT(0x05, "Allow Object"),
        DENY_OBJECT(0x06, "Deny Object"),
        AUDIT_OBJECT(0x07, "Audit Object"),
        ALARM_OBJECT(0x08, "Alarm Object"),
        ALLOW_CALLBACK(0x09, "Allow Callback"),
        DENY_CALLBACK(0x0a, "Deny Callback"),
        ALLOW_CALLBACK_OBJECT(0x0b, "Allow Callback Object"),
        DENY_CALLBACK_OBJECT(0x0c, "Deny Callback Object"),
        AUDIT_CALLBACK(0x0d, "Audit Callback"),
        ALARM_CALLBACK(0x0e, "Alarm Callback"),
        AUDIT_CALLBACK_OBJECT(0x0f, "Audit Callback Object"),
        ALARM_CALLBACK_OBJECT(0x10, "Alarm Callback Object"),
        MANDATORY_LABEL(0x11, "Mandatory Label"),
        RESOURCE_ATTRIBUTE(0x12, "Resource Attribute"),
        SCOPED_POLICY_ID(0x13, "Scoped Policy ID"),
        PROCESS_TRUST_LABEL(0x14, "Process Trust Label"),
        ACCESS_FILTER(0x15, "Access Filter");

        int type;
        String name;
            
        Type(int type, String name) {
            this.type = type;
            this.name = name;
        }
        
        /**
         * Gets the name for the given ACE type. Note the type is a plain value, not a set of flags, so at most one
         * name is ever returned.
         *
         * @param type the type to look up.
         * @return a single element list holding the name, or the unknown-type placeholder.
         */
        public static List<String> namesForType(int type) {
            List<String> names = new ArrayList<String>();

            for (Type aceType : values()) {
                if (aceType.type == type) {
                    names.add(aceType.name);
                    return names;
                }
            }

            names.add(String.format("Unknown Type: 0x%x", type));
            return names;
        }
    }

    /**
     * ACE flags.
     */
    public static enum Flags {
        OBJECT_INHERITS(0x01, "Object Inherits"),
        CONTAINER_INHERITS(0x02, "Container Inherits"),
        DONT_PROPAGATE_INHERIT_ACE(0x04, "No Propagate Inherit ACE"),
        INHERIT_ONLY_ACE(0x08, "Inherit Only ACE"),

        AUDIT_SUCCESS(0x40, "Audit Success"),
        AUDIT_FAILURE(0x80, "Audit Failure");

        int flags;
        String name;
            
        Flags(int flags, String name) {
            this.flags = flags;
            this.name = name;
        }

        /**
         * Gets a list of matching flags names for the given value.
         *
         * @param flags the flags to look up.
         * @return the list of names.
         */
        public static List<String> namesForFlags(int flags) {
            List<String> names = new ArrayList<String>();

            for (Flags aceFlags : values()) {
                if ((aceFlags.flags & flags) == aceFlags.flags) {
                    flags -= aceFlags.flags;
                    names.add(aceFlags.name);
                }
            }

            if (flags != 0) {
                names.add(String.format("Unknown Flags: 0x%x", flags));
            }

            return names;
        }
    }

    /**
     * ACE mask.
     */
    public static enum Mask {
        READ(0x01, "Read", "List Contents"),
        WRITE(0x02, "Write", "Add File"),
        APPEND(0x04, "Write", "Add Subdirectory"),
        READ_EXTENDED_ATTRIBUTES(0x08, "Read Extended Attributes"),
        WRITE_EXTENDED_ATTRIBUTES(0x10, "Write Extended Attributes"),
        EXECUTE(0x20, "Execute File", "Traverse Directory"),
        DELETE_CHILD(0x40, "Delete Child"),
        READ_ATTRIBUTES(0x80, "Read Attributes"),
        WRITE_ATTRIBUTES(0x100, "Write Attributes"),
        DELETE(0x10000, "Delete"),
        READ_CONTROL(0x20000, "Read Security Descriptor"),
        WRITE_DAC(0x40000, "Write DACL"),
        WRITE_OWNER(0x80000, "Write Owner"),
        SYNCHRONIZE(0x100000, "Synchronize");

        /**
         * The mask value.
         */
        int mask;

        /**
         * The name of the associated file permission.
         */
        String filePermission;

        /**
         * The name of the associated directory permission.
         */
        String directoryPermission;

        Mask(int mask, String permission) {
            this(mask, permission, permission);
        }

        Mask(int mask, String filePermission, String directoryPermission) {
            this.mask = mask;
            this.filePermission = filePermission;
            this.directoryPermission = directoryPermission;
        }

        /**
         * Gets a list of matching permission names for the given mask value.
         *
         * @param mask the mask.
         * @param isDirectory {@code true} to look up the names for a directory, {@code false} to get the names for a
         *   file.
         * @return the list of names.
         */
        public static List<String> namesForMask(int mask, boolean isDirectory) {
            List<String> names = new ArrayList<String>();

            for (Mask aceMask : values()) {
                if ((aceMask.mask & mask) == aceMask.mask) {
                    mask -= aceMask.mask;
                    names.add(isDirectory ? aceMask.directoryPermission : aceMask.filePermission);
                }
            }

            if (mask != 0) {
                names.add(String.format("Unknown Access Mask: 0x%x", mask));
            }

            return names;
        }
    }

    /**
     * The mask of a mandatory label ACE (type 0x11): which access from a lower integrity level is refused.
     */
    public static enum MandatoryPolicy {
        NO_WRITE_UP(0x1, "No Write Up"),
        NO_READ_UP(0x2, "No Read Up"),
        NO_EXECUTE_UP(0x4, "No Execute Up");

        int mask;

        String name;

        MandatoryPolicy(int mask, String name) {
            this.mask = mask;
            this.name = name;
        }

        /**
         * Gets the names of the policy bits set in a mandatory label's mask.
         *
         * @param mask the mask.
         * @return the list of names.
         */
        public static List<String> namesForMask(int mask) {
            List<String> names = new ArrayList<String>();

            for (MandatoryPolicy policy : values()) {
                if ((policy.mask & mask) != 0) {
                    mask &= ~policy.mask;
                    names.add(policy.name);
                }
            }

            if (mask != 0) {
                names.add(String.format("Unknown Mandatory Policy: 0x%x", mask));
            }

            return names;
        }
    }

    /**
     * Gets the names for this ACE's mask. A mandatory label's mask is a policy, not access rights, and the masks of
     * resource attribute and process trust label ACEs aren't decoded, so only the other types are named from
     * {@link Mask}.
     *
     * @param isDirectory {@code true} to name directory permissions, {@code false} for file permissions.
     * @return the list of names.
     */
    public List<String> getAccessMaskNames(boolean isDirectory) {
        int type = getType();
        if (type == Type.MANDATORY_LABEL.type) {
            return MandatoryPolicy.namesForMask(getAccessMask());
        }
        if (type == Type.RESOURCE_ATTRIBUTE.type || type == Type.PROCESS_TRUST_LABEL.type) {
            List<String> names = new ArrayList<String>();
            names.add(String.format("Access Mask: 0x%x", getAccessMask()));
            return names;
        }
        return Mask.namesForMask(getAccessMask(), isDirectory);
    }
}
