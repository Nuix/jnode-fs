package org.jnode.fs.ntfs;

import org.jnode.fs.ntfs.attribute.ReparsePointAttribute;
import org.jnode.fs.ntfs.attribute.NTFSAttribute;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.jnode.fs.FileSystemTestUtils.*;

/**
 * Tests for reparse point data in {@link NTFSAttribute}.
 */
public class NtfsReparsePointTest {

    @Test
    public void testResidentReparsePoint()  {
        byte[] data = intsToByteArray(
                "-64 0 0 0 -56 1 0 0 0 0 0 0 0 0 3 0 -84 1 0 0 24 0 0 0 3 0 0 -96 -92 1 0 0 0 0 -48 0 -46 0 " +
                        "-56 0 92 0 63 0 63 0 92 0 99 0 58 0 92 0 116 0 101 0 109 0 112 0 92 0 48 0 49 0 50 0 51 0 52 0" +
                        " 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49" +
                        " 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57" +
                        " 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54" +
                        " 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51" +
                        " 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 0 0 99 0 58 0 92 0 116 0 101 0 109 0 112" +
                        " 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54" +
                        " 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51" +
                        " 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48" +
                        " 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56" +
                        " 0 57 0 92 0 48 0 49 0 50 0 51 0 52 0 53 0 54 0 55 0 56 0 57 0 92 0 48 0 49 0 50 0 51 0 0 0 0 0 0 0");

        ReparsePointAttribute reparsePointAttribute = new ReparsePointAttribute(new NTFSStructure(data, 0), 0);

        assertThat(reparsePointAttribute.getPrintName(), is ("c:\\temp\\0123456789\\0123456789\\0123456789\\0123456789\\0123456789\\0123456789\\0123456789\\0123456789\\0123"));
        assertThat(reparsePointAttribute.getTargetName(), is ("\\??\\c:\\temp\\0123456789\\0123456789\\0123456789\\0123456789\\0123456789\\0123456789\\0123456789\\0123456789\\0123"));
    }
}
