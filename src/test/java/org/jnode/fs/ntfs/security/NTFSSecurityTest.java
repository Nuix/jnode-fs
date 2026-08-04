package org.jnode.fs.ntfs.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
        assertThat(AccessControlEntry.Type.namesForType(9), contains("Unknown Type: 0x9"));
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
