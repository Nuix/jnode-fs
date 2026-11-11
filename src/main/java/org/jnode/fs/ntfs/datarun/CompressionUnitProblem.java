package org.jnode.fs.ntfs.datarun;

/**
 * A way a compression unit fails strict LZNT1. A unit that a file really wrote never has one; a unit read from
 * clusters that now hold something else, such as those of a deleted file, nearly always does. The decoder still
 * decodes such a unit, so the result has to be treated as suspect rather than as the file's content.
 */
public enum CompressionUnitProblem {

    /**
     * A chunk header's signature bits aren't 3.
     */
    BAD_SIGNATURE,

    /**
     * A back-reference reaches before the start of its chunk.
     */
    BACK_REFERENCE_BEFORE_CHUNK,

    /**
     * A back-reference runs past the end of its chunk's 4,096 bytes.
     */
    BACK_REFERENCE_PAST_CHUNK,

    /**
     * A chunk other than the last decodes to fewer than 4,096 bytes. Expected only in the unit that holds the valid
     * data length, where the last chunk can be followed by slack rather than an end marker.
     */
    SHORT_CHUNK,

    /**
     * A chunk header claims more bytes than are stored. Expected only in the unit that holds the valid data length.
     */
    CHUNK_PAST_STORED_DATA,

    /**
     * The unit's runs hold more clusters than the compression unit. Only the clusters that fit are decoded.
     */
    RUNS_PAST_UNIT
}
