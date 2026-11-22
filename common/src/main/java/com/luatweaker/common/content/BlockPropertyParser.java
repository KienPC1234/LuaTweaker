package com.luatweaker.common.content;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses the string-based block properties configured from Lua
 * ({@code mapColor}, {@code pushReaction}, {@code offsetType}) into the real
 * Minecraft enums. Unknown values are logged loudly and yield {@code null}
 * (the property is skipped) instead of crashing block registration.
 */
public final class BlockPropertyParser {

    private static final Map<String, MapColor> MAP_COLORS = buildMapColors();

    private BlockPropertyParser() {}

    @Nullable
    public static MapColor parseMapColor(String input) {
        if (input == null || input.isBlank()) { return null; }
        String name = input.trim().toUpperCase(Locale.ROOT);
        if (name.matches("\\d+")) {
            try {
                return MapColor.byId(Integer.parseInt(name));
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Invalid mapColor id '" + input + "' (must be 0-61): " + e.getMessage());
                return null;
            }
        }
        MapColor color = MAP_COLORS.get(name);
        if (color == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM, "Unknown mapColor '" + input + "' - valid names: STONE, DIRT, WOOD, METAL, GOLD, DIAMOND, COLOR_RED, TERRACOTTA_ORANGE, ...");
        }
        return color;
    }

    @Nullable
    public static PushReaction parsePushReaction(String input) {
        if (input == null || input.isBlank()) { return null; }
        return switch (input.trim().toUpperCase(Locale.ROOT)) {
            case "NORMAL" -> PushReaction.NORMAL;
            case "DESTROY" -> PushReaction.DESTROY;
            case "BLOCK" -> PushReaction.BLOCK;
            case "IGNORE" -> PushReaction.IGNORE;
            case "PUSH_ONLY" -> PushReaction.PUSH_ONLY;
            default -> {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Unknown pushReaction '" + input + "' - valid values: NORMAL, DESTROY, BLOCK, IGNORE, PUSH_ONLY");
                yield null;
            }
        };
    }

    @Nullable
    public static BlockBehaviour.OffsetType parseOffsetType(String input) {
        if (input == null || input.isBlank()) { return null; }
        return switch (input.trim().toUpperCase(Locale.ROOT)) {
            case "NONE" -> BlockBehaviour.OffsetType.NONE;
            case "XZ" -> BlockBehaviour.OffsetType.XZ;
            case "XYZ" -> BlockBehaviour.OffsetType.XYZ;
            default -> {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Unknown offsetType '" + input + "' - valid values: NONE, XZ, XYZ");
                yield null;
            }
        };
    }

    private static Map<String, MapColor> buildMapColors() {
        Map<String, MapColor> colors = new ConcurrentHashMap<>();
        putBaseColors(colors);
        putDyePalette(colors);
        putTerracottaPalette(colors);
        putNetherWoodPalette(colors);
        return colors;
    }

    private static void putBaseColors(Map<String, MapColor> colors) {
        colors.put("NONE", MapColor.NONE);
        colors.put("GRASS", MapColor.GRASS);
        colors.put("SAND", MapColor.SAND);
        colors.put("WOOL", MapColor.WOOL);
        colors.put("FIRE", MapColor.FIRE);
        colors.put("ICE", MapColor.ICE);
        colors.put("METAL", MapColor.METAL);
        colors.put("PLANT", MapColor.PLANT);
        colors.put("SNOW", MapColor.SNOW);
        colors.put("CLAY", MapColor.CLAY);
        colors.put("DIRT", MapColor.DIRT);
        colors.put("STONE", MapColor.STONE);
        colors.put("WATER", MapColor.WATER);
        colors.put("WOOD", MapColor.WOOD);
        colors.put("QUARTZ", MapColor.QUARTZ);
        colors.put("GOLD", MapColor.GOLD);
        colors.put("DIAMOND", MapColor.DIAMOND);
        colors.put("LAPIS", MapColor.LAPIS);
        colors.put("EMERALD", MapColor.EMERALD);
        colors.put("PODZOL", MapColor.PODZOL);
        colors.put("NETHER", MapColor.NETHER);
        colors.put("DEEPSLATE", MapColor.DEEPSLATE);
        colors.put("RAW_IRON", MapColor.RAW_IRON);
        colors.put("GLOW_LICHEN", MapColor.GLOW_LICHEN);
    }

    /** COLOR_* (dye palette) with plain aliases: "RED" -> COLOR_RED. */
    private static void putDyePalette(Map<String, MapColor> colors) {
        putAlias(colors, "ORANGE", MapColor.COLOR_ORANGE);
        putAlias(colors, "MAGENTA", MapColor.COLOR_MAGENTA);
        putAlias(colors, "LIGHT_BLUE", MapColor.COLOR_LIGHT_BLUE);
        putAlias(colors, "YELLOW", MapColor.COLOR_YELLOW);
        putAlias(colors, "LIGHT_GREEN", MapColor.COLOR_LIGHT_GREEN);
        putAlias(colors, "PINK", MapColor.COLOR_PINK);
        putAlias(colors, "GRAY", MapColor.COLOR_GRAY);
        putAlias(colors, "LIGHT_GRAY", MapColor.COLOR_LIGHT_GRAY);
        putAlias(colors, "CYAN", MapColor.COLOR_CYAN);
        putAlias(colors, "PURPLE", MapColor.COLOR_PURPLE);
        putAlias(colors, "BLUE", MapColor.COLOR_BLUE);
        putAlias(colors, "BROWN", MapColor.COLOR_BROWN);
        putAlias(colors, "GREEN", MapColor.COLOR_GREEN);
        putAlias(colors, "RED", MapColor.COLOR_RED);
        putAlias(colors, "BLACK", MapColor.COLOR_BLACK);
    }

    private static void putTerracottaPalette(Map<String, MapColor> colors) {
        colors.put("TERRACOTTA_WHITE", MapColor.TERRACOTTA_WHITE);
        colors.put("TERRACOTTA_ORANGE", MapColor.TERRACOTTA_ORANGE);
        colors.put("TERRACOTTA_MAGENTA", MapColor.TERRACOTTA_MAGENTA);
        colors.put("TERRACOTTA_LIGHT_BLUE", MapColor.TERRACOTTA_LIGHT_BLUE);
        colors.put("TERRACOTTA_YELLOW", MapColor.TERRACOTTA_YELLOW);
        colors.put("TERRACOTTA_LIGHT_GREEN", MapColor.TERRACOTTA_LIGHT_GREEN);
        colors.put("TERRACOTTA_PINK", MapColor.TERRACOTTA_PINK);
        colors.put("TERRACOTTA_GRAY", MapColor.TERRACOTTA_GRAY);
        colors.put("TERRACOTTA_LIGHT_GRAY", MapColor.TERRACOTTA_LIGHT_GRAY);
        colors.put("TERRACOTTA_CYAN", MapColor.TERRACOTTA_CYAN);
        colors.put("TERRACOTTA_PURPLE", MapColor.TERRACOTTA_PURPLE);
        colors.put("TERRACOTTA_BLUE", MapColor.TERRACOTTA_BLUE);
        colors.put("TERRACOTTA_BROWN", MapColor.TERRACOTTA_BROWN);
        colors.put("TERRACOTTA_GREEN", MapColor.TERRACOTTA_GREEN);
        colors.put("TERRACOTTA_RED", MapColor.TERRACOTTA_RED);
        colors.put("TERRACOTTA_BLACK", MapColor.TERRACOTTA_BLACK);
    }

    private static void putNetherWoodPalette(Map<String, MapColor> colors) {
        colors.put("CRIMSON_NYLIUM", MapColor.CRIMSON_NYLIUM);
        colors.put("CRIMSON_STEM", MapColor.CRIMSON_STEM);
        colors.put("CRIMSON_HYPHAE", MapColor.CRIMSON_HYPHAE);
        colors.put("WARPED_NYLIUM", MapColor.WARPED_NYLIUM);
        colors.put("WARPED_STEM", MapColor.WARPED_STEM);
        colors.put("WARPED_HYPHAE", MapColor.WARPED_HYPHAE);
        colors.put("WARPED_WART_BLOCK", MapColor.WARPED_WART_BLOCK);
    }

    private static void putAlias(Map<String, MapColor> colors, String name, MapColor color) {
        colors.put("COLOR_" + name, color);
        colors.put(name, color);
    }
}
