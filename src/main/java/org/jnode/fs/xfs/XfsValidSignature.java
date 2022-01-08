package org.jnode.fs.xfs;

import java.util.List;
import java.util.stream.Collectors;

public class XfsValidSignature extends RuntimeException {

    /**
     * The signature (magic number)
     */
    private final String signature;

    /**
     * The offset of the signature
     */
    private final Long offset;

    /**
     * The class name.
     */
    private final Class forClass;


    public XfsValidSignature(String signature, List<Long> validSignatures, Long offset, Class forClass) {
        super("Invalid signature '" + signature + "' for class " + forClass + " on offset: " + offset + " Accepted signatures : "
                + validSignatures.stream().map(Long::toHexString)
                .map(XfsObject::hexToAscii)
                .collect(Collectors.toList())
        );
        this.signature = signature;
        this.offset = offset;
        this.forClass = forClass;
    }

    /**
     * Gets the magic number signature
     *
     * @return a magic number
     */
    public String getSignature() {
        return signature;
    }

    /**
     * Gets the offset where the signature is located
     *
     * @return a offset
     */
    public Long getOffset() {
        return offset;
    }

    /**
     * Gets the class that is validating the signature.
     *
     * @return a class name
     */
    public Class getForClass() {
        return forClass;
    }
}
