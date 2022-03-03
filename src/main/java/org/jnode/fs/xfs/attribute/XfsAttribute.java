package org.jnode.fs.xfs.attribute;

import org.jnode.fs.FSAttribute;
import org.jnode.fs.xfs.XfsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A XFS extended Attribute.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class XfsAttribute extends XfsObject implements FSAttribute {

    /**
     * The logger implementation.
     */
    private static final Logger log = LoggerFactory.getLogger(XfsAttribute.class);

    /**
     * The value of the flags.
     */
    private final int flags;

    /**
     * The length of the name.
     */
    private final int nameLength;

    /**
     * The length of the attribute.
     */
    private final int valueLength;

    /**
     * The name of the attribute.
     */
    private final String attributeName;

    private final byte[] value;

    /**
     * Creates an attribute instance.
     *
     * @param data   of the inode.
     * @param offset of the inode's data
     */
    public XfsAttribute(byte[] data, long offset) {
        super(data, (int) offset);
        nameLength = getUInt8(0);
        valueLength = getUInt8(1);
        flags = getUInt8(2);
        value = new byte[valueLength];
        byte[] name = new byte[nameLength];
        System.arraycopy(data, (int) offset + 3, name, 0, nameLength);
        System.arraycopy(data, (int) offset + 3 + nameLength, value, 0, valueLength);
        attributeName = new String(name, StandardCharsets.UTF_8);
    }

    /**
     * Gets the attribute size offset of this node.
     *
     * @return the inode number.
     */
    public int getAttributeSizeForOffset() {
        return nameLength + valueLength + 3;
    }

    /**
     * Gets flags of the attribute.
     *
     * @return the flags.
     */
    public int getFlags() {
        return flags;
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
    public String getName() {
        return attributeName;
    }

    /**
     * Gets the value of the attribute.
     *
     * @return the value.
     */
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
                "Attribute:[Name:%s Name Value:%s flags:%d]",
                getName(), Arrays.toString(getValue()), getFlags());
    }
}
