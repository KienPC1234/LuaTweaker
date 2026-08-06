package com.luatweaker.platform.content;

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
        if (input == null || input.isBlank()) return null;
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
        if (input == null || input.isBlank()) return null;
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
        if (input == null || input.isBlank()) return null;
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
        // COLOR_* (dye palette) with plain aliases: "RED" -> COLOR_RED
        colors.put("COLOR_ORANGE", MapColor.COLOR_ORANGE);
        colors.put("ORANGE", MapColor.COLOR_ORANGE);
        colors.put("COLOR_MAGENTA", MapColor.COLOR_MAGENTA);
        colors.put("MAGENTA", MapColor.COLOR_MAGENTA);
        colors.put("COLOR_LIGHT_BLUE", MapColor.COLOR_LIGHT_BLUE);
        colors.put("LIGHT_BLUE", MapColor.COLOR_LIGHT_BLUE);
        colors.put("COLOR_YELLOW", MapColor.COLOR_YELLOW);
        colors.put("YELLOW", MapColor.COLOR_YELLOW);
        colors.put("COLOR_LIGHT_GREEN", MapColor.COLOR_LIGHT_GREEN);
        colors.put("LIGHT_GREEN", MapColor.COLOR_LIGHT_GREEN);
        colors.put("COLOR_PINK", MapColor.COLOR_PINK);
        colors.put("PINK", MapColor.COLOR_PINK);
        colors.put("COLOR_GRAY", MapColor.COLOR_GRAY);
        colors.put("GRAY", MapColor.COLOR_GRAY);
        colors.put("COLOR_LIGHT_GRAY", MapColor.COLOR_LIGHT_GRAY);
        colors.put("LIGHT_GRAY", MapColor.COLOR_LIGHT_GRAY);
        colors.put("COLOR_CYAN", MapColor.COLOR_CYAN);
        colors.put("CYAN", MapColor.COLOR_CYAN);
        colors.put("COLOR_PURPLE", MapColor.COLOR_PURPLE);
        colors.put("PURPLE", MapColor.COLOR_PURPLE);
        colors.put("COLOR_BLUE", MapColor.COLOR_BLUE);
        colors.put("BLUE", MapColor.COLOR_BLUE);
        colors.put("COLOR_BROWN", MapColor.COLOR_BROWN);
        colors.put("BROWN", MapColor.COLOR_BROWN);
        colors.put("COLOR_GREEN", MapColor.COLOR_GREEN);
        colors.put("GREEN", MapColor.COLOR_GREEN);
        colors.put("COLOR_RED", MapColor.COLOR_RED);
        colors.put("RED", MapColor.COLOR_RED);
        colors.put("COLOR_BLACK", MapColor.COLOR_BLACK);
        colors.put("BLACK", MapColor.COLOR_BLACK);
        // TERRACOTTA_* palette
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
        // Nether wood palette
        colors.put("CRIMSON_NYLIUM", MapColor.CRIMSON_NYLIUM);
        colors.put("CRIMSON_STEM", MapColor.CRIMSON_STEM);
        colors.put("CRIMSON_HYPHAE", MapColor.CRIMSON_HYPHAE);
        colors.put("WARPED_NYLIUM", MapColor.WARPED_NYLIUM);
        colors.put("WARPED_STEM", MapColor.WARPED_STEM);
        colors.put("WARPED_HYPHAE", MapColor.WARPED_HYPHAE);
        colors.put("WARPED_WART_BLOCK", MapColor.WARPED_WART_BLOCK);
        return colors;
    }
}
