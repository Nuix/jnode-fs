package org.jnode.fs.btrfs;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jnode.driver.block.FileDevice;
import org.jnode.fs.FSAttribute;
import org.jnode.fs.FSDirectory;
import org.jnode.fs.FSEntry;
import org.jnode.fs.FileSystemTestUtils;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Extended attributes, end-to-end against a real image: {@code user.*} xattrs written with
 * {@code setfattr} before {@code mkfs.btrfs -r} (which carries them into the image), plus the
 * {@code security.selinux} context the build host stamped on every inode. Also unit-tests the
 * one subtle case — several attributes packed into one {@code XATTR_ITEM} (name-hash collision).
 *
 * @author David Baird
 */
public class BtrfsXattrTest {

    /** The SELinux context on every fixture inode (36 bytes on disk: this string + a NUL). */
    private static final String SELINUX = "unconfined_u:object_r:user_tmp_t:s0";

    @Test
    public void userXattrsReadBackFromARealImage() throws Exception {
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-xattr.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);
            Map<String, FSEntry> byPath = new HashMap<String, FSEntry>();
            collect(fs.getRootEntry().getDirectory(), "", byPath);

            Map<String, FSAttribute> one = attrs(byPath.get("one.txt"));
            assertArrayEquals("spacemap".getBytes(StandardCharsets.UTF_8),
                    one.get("user.origin").getValue());
            assertSelinux(one);

            Map<String, FSAttribute> multi = attrs(byPath.get("multi.txt"));
            assertArrayEquals("first value".getBytes(StandardCharsets.UTF_8),
                    multi.get("user.alpha").getValue());
            assertArrayEquals("second value".getBytes(StandardCharsets.UTF_8),
                    multi.get("user.beta").getValue());
            assertEquals("selinux + two user attrs", 3, multi.size());

            byte[] big = attrs(byPath.get("long.txt")).get("user.big").getValue();
            assertEquals(300, big.length);
            for (byte b : big) {
                assertEquals('V', b);
            }

            assertEquals("zero-length value survives", 0,
                    attrs(byPath.get("empty.txt")).get("user.empty").getValue().length);

            Map<String, FSAttribute> plain = attrs(byPath.get("plain.txt"));
            assertNull("no user attrs on plain.txt", plain.get("user.origin"));
            assertSelinux(plain);
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void selinuxContextOnTheOriginalFixture() throws Exception {
        // the pre-existing simple fixture already carries xattrs — they must read back too
        File testFile = FileSystemTestUtils.getTestFile("org/jnode/fs/btrfs/btrfs-simple.img");
        try (FileDevice device = new FileDevice(testFile, "r")) {
            BtrfsFileSystem fs = new BtrfsFileSystemType().create(device, true);
            Map<String, FSEntry> byPath = new HashMap<String, FSEntry>();
            collect(fs.getRootEntry().getDirectory(), "", byPath);
            assertSelinux(attrs(byPath.get("readme.txt")));
        } finally {
            testFile.delete();
        }
    }

    @Test
    public void hashCollidingAttributesShareOneItem() {
        // two entries packed back-to-back in a single XATTR_ITEM (a name-hash collision) — the
        // parser must loop through the item, not assume one entry per item
        byte[] item = concat(entry("user.a", "one"), entry("user.b", "two"));
        List<FSAttribute> out = new ArrayList<FSAttribute>();
        BtrfsAttribute.parseItem(item, out);
        assertEquals(2, out.size());
        assertEquals("user.a", out.get(0).getName());
        assertArrayEquals("one".getBytes(StandardCharsets.UTF_8), out.get(0).getValue());
        assertEquals("user.b", out.get(1).getName());
        assertArrayEquals("two".getBytes(StandardCharsets.UTF_8), out.get(1).getValue());
    }

    @Test
    public void corruptEntryStopsCleanly() {
        byte[] item = entry("user.ok", "fine");
        item[BtrfsConstants.DIR_DATA_LEN] = (byte) 0xFF; // data_len now runs past the item
        item[BtrfsConstants.DIR_DATA_LEN + 1] = (byte) 0xFF;
        List<FSAttribute> out = new ArrayList<FSAttribute>();
        BtrfsAttribute.parseItem(item, out);
        assertEquals("corrupt entry is skipped, not mis-parsed", 0, out.size());
    }

    /** A synthetic dir_item entry: header + name + value, as stored inside an XATTR_ITEM. */
    private static byte[] entry(String name, String value) {
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        byte[] v = value.getBytes(StandardCharsets.UTF_8);
        byte[] e = new byte[BtrfsConstants.DIR_NAME + n.length + v.length];
        e[BtrfsConstants.DIR_DATA_LEN] = (byte) v.length;
        e[BtrfsConstants.DIR_NAME_LEN] = (byte) n.length;
        System.arraycopy(n, 0, e, BtrfsConstants.DIR_NAME, n.length);
        System.arraycopy(v, 0, e, BtrfsConstants.DIR_NAME + n.length, v.length);
        return e;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] r = new byte[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    private static Map<String, FSAttribute> attrs(FSEntry entry) throws Exception {
        Map<String, FSAttribute> byName = new HashMap<String, FSAttribute>();
        for (FSAttribute a : ((BtrfsEntry) entry).getAttributes()) {
            byName.put(a.getName(), a);
        }
        return byName;
    }

    private static void assertSelinux(Map<String, FSAttribute> attrs) {
        FSAttribute selinux = attrs.get("security.selinux");
        assertNotNull("security.selinux present", selinux);
        byte[] v = selinux.getValue();
        assertEquals("value keeps its trailing NUL", 36, v.length);
        assertEquals(0, v[35]);
        assertEquals(SELINUX, new String(v, 0, 35, StandardCharsets.UTF_8));
    }

    private static void collect(FSDirectory dir, String prefix, Map<String, FSEntry> out) throws Exception {
        java.util.Iterator<? extends FSEntry> it = dir.iterator();
        while (it.hasNext()) {
            FSEntry e = it.next();
            String name = e.getName();
            if (".".equals(name) || "..".equals(name)) {
                continue;
            }
            String path = prefix.isEmpty() ? name : prefix + "/" + name;
            out.put(path, e);
            if (e.isDirectory()) {
                collect(e.getDirectory(), path, out);
            }
        }
    }
}
