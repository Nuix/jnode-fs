package org.jnode.fs.xfs.attribute;

import org.jnode.fs.xfs.XfsObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * A XFS SELinux Attribute.
 *
 * @author Ricardo Garza
 * @author Julio Parra
 */
public class XfsAttribute extends XfsObject {

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

    /**
     * The value of the attribute.
     */
    private final String attributeValue;

    /**
     * Creates an attribute instance.
     *
     * @param data of the inode.
     * @param offset of the inode's data
     */
    public XfsAttribute(byte[] data, long offset) {
        super(data, (int) offset);
        nameLength = getUInt8(0);
        valueLength = getUInt8(1);
        flags = getUInt8(2);
        ByteBuffer buffer = ByteBuffer.allocate(nameLength + valueLength);
        System.arraycopy(data, (int) offset + 3, buffer.array(), 0, (nameLength + valueLength));
        final String nameval = new String(buffer.array(), StandardCharsets.UTF_8);
        attributeName = nameval.substring(0, nameLength);
        attributeValue = nameval.substring(nameLength).replaceAll("\0", "");
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
    public String getValue() {
        return attributeValue;
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
                 getName(), getValue(), getFlags());
    }
}
