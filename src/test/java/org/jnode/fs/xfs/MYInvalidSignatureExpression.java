package org.jnode.fs.xfs;

import java.util.List;
import java.util.stream.Collectors;

public class MYInvalidSignatureExpression extends RuntimeException {
    private final String signature;
    private final Long offset;
    private final Class forClass;

    public MYInvalidSignatureExpression(String signature, List<Long> validSignatures, Long offset, Class forClass) {
        super("Invalid signature '" + signature + "' for class " + forClass + " on offset: " + offset + " Accepted signatures : "
                + validSignatures.stream().map(Long::toHexString)
                .map(MyXfsBaseAccessor::HexToAscii)
                .collect(Collectors.toList())
        );
        this.signature = signature;
        this.offset = offset;
        this.forClass = forClass;
    }

    public String getSignature() {
        return signature;
    }

    public Long getOffset() {
        return offset;
    }

    public Class getForClass() {
        return forClass;
    }
}
