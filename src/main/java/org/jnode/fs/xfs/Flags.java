package org.jnode.fs.xfs;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;

/**
 * A bitmask of a set of flags.
 */
public interface Flags {
    /**
     * Check if a certain flag has been set in this flag number.
     *
     * @param value the certain flag.
     * @return {@code true} if the flag has been set, {@code false} otherwise.
     */
    boolean isSet(long value);

    /**
     * Util class for getting the values out of a flag bitmask.
     */
    @AllArgsConstructor
    class FlagUtil {
        long flag;

        public boolean isSet(long value) {
            return flag == 0 ?
                    value == 0 :
                    (flag & value) == flag;
        }

        public static <F> List<F> fromValue(F[] values, long value) {
            return Arrays.stream(values)
                    .filter(f -> ((Flags) f).isSet(value))
                    .collect(Collectors.toList());
        }
    }
}
