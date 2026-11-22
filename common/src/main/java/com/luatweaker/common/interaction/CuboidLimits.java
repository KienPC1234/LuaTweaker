package com.luatweaker.common.interaction;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Size-limit and component-count rule evaluation for {@link CuboidValidator}. */
final class CuboidLimits {

    static final String RESULT_VALID_KEY = "valid";
    static final String RESULT_ERROR_KEY = "error";
    static final String COUNT_KEY = "_count";
    static final String MATCH_KEY = "match";

    private CuboidLimits() {
    }

    @NotNull
    static Map<String, Object> invalid(@NotNull String error) {
        return Map.of(RESULT_VALID_KEY, false, RESULT_ERROR_KEY, error);
    }

    @Nullable
    static Map<String, Object> checkBounds(CuboidValidator.Box box, Map<String, Object> spec) {
        Map<String, Object> widthError = checkWidth(box, spec);
        if (widthError != null) {
            return widthError;
        }
        Map<String, Object> heightError = checkHeight(box, spec);
        return heightError != null ? heightError : checkDepth(box, spec);
    }

    @Nullable
    private static Map<String, Object> checkWidth(CuboidValidator.Box box, Map<String, Object> spec) {
        if (spec.get("minWidth") instanceof Number n && box.width() < n.intValue()) {
            return invalid("Width " + box.width() + " is smaller than minWidth " + n.intValue());
        }
        if (spec.get("maxWidth") instanceof Number n && box.width() > n.intValue()) {
            return invalid("Width " + box.width() + " is larger than maxWidth " + n.intValue());
        }
        return null;
    }

    @Nullable
    private static Map<String, Object> checkHeight(CuboidValidator.Box box, Map<String, Object> spec) {
        if (spec.get("minHeight") instanceof Number n && box.height() < n.intValue()) {
            return invalid("Height " + box.height() + " is smaller than minHeight " + n.intValue());
        }
        if (spec.get("maxHeight") instanceof Number n && box.height() > n.intValue()) {
            return invalid("Height " + box.height() + " is larger than maxHeight " + n.intValue());
        }
        return null;
    }

    @Nullable
    private static Map<String, Object> checkDepth(CuboidValidator.Box box, Map<String, Object> spec) {
        if (spec.get("minDepth") instanceof Number n && box.depth() < n.intValue()) {
            return invalid("Depth " + box.depth() + " is smaller than minDepth " + n.intValue());
        }
        if (spec.get("maxDepth") instanceof Number n && box.depth() > n.intValue()) {
            return invalid("Depth " + box.depth() + " is larger than maxDepth " + n.intValue());
        }
        return null;
    }

    @NotNull
    static List<Map<String, Object>> parseLimits(Map<String, Object> spec) {
        if (!(spec.get("limits") instanceof List<?> declared)) {
            return new ArrayList<>();
        }
        return declared.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(CuboidLimits::copyLimit)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @NotNull
    private static Map<String, Object> copyLimit(Map<?, ?> source) {
        var limitMap = new LinkedHashMap<String, Object>();
        source.forEach((key, value) -> limitMap.put(String.valueOf(key), value));
        limitMap.put(COUNT_KEY, 0);
        return limitMap;
    }

    @Nullable
    static Map<String, Object> checkLimits(List<Map<String, Object>> limits) {
        for (Map<String, Object> limit : limits) {
            int count = (int) limit.getOrDefault(COUNT_KEY, 0);
            if (limit.get("min") instanceof Number n && count < n.intValue()) {
                return invalid("Component " + limit.get(MATCH_KEY) + " count " + count
                        + " is less than minimum " + n.intValue());
            }
            if (limit.get("max") instanceof Number n && count > n.intValue()) {
                return invalid("Component " + limit.get(MATCH_KEY) + " count " + count
                        + " exceeds maximum " + n.intValue());
            }
        }
        return null;
    }
}
