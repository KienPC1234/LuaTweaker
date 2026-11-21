package com.luatweaker.dimension;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Parses {@code Dimensions:Create} Lua tables into {@link DimensionConfig}.
 * Pure validation: every shape error throws {@link IllegalArgumentException}.
 */
final class DimensionConfigParser {

    private DimensionConfigParser() {
    }

    static DimensionConfig parse(@NotNull String dimensionId, @NotNull ILuaTable t) {
        boolean hasSkyLight = bool(t, "hasSkyLight", true);
        boolean hasCeiling = bool(t, "hasCeiling", false);
        boolean ultraWarm = bool(t, "ultraWarm", false);
        boolean natural = bool(t, "natural", true);
        double coordinateScale = parseCoordinateScale(t);
        ResourceFields resources = parseResourceFields(t);

        int skyColor = intColor(t, "skyColor", DimensionConfig.DEFAULT_SKY_COLOR);
        int fogColor = intColor(t, "fogColor", DimensionConfig.DEFAULT_FOG_COLOR);
        double ambientLight = LuaTableValues.number(t, "ambientLight", DimensionConfig.DEFAULT_AMBIENT_LIGHT);
        if (ambientLight < 0.0 || ambientLight > 1.0) {
            throw new IllegalArgumentException("ambientLight must be between 0.0 and 1.0, got: " + ambientLight);
        }

        DimensionBoundsParser.VerticalBounds bounds = DimensionBoundsParser.parseVerticalBounds(t);
        DimensionBoundsParser.SurfaceBlocks surface = DimensionBoundsParser.parseSurfaceBlocks(t);
        boolean hasBedrock = bool(t, "hasBedrock", false);
        int biomeSize = LuaTableValues.intRange(t, "biomeSize", DimensionConfig.DEFAULT_BIOME_SIZE, 1, 128);
        Double spawnX = LuaTableValues.optionalNumber(t, "spawnX");
        Double spawnZ = LuaTableValues.optionalNumber(t, "spawnZ");
        if ((spawnX == null) != (spawnZ == null)) {
            throw new IllegalArgumentException("spawnX and spawnZ must be set together (or both omitted)");
        }
        Integer spawnXInt = spawnX == null ? null : (int) Math.floor(spawnX);
        Integer spawnZInt = spawnZ == null ? null : (int) Math.floor(spawnZ);

        List<DimensionConfig.BiomeEntry> biomes = DimensionListParser.parseBiomes(t.rawget("biomes"));
        List<DimensionConfig.SpawnEntry> spawnEntities = DimensionListParser.parseSpawnEntities(t.rawget("spawnEntities"));

        return new DimensionConfig(
                dimensionId,
                hasSkyLight, hasCeiling, ultraWarm, natural, coordinateScale,
                bool(t, "bedWorks", true),
                bool(t, "respawnAnchorWorks", false),
                bool(t, "piglinSafe", false),
                bool(t, "hasRaids", false),
                LuaTableValues.intRange(t, "monsterSpawnLightLevel", 7, 0, 15),
                LuaTableValues.intRange(t, "monsterSpawnBlockLightLimit", 0, 0, 15),
                resources.infiniburn(), resources.effectsLocation(), resources.fixedTime(),
                skyColor, fogColor, ambientLight,
                bounds.seaLevel(), bounds.minHeight(), bounds.maxHeight(), bounds.logicalHeight(),
                surface.surface(), surface.subsurface(), surface.filler(), surface.water(),
                hasBedrock, biomeSize, spawnXInt, spawnZInt,
                biomes, spawnEntities
        );
    }

    private static double parseCoordinateScale(@NotNull ILuaTable t) {
        double coordinateScale = LuaTableValues.number(t, "coordinateScale", 1.0);
        if (!Double.isFinite(coordinateScale)
                || coordinateScale < DimensionConfig.MIN_COORDINATE_SCALE
                || coordinateScale > DimensionConfig.MAX_COORDINATE_SCALE) {
            throw new IllegalArgumentException("coordinateScale must be between "
                    + DimensionConfig.MIN_COORDINATE_SCALE + " and " + DimensionConfig.MAX_COORDINATE_SCALE
                    + " (vanilla DimensionType range), got: " + coordinateScale);
        }
        return coordinateScale;
    }

    private static boolean bool(@NotNull ILuaTable t, String key, boolean def) {
        ILuaValue v = t.rawget(key);
        if (v != null && !v.isNil()) {
            return v.asBoolean();
        }
        return def;
    }

    private static int intColor(@NotNull ILuaTable t, String key, int def) {
        int value = LuaTableValues.intRange(t, key, def, 0, 0xFFFFFF);
        if (!DimensionConfig.isColor(value)) {
            throw new IllegalArgumentException(key + " must be an RGB color 0x000000-0xFFFFFF, got: " + value);
        }
        return value;
    }

    private record ResourceFields(String infiniburn, String effectsLocation, Long fixedTime) {
    }

    private static ResourceFields parseResourceFields(@NotNull ILuaTable t) {
        String infiniburn = parseInfiniburn(t);
        String effectsLocation = LuaTableValues.string(t, "effectsLocation", DimensionConfig.DEFAULT_EFFECTS);
        if (!effectsLocation.contains(":") || !LuaTableValues.RESOURCE_LOCATION.matcher(effectsLocation).matches()) {
            throw new IllegalArgumentException("effectsLocation must be a namespaced resource location "
                    + "(e.g. 'minecraft:the_nether'), got: " + effectsLocation);
        }
        String terrain = LuaTableValues.string(t, "terrain", "custom");
        if (!"custom".equals(terrain)) {
            throw new IllegalArgumentException("terrain must be 'custom' (vanilla generators are not supported), got: " + terrain);
        }
        return new ResourceFields(infiniburn, effectsLocation, optionalLong(t));
    }

    private static String parseInfiniburn(@NotNull ILuaTable t) {
        String infiniburn = LuaTableValues.string(t, "infiniburn", DimensionConfig.DEFAULT_INFINIBURN);
        if (!infiniburn.startsWith("#") || infiniburn.length() < 2
                || !LuaTableValues.RESOURCE_LOCATION.matcher(infiniburn.substring(1)).matches()) {
            throw new IllegalArgumentException("infiniburn must be a block tag (e.g. '#minecraft:infiniburn_overworld'), got: "
                    + infiniburn);
        }
        return infiniburn;
    }

    @Nullable
    static Integer validateSeaLevel(@NotNull ILuaTable table, @NotNull String dimensionId,
            @NotNull DimensionConfig config) {
        Integer seaLevel = optionalInt(table, "seaLevel");
        if (seaLevel != null && (seaLevel <= config.minHeight() || seaLevel >= config.maxHeight())) {
            throw new IllegalArgumentException("terrain spec seaLevel (" + seaLevel + ") for '" + dimensionId
                    + "' must be between minHeight (" + config.minHeight() + ") and maxHeight ("
                    + config.maxHeight() + ")");
        }
        return seaLevel;
    }

    private static Long optionalLong(@NotNull ILuaTable t) {
        Double value = LuaTableValues.optionalNumber(t, "fixedTime");
        return value == null ? null : (long) Math.floor(value);
    }

    private static Integer optionalInt(@NotNull ILuaTable t, String key) {
        Double value = LuaTableValues.optionalNumber(t, key);
        return value == null ? null : (int) Math.floor(value);
    }
}
