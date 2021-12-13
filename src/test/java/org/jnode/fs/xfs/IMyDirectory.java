package org.jnode.fs.xfs;

import java.io.IOException;

public interface IMyDirectory {
    public long getNameSize() throws IOException;
    public String getName() throws IOException;
    public long getINodeNumber() throws IOException;
}
