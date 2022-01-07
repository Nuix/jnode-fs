package org.jnode.fs.xfs.attribute;

import org.jnode.fs.xfs.XfsObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * A XFS SELinux Attribute.
 *
 * @author
 */
public class XfsAttribute extends XfsObject {

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

    public XfsAttribute(byte[] data, long offset) throws IOException {
        super(data, (int)offset);
        nameLength = (int) read(0, 1);
        valueLength = (int) read(1, 1);
        flags = (int) read(2, 1);
        ByteBuffer buffer = ByteBuffer.allocate(nameLength + valueLength);
        System.arraycopy(data, (int)offset + 3 , buffer.array(), 0 , (nameLength + valueLength) );
        final String nameval = new String(buffer.array(), StandardCharsets.US_ASCII);
        attributeName = nameval.substring(0, nameLength);
        attributeValue = nameval.substring(nameLength).replaceAll("\0", "");
    }

    /**
     * Gets the validSignatures.
     *
     * @return the valid magic values.
     */
    protected List<Long> validSignatures() { return Arrays.asList(0L); }

    /**
     * Gets the magic signature.
     *
     * @return the magic signature.
     */
    @Override
    public long getMagicSignature() throws IOException {
        return 0L;
    }

    /**
     * Gets the attribute size offset of this node.
     *
     * @return the inode number.
     */
    public int getAttributeSizeForOffset(){ return nameLength + valueLength + 3; }

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
    public int getNameLength() { return nameLength; }

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
    public String getValue() { return attributeValue; }

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
