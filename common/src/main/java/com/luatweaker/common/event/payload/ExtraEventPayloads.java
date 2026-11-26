package com.luatweaker.common.event.payload;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaFunction;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import com.mojang.brigadier.ParseResults;
import dev.architectury.event.events.common.ChatEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Payload builders for extended Architectury common game events.
 */
public final class ExtraEventPayloads {

    private static final String DIMENSION_KEY = "dimension";
    private static final String ENTITY_LOWER = "entity";
    private static final String ENTITY_UPPER = "Entity";

    private ExtraEventPayloads() {
    }

    public static @NotNull ILuaTable buildChat(@NotNull ILuaEngine engine, @Nullable Player player,
                                              @Nullable Component component,
                                              @Nullable ChatEvent.ChatComponent mutator) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putPlayer(engine, table, player);
        String text = component != null ? component.getString() : "";
        PayloadTables.putBoth(table, "message", engine.wrapString(text));
        if (mutator != null) {
            ILuaFunction setFn = args -> {
                int off = LuaBinder.getOffset(args);
                if (args.length > off && !args[off].isNil()) {
                    mutator.set(Component.literal(args[off].asString()));
                }
                return engine.nilValue();
            };
            PayloadTables.putBoth(table, "setMessage", setFn);
        }
        return table;
    }

    public static @NotNull ILuaTable buildCommandPerform(@NotNull ILuaEngine engine,
                                                        @NotNull ParseResults<CommandSourceStack> results) {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "command", engine.wrapString(results.getReader().getString()));
        CommandSourceStack source = results.getContext().getSource();
        PayloadTables.putBoth(table, "sourceName", engine.wrapString(source.getTextName()));
        Player player = source.getPlayer();
        if (player != null) {
            PayloadEntities.putPlayer(engine, table, player);
        }
        return table;
    }

    public static @NotNull ILuaTable buildChunk(@NotNull ILuaEngine engine, @Nullable Level level,
                                               int chunkX, int chunkZ) {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "chunkX", engine.wrapNumber(chunkX));
        PayloadTables.putBoth(table, "chunkZ", engine.wrapNumber(chunkZ));
        if (level != null) {
            PayloadTables.putBoth(table, DIMENSION_KEY, engine.wrapString(level.dimension().location().toString()));
        }
        return table;
    }

    public static @NotNull ILuaTable buildLightning(@NotNull ILuaEngine engine, @NotNull Level level,
                                                   @NotNull Vec3 pos, @Nullable List<Entity> affected) {
        ILuaTable table = engine.createTable();
        PayloadTables.putBoth(table, "x", engine.wrapNumber(pos.x));
        PayloadTables.putBoth(table, "y", engine.wrapNumber(pos.y));
        PayloadTables.putBoth(table, "z", engine.wrapNumber(pos.z));
        PayloadTables.putBoth(table, DIMENSION_KEY, engine.wrapString(level.dimension().location().toString()));
        if (affected != null && !affected.isEmpty()) {
            ILuaTable list = engine.createTable();
            int idx = 1;
            for (Entity entity : affected) {
                ILuaTable wrapped = PayloadEntities.wrap(engine, entity);
                if (wrapped != null) {
                    list.rawset(idx++, wrapped);
                }
            }
            PayloadTables.putBoth(table, "affectedEntities", list);
        }
        return table;
    }

    public static @NotNull ILuaTable buildFallingLand(@NotNull ILuaEngine engine, @NotNull Level level,
                                                     @NotNull BlockPos pos, @NotNull BlockState fallState,
                                                     @NotNull BlockState landOn, @Nullable Entity entity) {
        ILuaTable table = engine.createTable();
        PayloadBlocks.putBlockAndPos(engine, table, level, pos);
        PayloadTables.putBoth(table, "block",
                engine.wrapString(BuiltInRegistries.BLOCK.getKey(fallState.getBlock()).toString()));
        PayloadTables.putBoth(table, "landedOn",
                engine.wrapString(BuiltInRegistries.BLOCK.getKey(landOn.getBlock()).toString()));
        PayloadEntities.putEntity(engine, table, ENTITY_LOWER, ENTITY_UPPER, entity);
        return table;
    }

    public static @NotNull ILuaTable buildFarmlandTrample(@NotNull ILuaEngine engine, @NotNull Level level,
                                                         @NotNull BlockPos pos, float distance,
                                                         @Nullable Entity entity) {
        ILuaTable table = engine.createTable();
        PayloadBlocks.putBlockAndPos(engine, table, level, pos);
        PayloadTables.putBoth(table, "distance", engine.wrapNumber(distance));
        if (entity instanceof Player player) {
            PayloadEntities.putPlayer(engine, table, player);
        } else {
            PayloadEntities.putEntity(engine, table, ENTITY_LOWER, ENTITY_UPPER, entity);
        }
        return table;
    }

    public static @NotNull ILuaTable buildSpawnCheck(@NotNull ILuaEngine engine, @NotNull Entity entity,
                                                    @NotNull LevelAccessor world, double x, double y, double z,
                                                    @NotNull MobSpawnType reason) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putEntity(engine, table, ENTITY_LOWER, ENTITY_UPPER, entity);
        PayloadTables.putBoth(table, "x", engine.wrapNumber(x));
        PayloadTables.putBoth(table, "y", engine.wrapNumber(y));
        PayloadTables.putBoth(table, "z", engine.wrapNumber(z));
        PayloadTables.putBoth(table, "spawnReason", engine.wrapString(reason.name()));
        if (world instanceof Level level) {
            PayloadTables.putBoth(table, DIMENSION_KEY, engine.wrapString(level.dimension().location().toString()));
        }
        return table;
    }

    public static @NotNull ILuaTable buildEnterSection(@NotNull ILuaEngine engine, @NotNull Entity entity,
                                                      int sx, int sy, int sz, int px, int py, int pz) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putEntity(engine, table, ENTITY_LOWER, ENTITY_UPPER, entity);
        PayloadTables.putBoth(table, "sectionX", engine.wrapNumber(sx));
        PayloadTables.putBoth(table, "sectionY", engine.wrapNumber(sy));
        PayloadTables.putBoth(table, "sectionZ", engine.wrapNumber(sz));
        PayloadTables.putBoth(table, "prevX", engine.wrapNumber(px));
        PayloadTables.putBoth(table, "prevY", engine.wrapNumber(py));
        PayloadTables.putBoth(table, "prevZ", engine.wrapNumber(pz));
        return table;
    }

    public static @NotNull ILuaTable buildAnimalTame(@NotNull ILuaEngine engine, @NotNull Entity animal,
                                                    @NotNull Player player) {
        ILuaTable table = engine.createTable();
        PayloadEntities.putEntity(engine, table, ENTITY_LOWER, ENTITY_UPPER, animal);
        PayloadEntities.putPlayer(engine, table, player);
        return table;
    }
}
