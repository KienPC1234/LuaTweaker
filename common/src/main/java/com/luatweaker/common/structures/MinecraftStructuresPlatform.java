package com.luatweaker.common.structures;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformStructures;
import com.luatweaker.common.interaction.ServerLevels;
import com.luatweaker.structures.NbtStructureLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import com.luatweaker.common.CommonServerHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Runtime structure placement on NeoForge.
 *
 * <p>Template lookup goes through the level's {@code StructureTemplateManager},
 * so both datapack NBT files and the virtual pack's physical-file fallback
 * resolve the same way natural generation does. Method shapes verified
 * against 1.21.1 ({@code StructureTemplateManager.get},
 * {@code StructureTemplate.placeInWorld(..., StructurePlaceSettings, RandomSource, int)}).
 */
public class MinecraftStructuresPlatform implements IPlatformStructures {

    /**
     * Block-update flags for placement: update neighbors + sync to clients,
     * the same combination {@code /setblock} uses ({@code Block.UPDATE_ALL}).
     */
    private static final int PLACE_FLAGS = net.minecraft.world.level.block.Block.UPDATE_ALL;

    @Override
    public boolean placeTemplate(@NotNull String templateId, @NotNull String dimensionId,
                                 int x, int y, int z,
                                 @NotNull String rotation, @NotNull String mirror) {
        ResourceLocation dimensionRl = ResourceLocation.tryParse(dimensionId);
        if (dimensionRl == null) {
            throw new IllegalArgumentException("dimensionId must be a valid resource location, got: " + dimensionId);
        }
        return ServerLevels.map(resolveLevel(dimensionRl, dimensionId), false,
                level -> placeOn(level, templateId, dimensionId, x, y, z, rotation, mirror));
    }

    @Nullable
    private static ServerLevel resolveLevel(ResourceLocation dimensionRl, String dimensionId) {
        return levelOf(CommonServerHolder.get(), dimensionRl, dimensionId);
    }

    @Nullable
    private static ServerLevel levelOf(@Nullable MinecraftServer server, ResourceLocation dimensionRl,
                                       String dimensionId) {
        if (server == null) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[StructuresPlatform] No server running; cannot place template in '" + dimensionId + "'.");
            return null;
        }
        Optional<ServerLevel> level =
                Optional.ofNullable(server.getLevel(ResourceKey.create(Registries.DIMENSION, dimensionRl)));
        if (level.isEmpty()) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[StructuresPlatform] Dimension level '" + dimensionId + "' is not loaded.");
            return null;
        }
        return level.get();
    }

    private static boolean placeOn(ServerLevel level, String templateId, String dimensionId,
                                   int x, int y, int z, String rotation, String mirror) {
        ResourceLocation templateRl = ResourceLocation.tryParse(NbtStructureLoader.templateLocation(templateId));
        if (templateRl == null) {
            throw new IllegalArgumentException("template must be 'namespace:path', got: " + templateId);
        }
        Optional<StructureTemplate> template = level.getStructureManager().get(templateRl);
        if (template.isEmpty()) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "[StructuresPlatform] Template '" + templateRl + "' not found in dimension '"
                            + dimensionId + "'.");
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(parseRotation(rotation))
                .setMirror(parseMirror(mirror))
                .setIgnoreEntities(false);
        BlockPos pos = new BlockPos(x, y, z);
        boolean placed = template.get().placeInWorld(level, pos, pos, settings, level.getRandom(), PLACE_FLAGS);
        if (!placed) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[StructuresPlatform] placeInWorld reported failure for '" + templateRl
                            + "' at (" + x + ", " + y + ", " + z + ").");
        }
        return placed;
    }

    private static @NotNull Rotation parseRotation(@NotNull String rotation) {
        return switch (rotation == null ? "" : rotation.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "none" -> Rotation.NONE;
            case "clockwise_90" -> Rotation.CLOCKWISE_90;
            case "clockwise_180" -> Rotation.CLOCKWISE_180;
            case "counterclockwise_90" -> Rotation.COUNTERCLOCKWISE_90;
            default -> throw new IllegalArgumentException(
                    "rotation must be one of none|clockwise_90|clockwise_180|counterclockwise_90, got: " + rotation);
        };
    }

    private static @NotNull Mirror parseMirror(@NotNull String mirror) {
        return switch (mirror == null ? "" : mirror.strip().toLowerCase(java.util.Locale.ROOT)) {
            case "none" -> Mirror.NONE;
            case "left_right" -> Mirror.LEFT_RIGHT;
            case "front_back" -> Mirror.FRONT_BACK;
            default -> throw new IllegalArgumentException(
                    "mirror must be one of none|left_right|front_back, got: " + mirror);
        };
    }
}

