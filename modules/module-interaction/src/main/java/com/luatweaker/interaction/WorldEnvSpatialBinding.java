package com.luatweaker.interaction;

import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds spatial environment queries and spawn access (GetBiome, GetLightLevel, CanSeeSky, GetHighestBlockY, IsChunkLoaded, GetWorldSpawn, SetWorldSpawn). */
final class WorldEnvSpatialBinding {

    private static final String DEFAULT_LIGHT_KIND = "all";
    private static final String DEFAULT_HEIGHTMAP = "motion_blocking";

    private WorldEnvSpatialBinding() {
    }

    static void bind(ILuaEngine engine, ILuaTable table) {
        bindBiomeAndLight(engine, table);
        bindColumnsAndSpawn(engine, table);
    }

    private static void bindBiomeAndLight(ILuaEngine engine, ILuaTable table) {
        WorldEnvSupport.bindPair(table, "GetBiome", args -> {
            InteractionBindingSupport.CoordinateArgs coords = InteractionParse.readCoordinates(args, "World:GetBiome");
            if (coords == null) {
                return engine.nilValue();
            }
            String biome = Platform.getInteraction().getBiome(
                    coords.dimension(), coords.x(), coords.y(), coords.z());
            return biome != null ? engine.wrapString(biome) : engine.nilValue();
        });

        WorldEnvSupport.bindPair(table, "GetLightLevel", args -> {
            InteractionBindingSupport.CoordinateArgs coords =
                    InteractionParse.readCoordinates(args, "World:GetLightLevel");
            if (coords == null) {
                return engine.nilValue();
            }
            String kind = WorldEnvSupport.normalizeOrDefault(args, coords.cursor(), DEFAULT_LIGHT_KIND);
            if (kind.isEmpty()) {
                return engine.nilValue();
            }
            int light = Platform.getInteraction().getLightLevel(
                    coords.dimension(), coords.x(), coords.y(), coords.z(), kind);
            return light >= 0 ? engine.wrapNumber(light) : engine.nilValue();
        });

        WorldEnvSupport.bindPair(table, "CanSeeSky", args -> {
            InteractionBindingSupport.CoordinateArgs coords =
                    InteractionParse.readCoordinates(args, "World:CanSeeSky");
            if (coords == null) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(Platform.getInteraction().canSeeSky(
                    coords.dimension(), coords.x(), coords.y(), coords.z()));
        });
    }

    private static void bindColumnsAndSpawn(ILuaEngine engine, ILuaTable table) {
        WorldEnvSupport.bindPair(table, "GetHighestBlockY", args -> {
            WorldEnvSupport.TwoCoords coords = WorldEnvSupport.readTwoCoords(args);
            if (coords == null) {
                return engine.nilValue();
            }
            String kind = WorldEnvSupport.normalizeOrDefault(args, coords.cursor(), DEFAULT_HEIGHTMAP);
            if (kind.isEmpty()) {
                return engine.nilValue();
            }
            int y = Platform.getInteraction().getHighestBlockY(
                    coords.dimension(), coords.x(), coords.z(), kind);
            return y > Integer.MIN_VALUE ? engine.wrapNumber(y) : engine.nilValue();
        });

        WorldEnvSupport.bindPair(table, "IsChunkLoaded", args -> {
            WorldEnvSupport.TwoCoords coords = WorldEnvSupport.readTwoCoords(args);
            if (coords == null) {
                return engine.wrapBoolean(false);
            }
            return engine.wrapBoolean(Platform.getInteraction().isChunkLoaded(
                    coords.dimension(), coords.x(), coords.z()));
        });

        WorldEnvSupport.bindPair(table, "GetWorldSpawn", args -> {
            String dim = WorldEnvSupport.readOptionalDimension(args, LuaBinder.getOffset(args));
            if (dim == null) {
                return engine.nilValue();
            }
            return WorldEnvSupport.mapToLuaTable(engine, Platform.getInteraction().getWorldSpawn(dim));
        });

        WorldEnvSupport.bindPair(table, "SetWorldSpawn", args ->
                engine.wrapBoolean(handleSetWorldSpawn(args)));
    }

    private static boolean handleSetWorldSpawn(ILuaValue... args) {
        InteractionBindingSupport.CoordinateArgs coords =
                InteractionParse.readCoordinates(args, "World:SetWorldSpawn");
        if (coords == null) {
            return false;
        }
        float angle = 0.0f;
        int cursor = coords.cursor();
        if (cursor < args.length && !args[cursor].isNil()) {
            if (!WorldEnvSupport.isFiniteNumber(args[cursor])) {
                return false;
            }
            angle = (float) args[cursor].asDouble();
        }
        return Platform.getInteraction().setWorldSpawn(
                coords.dimension(), coords.x(), coords.y(), coords.z(), angle);
    }
}
