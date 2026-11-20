package com.luatweaker.interaction;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.interaction.*;
import com.luatweaker.api.vm.*;
import com.luatweaker.entities.EntitiesLuaBinding;
import org.jetbrains.annotations.NotNull;

public class InteractionLuaBinding {

    private static final String DEFAULT_DIMENSION = "minecraft:overworld";
    /** Historical default for world sounds: category-level volume follows the Players slider. */
    private static final String DEFAULT_SOUND_SOURCE = "PLAYERS";
    /** Mirrors {@code net.minecraft.sounds.SoundSource}; used only to disambiguate StopSound args. */
    private static final java.util.Set<String> SOUND_SOURCE_NAMES = java.util.Set.of(
            "MASTER", "MUSIC", "RECORDS", "WEATHER", "BLOCKS", "HOSTILE", "NEUTRAL", "PLAYERS",
            "AMBIENT", "VOICE");

    public static void registerBindings(@NotNull ILuaEngine engine) {
        IInteractionService service = new InteractionServiceImpl();

        // One Lua shape for every raycast source: World:Raycast and IEntity:Raycast both
        // return this table (the raw record would otherwise arrive as opaque userdata).
        com.luatweaker.core.bind.LuaBinder.registerReturnConverter(
                com.luatweaker.api.spatial.RaycastResult.class,
                (returnEngine, value) -> value instanceof com.luatweaker.api.spatial.RaycastResult result
                        ? wrapRaycastResult(returnEngine, service, null, result)
                        : returnEngine.nilValue());
        
        // 1. Workspace Service
        ILuaTable workspaceTable = engine.createTable();
        workspaceTable.rawset("GetBlock", args -> {
            ILuaValue posVal = engine.nilValue();
            if (args.length >= 2) {
                if (args[1].isTable()) {
                    posVal = args[1];
                } else if (args.length >= 3 && args[2].isTable()) {
                    posVal = args[2];
                }
            }
            if (posVal.isTable()) {
                ILuaTable pos = posVal.asTable();
                double x = pos.rawget("X").asDouble();
                double y = pos.rawget("Y").asDouble();
                double z = pos.rawget("Z").asDouble();
                IInteractableBlock block = service.GetBlock(DEFAULT_DIMENSION, (int) x, (int) y, (int) z);
                return block != null ? wrapBlock(engine, block) : engine.nilValue();
            }
            return engine.nilValue();
        });

        workspaceTable.rawset("GetEntity", args -> {
            ILuaValue entityVal = engine.nilValue();
            if (args.length >= 2) {
                if (!args[1].isTable() || args[1].asTable().rawget("__entity") != null) {
                    entityVal = args[1];
                } else if (args.length >= 3) {
                    entityVal = args[2];
                }
            }
            Object arg = entityVal.toJavaObject();
            if (entityVal.isTable()) {
                ILuaValue inner = entityVal.asTable().rawget("__entity");
                if (inner != null && !inner.isNil()) {
                    arg = inner.toJavaObject();
                }
            }
            IInteractableEntity entity = null;
            if (arg instanceof String uuid) {
                entity = service.GetEntity(uuid);
            } else if (arg != null) {
                entity = service.GetEntity(arg);
            }
            return entity != null ? wrapEntity(engine, entity) : engine.nilValue();
        });

        workspaceTable.rawset("GetEntitiesInRadius", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) return engine.nilValue();
            IEntity center = EntitiesLuaBinding.getEntityFromTable(args[off]);
            double radius = args[off + 1].asDouble();
            if (center == null || radius <= 0) return engine.nilValue();
            java.util.List<IEntity> entities = com.luatweaker.api.pal.Platform.getInteraction().getNearbyEntities(center, radius);
            ILuaTable result = engine.createTable();
            for (int i = 0; i < entities.size(); i++) {
                result.rawset(i + 1, EntitiesLuaBinding.createEntityLuaTable(engine, entities.get(i)));
            }
            return result;
        });
        workspaceTable.rawset("getEntitiesInRadius", workspaceTable.rawget("GetEntitiesInRadius"));

        workspaceTable.rawset("FindNearest", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.nilValue();
            IEntity center = EntitiesLuaBinding.getEntityFromTable(args[off]);
            if (center == null) return engine.nilValue();
            String typeFilter = null;
            double radius = 32.0;
            if (args.length - off >= 2 && args[off + 1].isString()) {
                typeFilter = args[off + 1].asString();
                if (args.length - off >= 3 && args[off + 2].isNumber()) {
                    radius = args[off + 2].asDouble();
                }
            } else if (args.length - off >= 2 && args[off + 1].isNumber()) {
                radius = args[off + 1].asDouble();
            }
            if (radius <= 0) return engine.nilValue();
            java.util.List<IEntity> entities = com.luatweaker.api.pal.Platform.getInteraction().getNearbyEntities(center, radius);
            IEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (IEntity candidate : entities) {
                if (candidate == null || candidate.getUuid().equals(center.getUuid())) continue;
                if (typeFilter != null && !typeFilter.isBlank()
                        && !candidate.getType().equalsIgnoreCase(typeFilter)) continue;
                double dist = center.distanceTo(candidate);
                if (dist < bestDist) {
                    bestDist = dist;
                    best = candidate;
                }
            }
            return best != null ? EntitiesLuaBinding.createEntityLuaTable(engine, best) : engine.nilValue();
        });
        workspaceTable.rawset("findNearest", workspaceTable.rawget("FindNearest"));

        // ===== BlockState API (GetBlockState(x,y,z) or GetBlockState(dim,x,y,z)) =====
        workspaceTable.rawset("GetBlockState", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 3) return engine.nilValue();
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> state = com.luatweaker.api.pal.Platform.getInteraction()
                    .getBlockState(dimension, x, y, z);
            return state != null ? wrapValue(engine, state) : engine.nilValue();
        });
        workspaceTable.rawset("getBlockState", workspaceTable.rawget("GetBlockState"));

        workspaceTable.rawset("SetBlockState", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 4) return engine.nilValue();
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            String blockId = args[off + 3].asString();
            java.util.Map<String, Object> properties = null;
            if (args.length - off >= 5 && args[off + 4].isTable()) {
                properties = toJavaMap(args[off + 4].asTable());
            }
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setBlockState(dimension, x, y, z, blockId, properties));
        });
        workspaceTable.rawset("setBlockState", workspaceTable.rawget("SetBlockState"));

        // ===== BlockEntity NBT API (GetBlockEntityData / SetBlockEntityData) =====
        workspaceTable.rawset("GetBlockEntityData", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 3) return engine.nilValue();
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> data = com.luatweaker.api.pal.Platform.getInteraction()
                    .getBlockEntityData(dimension, x, y, z);
            return data != null ? wrapValue(engine, data) : engine.nilValue();
        });
        workspaceTable.rawset("getBlockEntityData", workspaceTable.rawget("GetBlockEntityData"));

        workspaceTable.rawset("SetBlockEntityData", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 4 || !args[off + 3].isTable()) return engine.wrapBoolean(false);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            java.util.Map<String, Object> data = toJavaMap(args[off + 3].asTable());
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setBlockEntityData(dimension, x, y, z, data));
        });
        workspaceTable.rawset("setBlockEntityData", workspaceTable.rawget("SetBlockEntityData"));

        // ===== Container ejection (EjectContainerItem(x,y,z,slot,[count])) =====
        workspaceTable.rawset("EjectContainerItem", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 4) return engine.wrapBoolean(false);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            int x = args[off].asInt();
            int y = args[off + 1].asInt();
            int z = args[off + 2].asInt();
            int slot = args[off + 3].asInt();
            int count = args.length - off >= 5 ? args[off + 4].asInt() : 1;
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .ejectContainerItem(dimension, x, y, z, slot, count));
        });
        workspaceTable.rawset("ejectContainerItem", workspaceTable.rawget("EjectContainerItem"));

        // ===== Bulk world editing (FillBlocks / ReplaceBlocks) =====
        // FillBlocks(x1,y1,z1,x2,y2,z2,blockId,[properties]) or with a dimension prefix.
        // Returns the number of blocks set, or -1 when the platform rejected the
        // operation (unknown block, invalid region, or over the platform cap).
        workspaceTable.rawset("FillBlocks", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 7) return engine.wrapNumber(-1);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            int x1 = args[off].asInt();
            int y1 = args[off + 1].asInt();
            int z1 = args[off + 2].asInt();
            int x2 = args[off + 3].asInt();
            int y2 = args[off + 4].asInt();
            int z2 = args[off + 5].asInt();
            String blockId = args[off + 6].asString();
            java.util.Map<String, Object> properties = null;
            if (args.length - off >= 8 && args[off + 7].isTable()) {
                properties = toJavaMap(args[off + 7].asTable());
            }
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .fillBlocks(dimension, x1, y1, z1, x2, y2, z2, blockId, properties));
        });
        workspaceTable.rawset("fillBlocks", workspaceTable.rawget("FillBlocks"));

        // ReplaceBlocks(x1,y1,z1,x2,y2,z2,fromId,toId) or with a dimension prefix.
        workspaceTable.rawset("ReplaceBlocks", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 8) return engine.wrapNumber(-1);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            int x1 = args[off].asInt();
            int y1 = args[off + 1].asInt();
            int z1 = args[off + 2].asInt();
            int x2 = args[off + 3].asInt();
            int y2 = args[off + 4].asInt();
            int z2 = args[off + 5].asInt();
            String fromId = args[off + 6].asString();
            String toId = args[off + 7].asString();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .replaceBlocks(dimension, x1, y1, z1, x2, y2, z2, fromId, toId));
        });
        workspaceTable.rawset("replaceBlocks", workspaceTable.rawget("ReplaceBlocks"));

        // ===== NBT structure placement (PlaceStructure) =====
        // PlaceStructure(templateId, x, y, z, [rotationDegrees]) or with a
        // dimension prefix. Loads the NBT template (data/<ns>/structures/*.nbt)
        // from the datapack and places it; the Lua mod decides when/where.
        workspaceTable.rawset("PlaceStructure", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            // The first argument is always a string (template id); the prefix
            // form has ANOTHER string as the second argument (the dimension).
            int dimOff = -1;
            // The first argument is always a string (template id); the prefix
            // form has ANOTHER string as the second argument (the dimension).
            // NOTE: Lua 5.2 isstring() is true for numbers too, so the strict
            // Java instanceof check is required here (same as FillBlocks).
            if (args.length - off >= 2 && args[off + 1].toJavaObject() instanceof String) {
                dimOff = off;
                off = off + 1;
            }
            if (args.length - off < 4) return engine.wrapBoolean(false);
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            String templateId = args[off].asString();
            int x = args[off + 1].asInt();
            int y = args[off + 2].asInt();
            int z = args[off + 3].asInt();
            int rotation = args.length - off >= 5 ? args[off + 4].asInt() : 0;
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .placeStructure(dimension, templateId, x, y, z, rotation));
        });
        workspaceTable.rawset("placeStructure", workspaceTable.rawget("PlaceStructure"));

        // ===== Server console command (ExecuteCommand("give ...")) =====
        workspaceTable.rawset("ExecuteCommand", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.wrapBoolean(false);
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .executeCommand(args[off].asString()));
        });
        workspaceTable.rawset("executeCommand", workspaceTable.rawget("ExecuteCommand"));

        // ===== Raycast API =====
        workspaceTable.rawset("Raycast", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = -1;
            if (args.length - off >= 1 && args[off].toJavaObject() instanceof String && (args.length - off < 2 || !args[off + 1].isTable())) {
                dimOff = off;
                off = off + 1;
            }
            double ox = 0, oy = 0, oz = 0;
            double dx = 0, dy = 0, dz = 0;
            double maxDist = 64.0;
            boolean ignoreLiquids = true;
            boolean ignorePassable = false;
            java.util.List<IEntity> ignoreEntities = null;
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;

            if (args.length - off >= 2 && args[off].isTable() && args[off + 1].isTable()) {
                double[] origin = readVector(args[off]);
                double[] direction = readVector(args[off + 1]);
                if (origin == null || direction == null) {
                    warn("World:Raycast needs Vector3 tables with numeric X/Y/Z");
                    return engine.nilValue();
                }
                ox = origin[0];
                oy = origin[1];
                oz = origin[2];
                dx = direction[0];
                dy = direction[1];
                dz = direction[2];
                if (args.length - off >= 3 && args[off + 2].isTable()) {
                    ILuaTable pt = args[off + 2].asTable();
                    ILuaValue md = pt.rawget("MaxDistance");
                    if (md != null && !md.isNil()) maxDist = md.asDouble();
                    ILuaValue il = pt.rawget("IgnoreLiquids");
                    if (il != null && !il.isNil()) ignoreLiquids = il.asBoolean();
                    ILuaValue ip = pt.rawget("IgnorePassableBlocks");
                    if (ip != null && !ip.isNil()) ignorePassable = ip.asBoolean();
                    ILuaValue ilist = pt.rawget("IgnoreList");
                    if (ilist != null && ilist.isTable()) {
                        ignoreEntities = new java.util.ArrayList<>();
                        for (int i = 1; i <= ilist.asTable().length(); i++) {
                            IEntity ent = EntitiesLuaBinding.getEntityFromTable(ilist.asTable().rawget(i));
                            if (ent != null) ignoreEntities.add(ent);
                        }
                    }
                }
            } else if (args.length - off >= 6) {
                double[] numbers = readNumbers(args, off, 6);
                if (numbers == null) {
                    warn("World:Raycast needs six numbers (origin xyz, direction xyz)");
                    return engine.nilValue();
                }
                ox = numbers[0];
                oy = numbers[1];
                oz = numbers[2];
                dx = numbers[3];
                dy = numbers[4];
                dz = numbers[5];
                if (args.length - off >= 7) maxDist = args[off + 6].asDouble();
                if (args.length - off >= 8) ignoreLiquids = args[off + 7].asBoolean();
            } else {
                return engine.nilValue();
            }

            com.luatweaker.api.spatial.RaycastParams p = new com.luatweaker.api.spatial.RaycastParams(
                    maxDist, ignoreLiquids, ignorePassable, ignoreEntities);
            com.luatweaker.api.spatial.RaycastResult res =
                    com.luatweaker.api.pal.Platform.getInteraction().raycast(dimension, ox, oy, oz, dx, dy, dz, p);
            return wrapRaycastResult(engine, service, dimension, res);
        });
        workspaceTable.rawset("raycast", workspaceTable.rawget("Raycast"));

        // ===== SpawnParticle API =====
        // SpawnParticle(id, pos[, velocity][, count][, speed])
        // SpawnParticle(id, x, y, z[, vx, vy, vz][, count][, speed])
        // A leading dimension string (dimension, id, ...) overrides the default dimension.
        workspaceTable.rawset("SpawnParticle", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = resolveDimensionPrefix(args, off);
            if (dimOff >= 0) {
                off++;
            }
            if (args.length - off < 2) {
                warn("World:SpawnParticle requires a particle id and a position");
                return engine.nilValue();
            }
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            String particleId = args[off].asString();

            int cursor = off + 1;
            double[] position;
            if (args[cursor].isTable()) {
                position = readVector(args[cursor++]);
            } else {
                position = readNumbers(args, cursor, 3);
                cursor += 3;
            }
            if (position == null) {
                warn("World:SpawnParticle('" + particleId + "') needs a Vector3 position or three numbers");
                return engine.nilValue();
            }

            double[] velocity = { 0.0, 0.0, 0.0 };
            if (cursor < args.length && args[cursor].isTable()) {
                double[] parsed = readVector(args[cursor]);
                if (parsed == null) {
                    warn("World:SpawnParticle('" + particleId + "') received an invalid velocity vector");
                    return engine.nilValue();
                }
                velocity = parsed;
                cursor++;
            } else if (args.length - cursor >= 3 && args[cursor].isNumber()) {
                double[] parsed = readNumbers(args, cursor, 3);
                if (parsed != null) {
                    velocity = parsed;
                    cursor += 3;
                }
            }

            int count = 1;
            double speed = 0.0;
            if (cursor < args.length && args[cursor].isNumber()) {
                count = args[cursor++].asInt();
            }
            if (cursor < args.length && args[cursor].isNumber()) {
                speed = args[cursor++].asDouble();
            }
            warnExtraArgs("World:SpawnParticle", cursor, args.length);

            com.luatweaker.api.pal.Platform.getInteraction().spawnParticle(
                    dimension, particleId,
                    position[0], position[1], position[2],
                    velocity[0], velocity[1], velocity[2], count, speed);
            return engine.nilValue();
        });
        workspaceTable.rawset("spawnParticle", workspaceTable.rawget("SpawnParticle"));

        // ===== PlaySound API =====
        // PlaySound(id, pos[, volume][, pitch][, source])
        // PlaySound(id, x, y, z[, volume][, pitch][, source])
        // source is a Minecraft sound category (PLAYERS, BLOCKS, HOSTILE, ...).
        workspaceTable.rawset("PlaySound", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = resolveDimensionPrefix(args, off);
            if (dimOff >= 0) {
                off++;
            }
            if (args.length - off < 2) {
                warn("World:PlaySound requires a sound id and a position");
                return engine.nilValue();
            }
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            String soundId = args[off].asString();

            int cursor = off + 1;
            double[] position;
            if (args[cursor].isTable()) {
                position = readVector(args[cursor++]);
            } else {
                position = readNumbers(args, cursor, 3);
                cursor += 3;
            }
            if (position == null) {
                warn("World:PlaySound('" + soundId + "') needs a Vector3 position or three numbers");
                return engine.nilValue();
            }

            double volume = 1.0;
            double pitch = 1.0;
            if (cursor < args.length && args[cursor].isNumber()) {
                volume = args[cursor++].asDouble();
            }
            if (cursor < args.length && args[cursor].isNumber()) {
                pitch = args[cursor++].asDouble();
            }
            String source = DEFAULT_SOUND_SOURCE;
            if (cursor < args.length && args[cursor].isString()) {
                source = args[cursor++].asString();
            }
            warnExtraArgs("World:PlaySound", cursor, args.length);

            com.luatweaker.api.pal.Platform.getInteraction().playSound(
                    dimension, soundId, position[0], position[1], position[2],
                    (float) volume, (float) pitch, source);
            return engine.nilValue();
        });
        workspaceTable.rawset("playSound", workspaceTable.rawget("PlaySound"));

        // ===== StopSound API =====
        // StopSound(id[, source]) / StopSound(dimension, id[, source]).
        // A second string that names a sound category is treated as the source, so
        // StopSound("id", "PLAYERS") is never mistaken for a dimension.
        workspaceTable.rawset("StopSound", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            int dimOff = resolveStopSoundDimensionPrefix(args, off);
            if (dimOff >= 0) {
                off++;
            }
            if (args.length - off < 1) {
                warn("World:StopSound requires a sound id");
                return engine.wrapBoolean(false);
            }
            String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
            String soundId = args[off].asString();
            String source = DEFAULT_SOUND_SOURCE;
            if (args.length - off >= 2 && args[off + 1].isString()) {
                source = args[off + 1].asString();
            }
            boolean stopped = com.luatweaker.api.pal.Platform.getInteraction()
                    .stopSound(dimension, soundId, source);
            return engine.wrapBoolean(stopped);
        });
        workspaceTable.rawset("stopSound", workspaceTable.rawget("StopSound"));

        // ===== Block capability primitives =====
        // Talking to other mods' machines through the standard NeoForge capabilities.
        // Every call takes an optional leading dimension string; coordinates are required.
        // side = up/down/north/south/west/east (omitted = unsided lookup).
        final boolean[] capabilityWarned = { false };

        // GetEnergy(x, y, z [, side]) -> { Stored, Max, CanExtract, CanReceive } | nil
        workspaceTable.rawset("GetEnergy", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:GetEnergy");
            if (pos == null) {
                return engine.nilValue();
            }
            SideParse side = readSide(args, pos.cursor(), args.length);
            if (side.error()) {
                warnSide("World:GetEnergy");
                return engine.nilValue();
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.nilValue();
            }
            java.util.Map<String, Object> data = capability.getEnergy(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), side.name());
            return data != null ? wrapValue(engine, data) : engine.nilValue();
        });
        workspaceTable.rawset("getEnergy", workspaceTable.rawget("GetEnergy"));

        // InsertEnergy(x, y, z, amount [, side]) -> accepted
        workspaceTable.rawset("InsertEnergy", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:InsertEnergy");
            if (pos == null) {
                return engine.wrapNumber(0);
            }
            Integer amount = readIntArg(args, pos.cursor(), args.length);
            if (amount == null) {
                warn("World:InsertEnergy requires an integer amount");
                return engine.wrapNumber(0);
            }
            SideParse side = readSide(args, pos.cursor() + 1, args.length);
            if (side.error()) {
                warnSide("World:InsertEnergy");
                return engine.wrapNumber(0);
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(capability.insertEnergy(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), amount, side.name()));
        });
        workspaceTable.rawset("insertEnergy", workspaceTable.rawget("InsertEnergy"));

        // ExtractEnergy(x, y, z, amount [, side]) -> extracted
        workspaceTable.rawset("ExtractEnergy", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:ExtractEnergy");
            if (pos == null) {
                return engine.wrapNumber(0);
            }
            Integer amount = readIntArg(args, pos.cursor(), args.length);
            if (amount == null) {
                warn("World:ExtractEnergy requires an integer amount");
                return engine.wrapNumber(0);
            }
            SideParse side = readSide(args, pos.cursor() + 1, args.length);
            if (side.error()) {
                warnSide("World:ExtractEnergy");
                return engine.wrapNumber(0);
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(capability.extractEnergy(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), amount, side.name()));
        });
        workspaceTable.rawset("extractEnergy", workspaceTable.rawget("ExtractEnergy"));

        // GetItemSlots(x, y, z [, side]) -> { { Slot, Id, Count }, ... } | nil
        workspaceTable.rawset("GetItemSlots", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:GetItemSlots");
            if (pos == null) {
                return engine.nilValue();
            }
            SideParse side = readSide(args, pos.cursor(), args.length);
            if (side.error()) {
                warnSide("World:GetItemSlots");
                return engine.nilValue();
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.nilValue();
            }
            java.util.List<java.util.Map<String, Object>> slots = capability.getItemSlots(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), side.name());
            return slots != null ? wrapValue(engine, slots) : engine.nilValue();
        });
        workspaceTable.rawset("getItemSlots", workspaceTable.rawget("GetItemSlots"));

        // InsertItem(x, y, z, itemId, count [, side]) -> inserted
        workspaceTable.rawset("InsertItem", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:InsertItem");
            if (pos == null) {
                return engine.wrapNumber(0);
            }
            String itemId = readStringArg(args, pos.cursor(), args.length);
            Integer count = readIntArg(args, pos.cursor() + 1, args.length);
            if (itemId == null || count == null) {
                warn("World:InsertItem requires (itemId, count)");
                return engine.wrapNumber(0);
            }
            SideParse side = readSide(args, pos.cursor() + 2, args.length);
            if (side.error()) {
                warnSide("World:InsertItem");
                return engine.wrapNumber(0);
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(capability.insertItem(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), itemId, count, side.name()));
        });
        workspaceTable.rawset("insertItem", workspaceTable.rawget("InsertItem"));

        // ExtractItem(x, y, z, slot, count [, side]) -> { Slot, Id, Count } | nil
        workspaceTable.rawset("ExtractItem", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:ExtractItem");
            if (pos == null) {
                return engine.nilValue();
            }
            Integer slot = readIntArg(args, pos.cursor(), args.length);
            Integer count = readIntArg(args, pos.cursor() + 1, args.length);
            if (slot == null || count == null) {
                warn("World:ExtractItem requires (slot, count)");
                return engine.nilValue();
            }
            SideParse side = readSide(args, pos.cursor() + 2, args.length);
            if (side.error()) {
                warnSide("World:ExtractItem");
                return engine.nilValue();
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.nilValue();
            }
            java.util.Map<String, Object> data = capability.extractItem(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), slot, count, side.name());
            return data != null ? wrapValue(engine, data) : engine.nilValue();
        });
        workspaceTable.rawset("extractItem", workspaceTable.rawget("ExtractItem"));

        // GetFluid(x, y, z [, side]) -> { Id, Amount, Capacity } | nil
        workspaceTable.rawset("GetFluid", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:GetFluid");
            if (pos == null) {
                return engine.nilValue();
            }
            SideParse side = readSide(args, pos.cursor(), args.length);
            if (side.error()) {
                warnSide("World:GetFluid");
                return engine.nilValue();
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.nilValue();
            }
            java.util.Map<String, Object> data = capability.getFluid(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), side.name());
            return data != null ? wrapValue(engine, data) : engine.nilValue();
        });
        workspaceTable.rawset("getFluid", workspaceTable.rawget("GetFluid"));

        // FillFluid(x, y, z, fluidId, amount [, side]) -> accepted
        workspaceTable.rawset("FillFluid", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:FillFluid");
            if (pos == null) {
                return engine.wrapNumber(0);
            }
            String fluidId = readStringArg(args, pos.cursor(), args.length);
            Integer amount = readIntArg(args, pos.cursor() + 1, args.length);
            if (fluidId == null || amount == null) {
                warn("World:FillFluid requires (fluidId, amount)");
                return engine.wrapNumber(0);
            }
            SideParse side = readSide(args, pos.cursor() + 2, args.length);
            if (side.error()) {
                warnSide("World:FillFluid");
                return engine.wrapNumber(0);
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(capability.fillFluid(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), fluidId, amount, side.name()));
        });
        workspaceTable.rawset("fillFluid", workspaceTable.rawget("FillFluid"));

        // DrainFluid(x, y, z, fluidId, amount [, side]) -> drained; fluidId "*" = first non-empty tank
        workspaceTable.rawset("DrainFluid", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:DrainFluid");
            if (pos == null) {
                return engine.wrapNumber(0);
            }
            String fluidId = readStringArg(args, pos.cursor(), args.length);
            Integer amount = readIntArg(args, pos.cursor() + 1, args.length);
            if (fluidId == null || amount == null) {
                warn("World:DrainFluid requires (fluidId, amount); use \"*\" for any fluid");
                return engine.wrapNumber(0);
            }
            SideParse side = readSide(args, pos.cursor() + 2, args.length);
            if (side.error()) {
                warnSide("World:DrainFluid");
                return engine.wrapNumber(0);
            }
            com.luatweaker.api.pal.IPlatformCapability capability = capability(capabilityWarned);
            if (capability == null) {
                return engine.wrapNumber(0);
            }
            return engine.wrapNumber(capability.drainFluid(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), fluidId, amount, side.name()));
        });
        workspaceTable.rawset("drainFluid", workspaceTable.rawget("DrainFluid"));

        // GetRedstoneSignal(x, y, z [, direction]) -> 0..15
        workspaceTable.rawset("GetRedstoneSignal", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:GetRedstoneSignal");
            if (pos == null) return engine.wrapNumber(0);
            String dir = (pos.cursor() < args.length && !args[pos.cursor()].isNil())
                    ? args[pos.cursor()].asString() : null;
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction().getRedstoneSignal(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), dir));
        });
        workspaceTable.rawset("getRedstoneSignal", workspaceTable.rawget("GetRedstoneSignal"));

        // CheckPattern(x, y, z, patternTable) -> boolean
        workspaceTable.rawset("CheckPattern", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:CheckPattern");
            if (pos == null || pos.cursor() >= args.length || !args[pos.cursor()].isTable()) {
                return engine.wrapBoolean(false);
            }
            ILuaTable pTable = args[pos.cursor()].asTable();
            java.util.List<java.util.Map<String, Object>> pattern = new java.util.ArrayList<>();
            for (int i = 1; ; i++) {
                ILuaValue item = pTable.rawget(i);
                if (item == null || item.isNil() || !item.isTable()) break;
                ILuaTable entry = item.asTable();
                java.util.Map<String, Object> map = new java.util.HashMap<>();
                ILuaValue dx = entry.rawget("dx"); if (dx.isNil()) dx = entry.rawget("DX");
                if (dx.isNil()) dx = entry.rawget("x"); if (dx.isNil()) dx = entry.rawget("X");
                ILuaValue dy = entry.rawget("dy"); if (dy.isNil()) dy = entry.rawget("DY");
                if (dy.isNil()) dy = entry.rawget("y"); if (dy.isNil()) dy = entry.rawget("Y");
                ILuaValue dz = entry.rawget("dz"); if (dz.isNil()) dz = entry.rawget("DZ");
                if (dz.isNil()) dz = entry.rawget("z"); if (dz.isNil()) dz = entry.rawget("Z");
                map.put("dx", dx.isNil() ? 0 : dx.asInt());
                map.put("dy", dy.isNil() ? 0 : dy.asInt());
                map.put("dz", dz.isNil() ? 0 : dz.asInt());
                ILuaValue block = entry.rawget("block"); if (block.isNil()) block = entry.rawget("Block");
                if (!block.isNil()) map.put("block", block.asString());
                ILuaValue tag = entry.rawget("tag"); if (tag.isNil()) tag = entry.rawget("Tag");
                if (!tag.isNil()) map.put("tag", tag.asString());
                pattern.add(map);
            }
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction().checkPattern(
                    pos.dimension(), pos.x(), pos.y(), pos.z(), pattern));
        });
        workspaceTable.rawset("checkPattern", workspaceTable.rawget("CheckPattern"));

        // TransferEnergy(fromPos, toPos, maxAmount [, simulate]) -> transferred
        workspaceTable.rawset("TransferEnergy", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 3) return engine.wrapNumber(0);
            int fx, fy, fz, tx, ty, tz, amount;
            boolean sim = false;
            int cursor = off;
            if (args[cursor].isTable()) {
                ILuaTable f = args[cursor++].asTable();
                fx = f.rawget("X").asInt(); fy = f.rawget("Y").asInt(); fz = f.rawget("Z").asInt();
                if (cursor < args.length && args[cursor].isTable()) {
                    ILuaTable t = args[cursor++].asTable();
                    tx = t.rawget("X").asInt(); ty = t.rawget("Y").asInt(); tz = t.rawget("Z").asInt();
                } else return engine.wrapNumber(0);
            } else if (args.length - off >= 7) {
                fx = args[cursor++].asInt(); fy = args[cursor++].asInt(); fz = args[cursor++].asInt();
                tx = args[cursor++].asInt(); ty = args[cursor++].asInt(); tz = args[cursor++].asInt();
            } else return engine.wrapNumber(0);
            amount = cursor < args.length ? args[cursor++].asInt() : 0;
            if (cursor < args.length) sim = args[cursor].asBoolean();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction().transferEnergy(
                    DEFAULT_DIMENSION, fx, fy, fz, tx, ty, tz, amount, sim));
        });
        workspaceTable.rawset("transferEnergy", workspaceTable.rawget("TransferEnergy"));

        // TransferFluid(fromPos, toPos, maxAmount [, simulate]) -> transferred
        workspaceTable.rawset("TransferFluid", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 3) return engine.wrapNumber(0);
            int fx, fy, fz, tx, ty, tz, amount;
            boolean sim = false;
            int cursor = off;
            if (args[cursor].isTable()) {
                ILuaTable f = args[cursor++].asTable();
                fx = f.rawget("X").asInt(); fy = f.rawget("Y").asInt(); fz = f.rawget("Z").asInt();
                if (cursor < args.length && args[cursor].isTable()) {
                    ILuaTable t = args[cursor++].asTable();
                    tx = t.rawget("X").asInt(); ty = t.rawget("Y").asInt(); tz = t.rawget("Z").asInt();
                } else return engine.wrapNumber(0);
            } else if (args.length - off >= 7) {
                fx = args[cursor++].asInt(); fy = args[cursor++].asInt(); fz = args[cursor++].asInt();
                tx = args[cursor++].asInt(); ty = args[cursor++].asInt(); tz = args[cursor++].asInt();
            } else return engine.wrapNumber(0);
            amount = cursor < args.length ? args[cursor++].asInt() : 0;
            if (cursor < args.length) sim = args[cursor].asBoolean();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction().transferFluid(
                    DEFAULT_DIMENSION, fx, fy, fz, tx, ty, tz, amount, sim));
        });
        workspaceTable.rawset("transferFluid", workspaceTable.rawget("TransferFluid"));

        // TransferItem(fromPos, fromSlot, toPos, toSlot, maxCount [, simulate]) -> transferred
        workspaceTable.rawset("TransferItem", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 5) return engine.wrapNumber(0);
            int fx, fy, fz, fromSlot, tx, ty, tz, toSlot, maxCount;
            boolean sim = false;
            int cursor = off;
            if (args[cursor].isTable()) {
                ILuaTable f = args[cursor++].asTable();
                fx = f.rawget("X").asInt(); fy = f.rawget("Y").asInt(); fz = f.rawget("Z").asInt();
                fromSlot = args[cursor++].asInt();
                if (cursor < args.length && args[cursor].isTable()) {
                    ILuaTable t = args[cursor++].asTable();
                    tx = t.rawget("X").asInt(); ty = t.rawget("Y").asInt(); tz = t.rawget("Z").asInt();
                } else return engine.wrapNumber(0);
                toSlot = cursor < args.length ? args[cursor++].asInt() : -1;
            } else if (args.length - off >= 9) {
                fx = args[cursor++].asInt(); fy = args[cursor++].asInt(); fz = args[cursor++].asInt();
                fromSlot = args[cursor++].asInt();
                tx = args[cursor++].asInt(); ty = args[cursor++].asInt(); tz = args[cursor++].asInt();
                toSlot = args[cursor++].asInt();
            } else return engine.wrapNumber(0);
            maxCount = cursor < args.length ? args[cursor++].asInt() : 64;
            if (cursor < args.length) sim = args[cursor].asBoolean();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction().transferItem(
                    DEFAULT_DIMENSION, fx, fy, fz, fromSlot, tx, ty, tz, toSlot, maxCount, sim));
        });
        workspaceTable.rawset("transferItem", workspaceTable.rawget("TransferItem"));

        // ValidateCuboid([dim, ] posA, posB, specTable) or (x1, y1, z1, x2, y2, z2, specTable)
        workspaceTable.rawset("ValidateCuboid", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            String dim = DEFAULT_DIMENSION;
            int cursor = off;
            if (cursor < args.length && args[cursor].toJavaObject() instanceof String s && !args[cursor].isTable()) {
                dim = s;
                cursor++;
            }
            int x1, y1, z1, x2, y2, z2;
            java.util.Map<String, Object> spec = java.util.Map.of();
            if (cursor < args.length && args[cursor].isTable()) {
                ILuaTable a = args[cursor++].asTable();
                x1 = a.rawget("X").asInt(); y1 = a.rawget("Y").asInt(); z1 = a.rawget("Z").asInt();
                if (cursor < args.length && args[cursor].isTable()) {
                    ILuaTable b = args[cursor++].asTable();
                    x2 = b.rawget("X").asInt(); y2 = b.rawget("Y").asInt(); z2 = b.rawget("Z").asInt();
                } else return engine.createTable();
            } else if (cursor + 5 < args.length) {
                x1 = args[cursor++].asInt(); y1 = args[cursor++].asInt(); z1 = args[cursor++].asInt();
                x2 = args[cursor++].asInt(); y2 = args[cursor++].asInt(); z2 = args[cursor++].asInt();
            } else {
                return engine.createTable();
            }
            if (cursor < args.length && args[cursor].isTable()) {
                spec = toJavaMap(args[cursor].asTable());
            }
            java.util.Map<String, Object> result = com.luatweaker.api.pal.Platform.getInteraction()
                    .validateCuboid(dim, x1, y1, z1, x2, y2, z2, spec);
            return wrapValue(engine, result);
        });
        workspaceTable.rawset("validateCuboid", workspaceTable.rawget("ValidateCuboid"));

        // SetBlockStatesInBox([dim, ] posA, posB, [filterBlockId, ] propertiesTable)
        workspaceTable.rawset("SetBlockStatesInBox", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            String dim = DEFAULT_DIMENSION;
            int cursor = off;
            if (cursor < args.length && args[cursor].toJavaObject() instanceof String s && !args[cursor].isTable()) {
                dim = s;
                cursor++;
            }
            int x1, y1, z1, x2, y2, z2;
            String filter = null;
            java.util.Map<String, Object> props = java.util.Map.of();
            if (cursor < args.length && args[cursor].isTable()) {
                ILuaTable a = args[cursor++].asTable();
                x1 = a.rawget("X").asInt(); y1 = a.rawget("Y").asInt(); z1 = a.rawget("Z").asInt();
                if (cursor < args.length && args[cursor].isTable()) {
                    ILuaTable b = args[cursor++].asTable();
                    x2 = b.rawget("X").asInt(); y2 = b.rawget("Y").asInt(); z2 = b.rawget("Z").asInt();
                } else return engine.wrapNumber(0);
            } else if (cursor + 5 < args.length) {
                x1 = args[cursor++].asInt(); y1 = args[cursor++].asInt(); z1 = args[cursor++].asInt();
                x2 = args[cursor++].asInt(); y2 = args[cursor++].asInt(); z2 = args[cursor++].asInt();
            } else {
                return engine.wrapNumber(0);
            }
            if (cursor < args.length && args[cursor].toJavaObject() instanceof String f && !args[cursor].isTable()) {
                filter = f;
                cursor++;
            }
            if (cursor < args.length && args[cursor].isTable()) {
                props = toJavaMap(args[cursor].asTable());
            }
            int updated = com.luatweaker.api.pal.Platform.getInteraction()
                    .setBlockStatesInBox(dim, x1, y1, z1, x2, y2, z2, filter, props);
            return engine.wrapNumber(updated);
        });
        workspaceTable.rawset("setBlockStatesInBox", workspaceTable.rawget("SetBlockStatesInBox"));

        // FindBlocks([dim, ] x, y, z, radius, filter [, limit]) or (pos, radius, filter [, limit])
        workspaceTable.rawset("FindBlocks", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:FindBlocks");
            if (pos == null || pos.cursor() >= args.length) return engine.createTable();
            int cursor = pos.cursor();
            int radius = args[cursor++].asInt();
            if (cursor >= args.length || !args[cursor].isString()) return engine.createTable();
            String filter = args[cursor++].asString();
            int limit = cursor < args.length ? args[cursor].asInt() : 64;
            java.util.List<java.util.Map<String, Object>> list = com.luatweaker.api.pal.Platform.getInteraction()
                    .findBlocks(pos.dimension(), pos.x(), pos.y(), pos.z(), radius, filter, limit);
            return wrapValue(engine, list);
        });
        workspaceTable.rawset("findBlocks", workspaceTable.rawget("FindBlocks"));

        // SetEnergy([dim, ] x, y, z, amount) or (pos, amount) -> boolean
        workspaceTable.rawset("SetEnergy", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:SetEnergy");
            if (pos == null || pos.cursor() >= args.length) return engine.wrapBoolean(false);
            int amount = args[pos.cursor()].asInt();
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setEnergy(pos.dimension(), pos.x(), pos.y(), pos.z(), amount));
        });
        workspaceTable.rawset("setEnergy", workspaceTable.rawget("SetEnergy"));

        // ProduceEnergy([dim, ] x, y, z, amount) or (pos, amount) -> actualProduced
        workspaceTable.rawset("ProduceEnergy", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:ProduceEnergy");
            if (pos == null || pos.cursor() >= args.length) return engine.wrapNumber(0);
            int amount = args[pos.cursor()].asInt();
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .produceEnergy(pos.dimension(), pos.x(), pos.y(), pos.z(), amount));
        });
        workspaceTable.rawset("produceEnergy", workspaceTable.rawget("ProduceEnergy"));

        // SetProgress([dim, ] x, y, z, progress) or (pos, progress) -> boolean
        workspaceTable.rawset("SetProgress", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:SetProgress");
            if (pos == null || pos.cursor() >= args.length) return engine.wrapBoolean(false);
            float progress = (float) args[pos.cursor()].asDouble();
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction()
                    .setProgress(pos.dimension(), pos.x(), pos.y(), pos.z(), progress));
        });
        workspaceTable.rawset("setProgress", workspaceTable.rawget("SetProgress"));

        // GetProgress([dim, ] x, y, z) or (pos) -> progress
        workspaceTable.rawset("GetProgress", args -> {
            CoordinateArgs pos = readCoordinates(args, "World:GetProgress");
            if (pos == null) return engine.wrapNumber(0.0);
            return engine.wrapNumber(com.luatweaker.api.pal.Platform.getInteraction()
                    .getProgress(pos.dimension(), pos.x(), pos.y(), pos.z()));
        });
        workspaceTable.rawset("getProgress", workspaceTable.rawget("GetProgress"));

        // GetItem(posOrEntity, slot) or (x, y, z, slot) -> IInteractableItem | nil
        workspaceTable.rawset("GetItem", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) return engine.nilValue();
            ILuaValue target = args[off];
            int slot = args[args.length - 1].asInt();
            IInteractableItem item = null;
            if (target.isTable() && (target.asTable().rawget("X") != null || target.asTable().rawget("x") != null)) {
                ILuaTable t = target.asTable();
                ILuaValue xVal = t.rawget("X"); if (xVal == null || xVal.isNil()) xVal = t.rawget("x");
                ILuaValue yVal = t.rawget("Y"); if (yVal == null || yVal.isNil()) yVal = t.rawget("y");
                ILuaValue zVal = t.rawget("Z"); if (zVal == null || zVal.isNil()) zVal = t.rawget("z");
                ILuaValue dimVal = t.rawget("dimension");
                if (dimVal == null || dimVal.isNil()) dimVal = t.rawget("Dimension");
                String dimension = (dimVal != null && !dimVal.isNil()) ? dimVal.asString() : DEFAULT_DIMENSION;
                var block = service.GetBlock(dimension, xVal.asInt(), yVal.asInt(), zVal.asInt());
                if (block != null) {
                    item = service.GetItem(block, slot);
                }
            } else if (args.length - off >= 4 && args[off].isNumber()) {
                int x = args[off].asInt();
                int y = args[off + 1].asInt();
                int z = args[off + 2].asInt();
                var block = service.GetBlock(DEFAULT_DIMENSION, x, y, z);
                if (block != null) {
                    item = service.GetItem(block, slot);
                }
            } else {
                Object raw = target.toJavaObject();
                if (target.isTable()) {
                    ILuaValue inner = target.asTable().rawget("__entity");
                    if (inner != null && !inner.isNil()) raw = inner.toJavaObject();
                }
                if (raw != null) {
                    item = service.GetItem(raw, slot);
                }
            }
            return item != null ? wrapItem(engine, item) : engine.nilValue();
        });
        workspaceTable.rawset("getItem", workspaceTable.rawget("GetItem"));

        // ===== RaycastParams Class =====
        ILuaTable raycastParamsClass = engine.createTable();
        raycastParamsClass.rawset("new", args -> {
            ILuaTable pt = engine.createTable();
            pt.rawset("MaxDistance", engine.wrapNumber(64.0));
            pt.rawset("IgnoreLiquids", engine.wrapBoolean(true));
            pt.rawset("IgnorePassableBlocks", engine.wrapBoolean(false));
            pt.rawset("IgnoreList", engine.createTable());
            return pt;
        });
        engine.registerGlobal("RaycastParams", raycastParamsClass);

        // 2. EntityService Service
        ILuaTable entityServiceTable = engine.createTable();
        ILuaValue sigClass = engine.getGlobalEnvironment().rawget("Signal");
        ILuaValue entitySpawnedSignal = engine.nilValue();
        if (sigClass != null && !sigClass.isNil()) {
            ILuaValue sigNew = sigClass.asTable().rawget("new");
            entitySpawnedSignal = engine.callFunction(sigNew, sigClass);
            entityServiceTable.rawset("EntitySpawned", entitySpawnedSignal);
        }
        final ILuaValue finalSpawnedSignal = entitySpawnedSignal;
        entityServiceTable.rawset("spawnEntity", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 4) return engine.nilValue();
            String type = args[off].asString();
            double x = args[off + 1].asDouble();
            double y = args[off + 2].asDouble();
            double z = args[off + 3].asDouble();
            Object spawned = com.luatweaker.api.pal.Platform.getEntity().spawnEntity(type, x, y, z);
            if (spawned == null) return engine.nilValue();
            ILuaValue wrapped = getWrappedEntity(engine, spawned);
            if (finalSpawnedSignal != null && !finalSpawnedSignal.isNil()) {
                ILuaValue fireFn = finalSpawnedSignal.asTable().rawget("Fire");
                if (fireFn != null && !fireFn.isNil()) {
                    engine.callFunction(fireFn, finalSpawnedSignal, wrapped);
                }
            }
            return wrapped;
        });
        entityServiceTable.rawset("GetEntity", workspaceTable.rawget("GetEntity"));
        entityServiceTable.rawset("getEntity", workspaceTable.rawget("GetEntity"));

        workspaceTable.rawset("PlayAnimation", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 2) return engine.wrapBoolean(false);
            ILuaValue targetVal = args[off];
            String animName = args[off + 1].asString();
            String ctrl = args.length - off >= 3 ? args[off + 2].asString() : "controller";
            Object target = null;
            if (targetVal.isTable()) {
                ILuaTable tbl = targetVal.asTable();
                ILuaValue ent = tbl.rawget("__entity");
                if (ent != null && !ent.isNil()) {
                    target = ent.toJavaObject();
                } else {
                    ILuaValue blk = tbl.rawget("__block");
                    if (blk != null && !blk.isNil()) {
                        target = blk.toJavaObject();
                    } else {
                        ILuaValue xVal = tbl.rawget("X");
                        if (xVal == null || xVal.isNil()) xVal = tbl.rawget("x");
                        ILuaValue yVal = tbl.rawget("Y");
                        if (yVal == null || yVal.isNil()) yVal = tbl.rawget("y");
                        ILuaValue zVal = tbl.rawget("Z");
                        if (zVal == null || zVal.isNil()) zVal = tbl.rawget("z");
                        if (xVal != null && !xVal.isNil() && yVal != null && !yVal.isNil() && zVal != null && !zVal.isNil()) {
                            int x = (int) Math.floor(xVal.asDouble());
                            int y = (int) Math.floor(yVal.asDouble());
                            int z = (int) Math.floor(zVal.asDouble());
                            ILuaValue dimVal = tbl.rawget("Dimension");
                            if (dimVal == null || dimVal.isNil()) dimVal = tbl.rawget("dimension");
                            String dim = (dimVal != null && !dimVal.isNil()) ? dimVal.asString() : "minecraft:overworld";
                            target = com.luatweaker.api.pal.Platform.getInteraction().getInteractableBlock(dim, x, y, z);
                        }
                    }
                }
            } else {
                target = targetVal.toJavaObject();
            }
            if (target == null) return engine.wrapBoolean(false);
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction().triggerGeoAnimation(target, ctrl, animName, 1.0, false));
        });
        workspaceTable.rawset("playAnimation", workspaceTable.rawget("PlayAnimation"));
        workspaceTable.rawset("TriggerAnimation", workspaceTable.rawget("PlayAnimation"));
        workspaceTable.rawset("triggerAnimation", workspaceTable.rawget("PlayAnimation"));

        // Register services & globals
        engine.registerService("Workspace", workspaceTable);
        engine.registerService("World", workspaceTable);
        engine.registerService("EntityService", entityServiceTable);
        engine.registerService("Entities", entityServiceTable);

        engine.registerGlobal("Workspace", workspaceTable);
        engine.registerGlobal("World", workspaceTable);
        engine.registerGlobal("EntityService", entityServiceTable);
        engine.registerGlobal("Entities", entityServiceTable);
    }

    public static ILuaValue getWrappedEntity(@NotNull ILuaEngine engine, @NotNull Object rawEntity) {
        IInteractionService service = new InteractionServiceImpl();
        IInteractableEntity entity = service.GetEntity(rawEntity);
        return entity != null ? wrapEntity(engine, entity) : engine.nilValue();
    }

    @NotNull
    public static ILuaTable wrapBlock(@NotNull ILuaEngine engine, @NotNull IInteractableBlock block) {
        ILuaTable table = engine.createTable();
        table.rawset("__block", engine.wrapUserdata(block));

        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        ILuaValue attrSignal = engine.nilValue();
        if (signalClass != null && !signalClass.isNil()) {
            ILuaValue signalNew = signalClass.asTable().rawget("new");
            attrSignal = engine.callFunction(signalNew, signalClass);
            table.rawset("AttributeChanged", attrSignal);
        }
        final ILuaValue finalAttrSignal = attrSignal;

        ILuaTable meta = engine.createTable();
        ILuaTable methods = engine.createTable();

        methods.rawset("Destroy", args -> engine.wrapBoolean(block.Destroy()));
        methods.rawset("GetAttribute", args -> {
            if (args.length >= 2) {
                String val = block.getAttribute(args[1].asString());
                return val != null ? engine.wrapString(val) : engine.nilValue();
            }
            return engine.nilValue();
        });
        methods.rawset("SetAttribute", args -> {
            if (args.length >= 3) {
                String name = args[1].asString();
                String val = args[2].asString();
                block.setAttribute(name, val);
                if (finalAttrSignal != null && !finalAttrSignal.isNil()) {
                    ILuaValue fireFn = finalAttrSignal.asTable().rawget("Fire");
                    if (fireFn != null && !fireFn.isNil()) {
                        engine.callFunction(fireFn, finalAttrSignal, engine.wrapString(name), engine.wrapString(val));
                    }
                }
            }
            return engine.nilValue();
        });
        methods.rawset("UseBlock", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("Block:UseBlock requires (actor)");
            }
            Object actor = args[1].toJavaObject();
            if (args[1].isTable()) {
                ILuaValue inner = args[1].asTable().rawget("__entity");
                if (inner != null && !inner.isNil()) {
                    actor = inner.toJavaObject();
                }
            }
            return engine.wrapBoolean(actor != null && block.useBlock(actor));
        });
        methods.rawset("PlayAnimation", args -> {
            int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
            if (args.length - off < 1) return engine.wrapBoolean(false);
            String animName = args[off].asString();
            String ctrl = args.length - off >= 2 ? args[off + 1].asString() : "controller";
            return engine.wrapBoolean(com.luatweaker.api.pal.Platform.getInteraction().triggerGeoAnimation(block, ctrl, animName, 1.0, false));
        });
        methods.rawset("playAnimation", methods.rawget("PlayAnimation"));
        methods.rawset("TriggerAnimation", methods.rawget("PlayAnimation"));
        methods.rawset("triggerAnimation", methods.rawget("PlayAnimation"));

        meta.rawset("__index", args -> {
            String key = args[1].asString();
            switch (key) {
                case "Id":
                case "id":
                    return engine.wrapString(block.getId());
                case "Nbt":
                case "nbt":
                    return engine.wrapString(block.getNbt());
                case "Position":
                case "position":
                    ILuaValue v3Class = engine.getGlobalEnvironment().rawget("Vector3");
                    if (v3Class != null && !v3Class.isNil()) {
                        ILuaValue v3New = v3Class.asTable().rawget("new");
                        return engine.callFunction(v3New, v3Class,
                            engine.wrapNumber(block.getX()),
                            engine.wrapNumber(block.getY()),
                            engine.wrapNumber(block.getZ())
                        );
                    }
                    return engine.nilValue();
                case "Dimension":
                case "dimension":
                    return engine.wrapString(block.getDimension());
                case "Hardness":
                case "hardness":
                    return engine.wrapNumber(block.getHardness());
                case "LightLevel":
                case "lightLevel":
                    return engine.wrapNumber(block.getLightLevel());
                case "IsAir":
                case "isAir":
                    return engine.wrapBoolean(block.isAir());
                case "IsSolid":
                case "isSolid":
                    return engine.wrapBoolean(block.isSolid());
                case "IsLiquid":
                case "isLiquid":
                    return engine.wrapBoolean(block.isLiquid());
                default:
                    ILuaValue method = methods.rawget(key);
                    if (method != null && !method.isNil()) {
                        return method;
                    }
                    Object rawBlock = block.getRawBlockState();
                    if (rawBlock != null) {
                        ILuaValue fallback = getDynamicFallback(engine, rawBlock, key);
                        if (fallback != null) {
                            return fallback;
                        }
                    }
                    return engine.nilValue();
            }
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            ILuaValue val = args[2];
            if ("Id".equals(key) || "id".equals(key)) {
                block.setId(val.asString());
            } else if ("Nbt".equals(key) || "nbt".equals(key)) {
                block.setNbt(val.asString());
            } else {
                Object rawBlock = block.getRawBlockState();
                if (rawBlock != null) {
                    setDynamicFallback(engine, rawBlock, key, val);
                }
            }
            return engine.nilValue();
        });

        table.setMetatable(meta);
        return table;
    }

    @NotNull
    public static ILuaTable wrapItem(@NotNull ILuaEngine engine, @NotNull IInteractableItem item) {
        ILuaTable table = engine.createTable();
        table.rawset("__item", engine.wrapUserdata(item));

        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        ILuaValue attrSignal = engine.nilValue();
        if (signalClass != null && !signalClass.isNil()) {
            ILuaValue signalNew = signalClass.asTable().rawget("new");
            attrSignal = engine.callFunction(signalNew, signalClass);
            table.rawset("AttributeChanged", attrSignal);
        }
        final ILuaValue finalAttrSignal = attrSignal;

        ILuaTable meta = engine.createTable();
        ILuaTable methods = engine.createTable();

        methods.rawset("UseItem", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("Item:UseItem requires (actor)");
            }
            Object actor = args[1].toJavaObject();
            if (args[1].isTable()) {
                ILuaValue inner = args[1].asTable().rawget("__entity");
                if (inner != null && !inner.isNil()) {
                    actor = inner.toJavaObject();
                }
            }
            return engine.wrapBoolean(actor != null && item.useItem(actor));
        });

        methods.rawset("Drop", args -> {
            if (args.length < 2) {
                throw new IllegalArgumentException("Item:Drop requires (actor, [count])");
            }
            Object actor = args[1].toJavaObject();
            if (args[1].isTable()) {
                ILuaValue inner = args[1].asTable().rawget("__entity");
                if (inner != null && !inner.isNil()) {
                    actor = inner.toJavaObject();
                }
            }
            int count = args.length >= 3 ? args[2].asInt() : item.getCount();
            return engine.wrapBoolean(actor != null && item.drop(actor, count));
        });

        methods.rawset("GetAttribute", args -> {
            if (args.length >= 2) {
                String val = item.getAttribute(args[1].asString());
                return val != null ? engine.wrapString(val) : engine.nilValue();
            }
            return engine.nilValue();
        });
        methods.rawset("SetAttribute", args -> {
            if (args.length >= 3) {
                String name = args[1].asString();
                String val = args[2].asString();
                item.setAttribute(name, val);
                if (finalAttrSignal != null && !finalAttrSignal.isNil()) {
                    ILuaValue fireFn = finalAttrSignal.asTable().rawget("Fire");
                    if (fireFn != null && !fireFn.isNil()) {
                        engine.callFunction(fireFn, finalAttrSignal, engine.wrapString(name), engine.wrapString(val));
                    }
                }
            }
            return engine.nilValue();
        });

        methods.rawset("GetCustomData", args -> {
            if (args.length >= 2) {
                Object val = item.getCustomData(args[1].asString());
                return wrapValue(engine, val);
            }
            return engine.nilValue();
        });
        methods.rawset("getCustomData", methods.rawget("GetCustomData"));

        methods.rawset("SetCustomData", args -> {
            if (args.length >= 3) {
                String name = args[1].asString();
                Object val = args[2].toJavaObject();
                item.setCustomData(name, val);
            }
            return engine.nilValue();
        });
        methods.rawset("setCustomData", methods.rawget("SetCustomData"));

        meta.rawset("__index", args -> {
            String key = args[1].asString();
            switch (key) {
                case "Id":
                case "id":
                    return engine.wrapString(item.getId());
                case "Nbt":
                case "nbt":
                    return engine.wrapString(item.getNbt());
                case "Count":
                case "count":
                    return engine.wrapNumber(item.getCount());
                case "Slot":
                case "slot":
                    return engine.wrapNumber(item.getSlot());
                case "OwnerUuid":
                case "ownerUuid":
                    return item.getOwnerUuid() != null ? engine.wrapString(item.getOwnerUuid()) : engine.nilValue();
                case "Damage":
                case "damage":
                    return engine.wrapNumber(item.getDamage());
                case "MaxDamage":
                case "maxDamage":
                    return engine.wrapNumber(item.getMaxDamage());
                case "CustomName":
                case "customName":
                    return item.getCustomName() != null ? engine.wrapString(item.getCustomName()) : engine.nilValue();
                case "IsDamageable":
                case "isDamageable":
                    return engine.wrapBoolean(item.isDamageable());
                case "IsEnchanted":
                case "isEnchanted":
                    return engine.wrapBoolean(item.isEnchanted());
                default:
                    ILuaValue method = methods.rawget(key);
                    if (method != null && !method.isNil()) {
                        return method;
                    }
                    Object rawItem = item.getRawItemStack();
                    if (rawItem != null) {
                        ILuaValue fallback = getDynamicFallback(engine, rawItem, key);
                        if (fallback != null) {
                            return fallback;
                        }
                    }
                    return engine.nilValue();
            }
        });

        meta.rawset("__newindex", args -> {
            String key = args[1].asString();
            ILuaValue val = args[2];
            if ("Count".equals(key) || "count".equals(key)) {
                item.setCount(val.asInt());
            } else if ("Slot".equals(key) || "slot".equals(key)) {
                item.setSlot(val.asInt());
            } else if ("Damage".equals(key) || "damage".equals(key)) {
                item.setDamage(val.asInt());
            } else if ("CustomName".equals(key) || "customName".equals(key)) {
                item.setCustomName(val.asString());
            } else if ("Nbt".equals(key) || "nbt".equals(key)) {
                item.setNbt(val.asString());
            } else {
                Object rawItem = item.getRawItemStack();
                if (rawItem != null) {
                    setDynamicFallback(engine, rawItem, key, val);
                }
            }
            return engine.nilValue();
        });

        table.setMetatable(meta);
        return table;
    }

    /**
     * Wraps an entity as the unified Lua entity table (method-style API + Roblox
     * property aliases). IInteractableEntity implementations also implement IEntity,
     * so both interaction events and entity bindings operate on the same shape.
     */
    @NotNull
    public static ILuaTable wrapEntity(@NotNull ILuaEngine engine, @NotNull IInteractableEntity entity) {
        if (entity instanceof IEntity ie) {
            return com.luatweaker.entities.EntitiesLuaBinding.createEntityLuaTable(engine, ie);
        }
        ILuaTable table = engine.createTable();
        table.rawset("__entity", engine.wrapUserdata(entity));
        return table;
    }

    /** Recursively converts a Java Map/List/primitive (NbtCodec shape) to a Lua table/value. */
    @NotNull
    public static ILuaValue wrapValue(@NotNull ILuaEngine engine, @NotNull Object value) {
        if (value instanceof String s) return engine.wrapString(s);
        if (value instanceof Boolean b) return engine.wrapBoolean(b);
        if (value instanceof Number n) return engine.wrapNumber(n.doubleValue());
        if (value instanceof byte[] arr) {
            ILuaTable t = engine.createTable();
            for (int i = 0; i < arr.length; i++) t.rawset(i + 1, engine.wrapNumber(arr[i] & 0xFF));
            return t;
        }
        if (value instanceof int[] arr) {
            ILuaTable t = engine.createTable();
            for (int i = 0; i < arr.length; i++) t.rawset(i + 1, engine.wrapNumber(arr[i]));
            return t;
        }
        if (value instanceof long[] arr) {
            ILuaTable t = engine.createTable();
            for (int i = 0; i < arr.length; i++) t.rawset(i + 1, engine.wrapNumber(arr[i]));
            return t;
        }
        if (value instanceof java.util.List<?> list) {
            ILuaTable t = engine.createTable();
            for (int i = 0; i < list.size(); i++) {
                t.rawset(i + 1, wrapValue(engine, list.get(i)));
            }
            return t;
        }
        if (value instanceof java.util.Map<?, ?> map) {
            ILuaTable t = engine.createTable();
            for (java.util.Map.Entry<?, ?> entry : map.entrySet()) {
                t.rawset(String.valueOf(entry.getKey()), wrapValue(engine, entry.getValue()));
            }
            return t;
        }
        return engine.nilValue();
    }

    /** Recursively converts a Lua table/value to a Java Map (NbtCodec shape). */
    @NotNull
    public static java.util.Map<String, Object> toJavaMap(@NotNull ILuaTable table) {
        java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<ILuaValue, ILuaValue> entry : table.asMap().entrySet()) {
            map.put(String.valueOf(entry.getKey().toJavaObject()), toJavaValue(entry.getValue()));
        }
        return map;
    }

    /**
     * A leading dimension is only possible when the first two arguments are strings
     * (dimension, then id); a position can never be a string.
     */
    private static int resolveDimensionPrefix(ILuaValue[] args, int off) {
        if (args.length - off >= 2
                && args[off].toJavaObject() instanceof String
                && args[off + 1].toJavaObject() instanceof String) {
            return off;
        }
        return -1;
    }

    /** Same as {@link #resolveDimensionPrefix}, but the second string may be a sound category. */
    private static int resolveStopSoundDimensionPrefix(ILuaValue[] args, int off) {
        if (args.length - off >= 2
                && args[off].toJavaObject() instanceof String
                && args[off + 1].toJavaObject() instanceof String
                && !isSoundSourceName(args[off + 1].asString())) {
            return off;
        }
        return -1;
    }

    private static boolean isSoundSourceName(String value) {
        return value != null && SOUND_SOURCE_NAMES.contains(value.trim().toUpperCase(java.util.Locale.ROOT));
    }

    // ===== Capability argument parsing =====

    /** Mirrors {@code net.minecraft.core.Direction}; used only to validate Lua input. */
    private static final java.util.Set<String> SIDE_NAMES = java.util.Set.of(
            "UP", "DOWN", "NORTH", "SOUTH", "WEST", "EAST");

    private record CoordinateArgs(String dimension, int x, int y, int z, int cursor) {
    }

    private record SideParse(boolean error, String name) {
    }

    /**
     * Reads {@code [dimension,] x, y, z} and returns the cursor of the next argument.
     * A leading string is a dimension (numbered coordinates can never be one).
     */
    private static CoordinateArgs readCoordinates(ILuaValue[] args, String call) {
        int off = com.luatweaker.core.bind.LuaBinder.getOffset(args);
        int dimOff = -1;
        if (args.length - off >= 2
                && args[off].toJavaObject() instanceof String
                && args[off + 1].isNumber()) {
            dimOff = off;
            off++;
        }
        if (args.length - off >= 1 && args[off] != null && args[off].isTable()) {
            ILuaTable t = args[off].asTable();
            ILuaValue xVal = t.rawget("X"); if (xVal == null || xVal.isNil()) xVal = t.rawget("x");
            ILuaValue yVal = t.rawget("Y"); if (yVal == null || yVal.isNil()) yVal = t.rawget("y");
            ILuaValue zVal = t.rawget("Z"); if (zVal == null || zVal.isNil()) zVal = t.rawget("z");
            if (xVal != null && !xVal.isNil() && yVal != null && !yVal.isNil() && zVal != null && !zVal.isNil()) {
                ILuaValue dimVal = t.rawget("dimension");
                if (dimVal == null || dimVal.isNil()) dimVal = t.rawget("Dimension");
                String dimension = (dimVal != null && !dimVal.isNil()) ? dimVal.asString() : (dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION);
                int x = (int) Math.floor(xVal.asDouble());
                int y = (int) Math.floor(yVal.asDouble());
                int z = (int) Math.floor(zVal.asDouble());
                return new CoordinateArgs(dimension, x, y, z, off + 1);
            }
        }
        if (args.length - off < 3
                || !args[off].isNumber() || !args[off + 1].isNumber() || !args[off + 2].isNumber()) {
            warn(call + " requires (x, y, z) block coordinates");
            return null;
        }
        String dimension = dimOff >= 0 ? args[dimOff].asString() : DEFAULT_DIMENSION;
        int x = (int) Math.floor(args[off].asDouble());
        int y = (int) Math.floor(args[off + 1].asDouble());
        int z = (int) Math.floor(args[off + 2].asDouble());
        return new CoordinateArgs(dimension, x, y, z, off + 3);
    }

    private static SideParse readSide(ILuaValue[] args, int index, int length) {
        if (index >= length) {
            return new SideParse(false, null);
        }
        ILuaValue value = args[index];
        if (value == null || !value.isString()) {
            return new SideParse(true, null);
        }
        String raw = value.asString().trim();
        if (raw.isEmpty() || "NONE".equalsIgnoreCase(raw) || "NULL".equalsIgnoreCase(raw)) {
            return new SideParse(false, null);
        }
        String name = raw.toUpperCase(java.util.Locale.ROOT);
        return SIDE_NAMES.contains(name) ? new SideParse(false, name) : new SideParse(true, null);
    }

    private static Integer readIntArg(ILuaValue[] args, int index, int length) {
        if (index >= length || args[index] == null || !args[index].isNumber()) {
            return null;
        }
        return args[index].asInt();
    }

    private static String readStringArg(ILuaValue[] args, int index, int length) {
        if (index >= length || args[index] == null || !args[index].isString()) {
            return null;
        }
        return args[index].asString();
    }

    private static com.luatweaker.api.pal.IPlatformCapability capability(boolean[] warned) {
        com.luatweaker.api.pal.IPlatformCapability capability =
                com.luatweaker.api.pal.Platform.getCapabilityOrNull();
        if (capability == null && !warned[0]) {
            warned[0] = true;
            warn("World capability access is unavailable on this platform");
        }
        return capability;
    }

    private static void warnSide(String call) {
        warn(call + " side must be one of UP, DOWN, NORTH, SOUTH, WEST, EAST");
    }

    /** Reads a Vector3-shaped table; {@code null} when any component is missing or not a number. */
    private static double[] readVector(ILuaValue value) {
        if (value == null || !value.isTable()) {
            return null;
        }
        ILuaTable table = value.asTable();
        ILuaValue x = table.rawget("X");
        ILuaValue y = table.rawget("Y");
        ILuaValue z = table.rawget("Z");
        if (x == null || !x.isNumber() || y == null || !y.isNumber() || z == null || !z.isNumber()) {
            return null;
        }
        return new double[] { x.asDouble(), y.asDouble(), z.asDouble() };
    }

    private static double[] readNumbers(ILuaValue[] args, int start, int count) {
        if (args.length - start < count) {
            return null;
        }
        double[] values = new double[count];
        for (int i = 0; i < count; i++) {
            ILuaValue value = args[start + i];
            if (value == null || !value.isNumber()) {
                return null;
            }
            values[i] = value.asDouble();
        }
        return values;
    }

    private static void warnExtraArgs(String call, int cursor, int length) {
        if (cursor < length) {
            warn(call + " ignored " + (length - cursor) + " extra argument(s) starting at #" + (cursor + 1));
        }
    }

    private static void warn(String message) {
        com.luatweaker.api.log.LuaTweakerLog.get().warn(
                com.luatweaker.api.log.LogStage.SYSTEM, message);
    }

    private static Object toJavaValue(ILuaValue value) {
        if (value.isTable()) return toJavaTableValue(value.asTable());
        Object raw = value.toJavaObject();
        if (raw instanceof String || raw instanceof Boolean || raw instanceof Number) {
            return raw;
        }
        if (value.isNil()) return null;
        return raw;
    }

    /**
     * Lua tables come in two shapes that must map to different NBT forms:
     * array-like tables (integer keys 1..n, e.g. NBT lists such as {@code Items})
     * become {@code List}s, everything else becomes a {@code Map}.
     */
    private static Object toJavaTableValue(ILuaTable table) {
        java.util.Map<ILuaValue, ILuaValue> entries = table.asMap();
        if (entries.isEmpty()) return new java.util.LinkedHashMap<>();
        boolean arrayLike = true;
        for (ILuaValue key : entries.keySet()) {
            if (!(key.toJavaObject() instanceof Number num) || num.intValue() < 1) {
                arrayLike = false;
                break;
            }
        }
        if (!arrayLike) return toJavaMap(table);
        Object[] ordered = new Object[entries.size()];
        boolean placedAny = false;
        for (java.util.Map.Entry<ILuaValue, ILuaValue> entry : entries.entrySet()) {
            int index = ((Number) entry.getKey().toJavaObject()).intValue();
            if (index >= 1 && index <= ordered.length) {
                ordered[index - 1] = toJavaValue(entry.getValue());
                placedAny = true;
            }
        }
        if (!placedAny) return toJavaMap(table);
        java.util.List<Object> list = new java.util.ArrayList<>(ordered.length);
        for (Object item : ordered) {
            list.add(item);
        }
        return list;
    }

    @org.jetbrains.annotations.Nullable
    private static ILuaValue getDynamicFallback(@NotNull ILuaEngine engine, @NotNull Object rawJavaObj, @NotNull String key) {
        return EntitiesLuaBinding.readDynamicProperty(engine, rawJavaObj, key);
    }

    private static boolean setDynamicFallback(@NotNull ILuaEngine engine, @NotNull Object rawJavaObj, @NotNull String key, @NotNull ILuaValue val) {
        return EntitiesLuaBinding.writeDynamicProperty(engine, rawJavaObj, key, val);
    }

    /**
     * Builds the single Lua shape for raycast results. Also registered as the return converter
     * for {@code RaycastResult}, so {@code entity:raycast()} and {@code World:Raycast()} hand
     * scripts exactly the same table instead of an opaque userdata.
     */
    private static ILuaTable wrapRaycastResult(ILuaEngine engine, IInteractionService service,
                                               @org.jetbrains.annotations.Nullable String dimension,
                                               com.luatweaker.api.spatial.RaycastResult res) {
        ILuaTable table = engine.createTable();
        table.rawset("IsHit", engine.wrapBoolean(res.isHit()));
        table.rawset("isHit", engine.wrapBoolean(res.isHit()));
        table.rawset("HitType", engine.wrapString(res.hitType()));
        table.rawset("hitType", engine.wrapString(res.hitType()));
        table.rawset("Distance", engine.wrapNumber(res.distance()));
        table.rawset("distance", engine.wrapNumber(res.distance()));

        ILuaTable pos = engine.createTable();
        pos.rawset("X", engine.wrapNumber(res.hitX()));
        pos.rawset("Y", engine.wrapNumber(res.hitY()));
        pos.rawset("Z", engine.wrapNumber(res.hitZ()));
        pos.rawset("x", engine.wrapNumber(res.hitX()));
        pos.rawset("y", engine.wrapNumber(res.hitY()));
        pos.rawset("z", engine.wrapNumber(res.hitZ()));
        table.rawset("Position", pos);
        table.rawset("position", pos);

        ILuaTable norm = engine.createTable();
        norm.rawset("X", engine.wrapNumber(res.normalX()));
        norm.rawset("Y", engine.wrapNumber(res.normalY()));
        norm.rawset("Z", engine.wrapNumber(res.normalZ()));
        norm.rawset("x", engine.wrapNumber(res.normalX()));
        norm.rawset("y", engine.wrapNumber(res.normalY()));
        norm.rawset("z", engine.wrapNumber(res.normalZ()));
        table.rawset("Normal", norm);
        table.rawset("normal", norm);

        if (res.entity() != null) {
            table.rawset("Entity", EntitiesLuaBinding.createEntityLuaTable(engine, res.entity()));
            table.rawset("entity", table.rawget("Entity"));
        } else {
            table.rawset("Entity", engine.nilValue());
            table.rawset("entity", engine.nilValue());
        }

        if (res.block() != null) {
            String blockDimension = dimension != null ? dimension : res.block().getDimension();
            IInteractableBlock block = service.GetBlock(blockDimension,
                    res.block().getX(), res.block().getY(), res.block().getZ());
            table.rawset("Block", block != null ? wrapBlock(engine, block) : engine.nilValue());
            table.rawset("block", table.rawget("Block"));
        } else {
            table.rawset("Block", engine.nilValue());
            table.rawset("block", engine.nilValue());
        }

        return table;
    }
}
