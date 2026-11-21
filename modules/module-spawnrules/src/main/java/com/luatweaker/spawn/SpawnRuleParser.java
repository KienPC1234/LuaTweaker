package com.luatweaker.spawn;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Parses the table passed to {@code SpawnRules:AddRule} into an immutable {@link SpawnRule}.
 * Every error names the offending field, so a broken script fails at registration time
 * instead of silently spawning nothing.
 */
public final class SpawnRuleParser {

    /** Dimension ids default to a namespace but a bare path is still accepted. */
    private static final Pattern DIMENSION_ID =
            Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    private static final int WORLD_MIN_Y = -2032;
    private static final int WORLD_MAX_Y = 2032;
    private static final int MAX_GROUP = 128;
    private static final int MAX_WEIGHT = 1000;

    private record Identity(String entity, String category) {}

    private record Groups(int weight, int minGroup, int maxGroup) {}

    private SpawnRuleParser() {}

    /**
     * @throws IllegalArgumentException when the rule is not a table or a field is invalid
     */
    @NotNull
    public static SpawnRule parse(@NotNull String dimensionId, @NotNull ILuaValue value) {
        String where = "spawn rule for '" + dimensionId + "'";
        if (value == null || value.isNil() || !value.isTable()) {
            throw new IllegalArgumentException(where + " must be a table");
        }
        ILuaTable table = value.asTable();

        Identity identity = parseIdentity(table, where);
        Groups groups = parseGroups(table, where);

        int[] y = SpawnRuleFieldReaders.range(table, "y", WORLD_MIN_Y, WORLD_MAX_Y, where);
        int[] light = SpawnRuleFieldReaders.range(table, "light", 0, 15, where);
        int[] skyLight = SpawnRuleFieldReaders.range(table, "skyLight", 0, 15, where);

        Boolean requireDay = timeCondition(table, where);
        Boolean[] weather = weatherCondition(table, where);

        return new SpawnRule(
                identity.entity(), identity.category(), groups.weight(), groups.minGroup(), groups.maxGroup(),
                y[0], y[1],
                light[0], light[1],
                skyLight[0], skyLight[1],
                requireDay, weather[0], weather[1],
                SpawnRuleListReaders.stringList(table, "biomes", false, where),
                SpawnRuleListReaders.stringList(table, "blockBelow", true, where),
                SpawnRuleFieldReaders.boolField(table, "solidGround", false, where),
                SpawnRuleFieldReaders.functionField(table, "check", where));
    }

    @NotNull
    private static Identity parseIdentity(@NotNull ILuaTable table, @NotNull String where) {
        String entity = SpawnRuleFieldReaders.requiredString(table, "entity", where);
        if (!SpawnRuleListReaders.isEntityId(entity)) {
            throw new IllegalArgumentException(where + ".entity must be a valid entity id, got: " + entity);
        }
        String category = SpawnRuleFieldReaders.optionalString(table, "category", where);
        if (category != null && !category.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException(where + ".category must look like 'monster', got: " + category);
        }
        return new Identity(entity, category);
    }

    @NotNull
    private static Groups parseGroups(@NotNull ILuaTable table, @NotNull String where) {
        int weight = SpawnRuleFieldReaders.intField(table, "weight", 10, 0, MAX_WEIGHT, where);
        int minGroup = SpawnRuleFieldReaders.intField(table, "minGroup", 1, 1, MAX_GROUP, where);
        int maxGroup = SpawnRuleFieldReaders.intField(table, "maxGroup", minGroup, minGroup, MAX_GROUP, where);
        if (maxGroup < minGroup) {
            throw new IllegalArgumentException(where + ".maxGroup (" + maxGroup
                    + ") must be >= minGroup (" + minGroup + ")");
        }
        return new Groups(weight, minGroup, maxGroup);
    }

    /** @return true = day only, false = night only, null = any time */
    @Nullable
    private static Boolean timeCondition(@NotNull ILuaTable table, @NotNull String where) {
        String time = SpawnRuleFieldReaders.optionalString(table, "time", where);
        if (time == null) {
            return null;
        }
        return switch (time.toLowerCase(Locale.ROOT)) {
            case "day" -> Boolean.TRUE;
            case "night" -> Boolean.FALSE;
            default -> throw new IllegalArgumentException(where
                    + ".time must be 'day' or 'night', got: " + time);
        };
    }

    /** @return {requireRaining, requireThundering}; null entries mean "any weather" */
    @Nullable
    private static Boolean[] weatherCondition(@NotNull ILuaTable table, @NotNull String where) {
        String weather = SpawnRuleFieldReaders.optionalString(table, "weather", where);
        if (weather == null) {
            return new Boolean[]{null, null};
        }
        return parseWeatherName(weather, where);
    }

    @NotNull
    private static Boolean[] parseWeatherName(@NotNull String weather, @NotNull String where) {
        return switch (weather.toLowerCase(Locale.ROOT)) {
            case "clear", "sunny" -> new Boolean[]{Boolean.FALSE, Boolean.FALSE};
            case "rain", "raining" -> new Boolean[]{Boolean.TRUE, null};
            case "thunder", "thundering", "storm" -> new Boolean[]{null, Boolean.TRUE};
            default -> throw new IllegalArgumentException(where
                    + ".weather must be 'clear', 'rain' or 'thunder', got: " + weather);
        };
    }

    static void validateDimensionId(String name, String id) {
        if (id == null || !DIMENSION_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: " + id);
        }
    }
}
