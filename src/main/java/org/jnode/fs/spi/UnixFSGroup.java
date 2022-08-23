package org.jnode.fs.spi;

import java.security.Principal;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

public class UnixFSGroup {

    private final String name;

    private final Set<Principal> members = new LinkedHashSet<Principal>();

    public UnixFSGroup(String name) {
        this.name = name;
    }

    public boolean addMember(Principal user) {
        return members.add(user);
    }

    public boolean removeMember(Principal user) {
        return members.remove(user);
    }

    public boolean isMember(Principal member) {
        return members.contains(member);
    }

    public Enumeration<? extends Principal> members() {
        return Collections.enumeration(members);
    }

    public String getName() {
        return name;
    }
}
