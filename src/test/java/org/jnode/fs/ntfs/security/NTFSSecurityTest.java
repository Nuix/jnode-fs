package org.jnode.fs.ntfs.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jnode.util.LittleEndian;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

/**
 * Tests for the NTFS security structures.
 */
public class NTFSSecurityTest {

    /**
     * ACE types are sequential values, not flags. Matching them bitwise made the zero-valued ALLOW match every
     * input, so every ACE reported "Allow" and an audit ACE reported both.
     */
    @Test
    public void testAceTypeNames() {
        assertThat(AccessControlEntry.Type.namesForType(0), contains("Allow"));
        assertThat(AccessControlEntry.Type.namesForType(1), contains("Deny"));
        assertThat(AccessControlEntry.Type.namesForType(2), contains("Audit"));
        assertThat(AccessControlEntry.Type.namesForType(9), contains("Allow Callback"));
        assertThat(AccessControlEntry.Type.namesForType(0x11), contains("Mandatory Label"));
        assertThat(AccessControlEntry.Type.namesForType(0x12), contains("Resource Attribute"));
        assertThat(AccessControlEntry.Type.namesForType(0x14), contains("Process Trust Label"));
        assertThat(AccessControlEntry.Type.namesForType(0x16), contains("Unknown Type: 0x16"));
    }

    /**
     * Builds an ACL holding one ACE with a one sub-authority SID.
     */
    private static AccessControlEntry ace(int type, int mask) {
        byte[] buffer = new byte[0x14 + 8 + 0x14];
        int acl = 0x14;
        buffer[acl] = 2;
        LittleEndian.setInt16(buffer, acl + 2, 8 + 0x14);
        LittleEndian.setInt16(buffer, acl + 4, 1);

        int ace = acl + 8;
        buffer[ace] = (byte) type;
        LittleEndian.setInt16(buffer, ace + 2, 0x14);
        LittleEndian.setInt32(buffer, ace + 4, mask);
        buffer[ace + 8] = 1;
        buffer[ace + 9] = 1;
        buffer[ace + 15] = 16;
        LittleEndian.setInt32(buffer, ace + 16, 0x2000);

        SecurityDescriptor descriptor = new SecurityDescriptor(buffer, 0);
        return new AccessControlList(descriptor, acl).getAces().get(0);
    }

    /**
     * An object ACE holds its object flags and the GUIDs they name before the SID.
     */
    @Test
    public void testObjectAceSidFollowsItsGuids() {
        // Arrange: Allow Object, with both GUIDs present
        byte[] buffer = new byte[0x14 + 8 + 0x38];
        int acl = 0x14;
        buffer[acl] = 2;
        LittleEndian.setInt16(buffer, acl + 2, 8 + 0x38);
        LittleEndian.setInt16(buffer, acl + 4, 1);

        int ace = acl + 8;
        buffer[ace] = 0x05;
        LittleEndian.setInt16(buffer, ace + 2, 0x38);
        LittleEndian.setInt32(buffer, ace + 4, 0x100);
        LittleEndian.setInt32(buffer, ace + 8, 0x3);
        Arrays.fill(buffer, ace + 12, ace + 44, (byte) 0xAB);
        buffer[ace + 44] = 1;
        buffer[ace + 45] = 1;
        buffer[ace + 51] = 5;
        LittleEndian.setInt32(buffer, ace + 52, 18);

        // Act
        AccessControlEntry entry =
            new AccessControlList(new SecurityDescriptor(buffer, 0), acl).getAces().get(0);

        // Assert
        assertThat(entry.isObjectAce(), is(true));
        assertThat(entry.getSid().toSidString(), is("S-1-5-18"));
    }

    /**
     * A mandatory label's mask is the integrity policy, not file access rights: 0x1 is "No Write Up", not "Read".
     */
    @Test
    public void testMandatoryLabelMaskIsNamedAsAPolicy() {
        assertThat(ace(0x11, 0x1).getAccessMaskNames(false), contains("No Write Up"));
        assertThat(ace(0x11, 0x7).getAccessMaskNames(false), contains("No Write Up", "No Read Up", "No Execute Up"));
        assertThat(ace(0x11, 0x9).getAccessMaskNames(false), contains("No Write Up", "Unknown Mandatory Policy: 0x8"));
    }

    @Test
    public void testOtherMasksAreNamedAsBefore() {
        assertThat(ace(0x00, 0x1).getAccessMaskNames(false), contains("Read"));
        assertThat(ace(0x00, 0x1).getAccessMaskNames(true), contains("List Contents"));
        assertThat(ace(0x14, 0x1).getAccessMaskNames(false), contains("Access Mask: 0x1"));
    }

    /**
     * A SID with no sub-authorities is valid, e.g. S-1-0, and used to throw from toSidString().
     */
    @Test
    public void testSidWithNoSubAuthorities() {
        SecurityIdentifier sid = new SecurityIdentifier(0, new ArrayList<Long>());

        assertThat(sid.toSidString(), is("S-1-0"));
    }

    @Test
    public void testOrdinarySids() {
        assertThat(new SecurityIdentifier(5, Arrays.asList(18L)).toSidString(), is("S-1-5-18"));
        assertThat(new SecurityIdentifier(5, Arrays.asList(32L, 544L)).toSidString(), is("S-1-5-32-544"));
    }

    /**
     * A SID parsed back from its own string form has to survive being printed again - previously
     * {@code fromString("S-1-5")} produced an object that {@code toSidString()} could not render.
     */
    @Test
    public void testSidRoundTrip() {
        for (String text : new String[] {"S-1-5", "S-1-5-18", "S-1-5-32-544", "S-1-5-21-1-2-3-1001"}) {
            SecurityIdentifier sid = SecurityIdentifier.fromString(text);

            assertThat(sid.toSidString(), is(text));
        }
    }

    @Test
    public void testSidEquality() {
        List<Long> subAuthorities = Arrays.asList(32L, 544L);

        assertThat(new SecurityIdentifier(5, subAuthorities), is(new SecurityIdentifier(5, subAuthorities)));
        assertThat(new SecurityIdentifier(5, subAuthorities), is(not(new SecurityIdentifier(4, subAuthorities))));
    }
}
