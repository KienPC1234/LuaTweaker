package com.luatweaker.core.remap;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Picks the best overload from reflection candidates by parameter compatibility.
 */
final class OverloadResolver {
    private OverloadResolver() {}

    /** @return the winning overload, or {@code null} when nothing is compatible. */
    static @Nullable Method resolveOverload(@NotNull List<Method> candidates,
            @Nullable Class<?>... paramHints) {
        if (paramHints == null || paramHints.length == 0) {
            return candidates.stream()
                    .min(Comparator.comparingInt(Method::getParameterCount))
                    .orElse(null);
        }
        List<Method> exactParamMatch = new ArrayList<>();
        List<Method> compatibleParamMatch = new ArrayList<>();
        for (Method candidate : candidates) {
            Class<?>[] methodParams = candidate.getParameterTypes();
            if (methodParams.length != paramHints.length) {
                continue;
            }
            int verdict = compareParameters(methodParams, paramHints);
            if (verdict == 0) {
                exactParamMatch.add(candidate);
            } else if (verdict > 0) {
                compatibleParamMatch.add(candidate);
            }
        }
        if (!exactParamMatch.isEmpty()) {
            return exactParamMatch.get(0);
        }
        if (!compatibleParamMatch.isEmpty()) {
            return compatibleParamMatch.get(0);
        }
        return candidates.get(0);
    }

    /**
     * @return 0 for an exact match, positive for a compatible one, negative
     *         when any parameter is incompatible
     */
    private static int compareParameters(@NotNull Class<?>[] methodParams, @NotNull Class<?>... hints) {
        boolean exactMatch = true;
        for (int i = 0; i < methodParams.length; i++) {
            if (methodParams[i].equals(hints[i])) {
                continue;
            }
            if (methodParams[i].isAssignableFrom(hints[i])
                    || ObfuscationHeuristics.isBoxingCompatible(methodParams[i], hints[i])) {
                exactMatch = false;
            } else {
                return -1;
            }
        }
        return exactMatch ? 0 : 1;
    }
}
