package org.jnode.fs.xfs.attribute;

import java.util.Arrays;
import java.util.List;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.util.FSUtils;
import org.jnode.fs.xfs.XfsObject;

/**
 * <p>An XFS short form attribute.</p>
 *
 * <pre>
 * typedef struct xfs_attr_shortform {
 *     struct xfs_attr_sf_hdr {
 *         __be16 totsize;
 *         __u8 count;
 *     } hdr;
 *     struct xfs_attr_sf_entry {
 *         __uint8_t namelen;
 *         __uint8_t valuelen;
 *         __uint8_t flags;
 *         __uint8_t nameval[1];
 *     } list[1];
 * } xfs_attr_shortform_t;
 * </pre>
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class XfsShortFormAttribute extends XfsObject implements FSAttribute {

    /**
     * The length of the name.
     */
    private final int nameLength;

    /**
     * The length of the attribute.
     */
    private final int valueLength;

    /**
     * The value of the flags.
     */
    private final int flags;

    /**
     * The name of the attribute.
     */
    private final String name;

    /**
     * The value of the attribute.
     */
    private final byte[] value;

    /**
     * Creates an attribute instance.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     */
    public XfsShortFormAttribute(byte[] data, long offset) {
        super(data, (int) offset);
        nameLength = readUInt8();
        valueLength = readUInt8();
        flags = readUInt8();

        byte[] attributeName = new byte[nameLength];
        value = new byte[valueLength];
        System.arraycopy(data, getOffset(), attributeName, 0, nameLength);
        System.arraycopy(data, getOffset() + nameLength, value, 0, valueLength);
        name = FSUtils.toNormalizedString(attributeName);
        skipBytes(nameLength + valueLength);
    }

    /**
     * Gets flags of the attribute.
     *
     * @return the flags.
     */
    public List<AttributeFlags> getAttributeFlags() {
        return AttributeFlags.fromValue(flags);
    }

    /**
     * Gets the length of the attribute name.
     *
     * @return the length of the attribute name.
     */
    public int getNameLength() {
        return nameLength;
    }

    /**
     * Gets the length of the attribute value.
     *
     * @return the length of the attribute value.
     */
    public int getValueLength() {
        return valueLength;
    }

    /**
     * Gets the attribute name.
     *
     * @return the attribute name.
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Gets the value of the attribute.
     *
     * @return the value.
     */
    @Override
    public byte[] getValue() {
        return value;
    }

    /**
     * Gets the string of the data.
     *
     * @return the data string .
     */
    @Override
    public String toString() {
        return String.format(
                "Attribute:[Name:%s Name Value:%s flags:%x]",
                getName(), Arrays.toString(getValue()), flags);
    }
}
