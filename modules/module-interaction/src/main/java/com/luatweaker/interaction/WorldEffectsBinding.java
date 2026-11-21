package com.luatweaker.interaction;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.bind.LuaBinder;

/** Binds particle and sound effects (SpawnParticle, PlaySound, StopSound). */
final class WorldEffectsBinding {

    private WorldEffectsBinding() {
    }

    static void bindWorkspaceEffects(ILuaEngine engine, ILuaTable workspaceTable) {
        bindSpawnParticle(engine, workspaceTable);
        bindPlaySound(engine, workspaceTable);
        bindStopSound(engine, workspaceTable);
    }

    private static void bindSpawnParticle(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("SpawnParticle", args -> {
            int off = LuaBinder.getOffset(args);
            int dimOff = InteractionArgReaders.resolveDimensionPrefix(args, off);
            if (dimOff >= 0) {
                off++;
            }
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                InteractionBindingSupport.warn("World:SpawnParticle requires a particle id and a position");
                return engine.nilValue();
            }
            String dimension = dimOff >= 0 ? args[dimOff].asString()
                    : InteractionBindingSupport.DEFAULT_DIMENSION;
            String particleId = args[off].asString();

            int cursor = off + 1;
            PositionAndVelocity placed = readPositionVelocity(args, cursor);
            if (placed == null) {
                InteractionBindingSupport.warn(
                        "World:SpawnParticle('" + particleId + "') needs a Vector3 position or three numbers");
                return engine.nilValue();
            }
            double[] position = placed.position();
            double[] velocity = placed.velocity();
            cursor = placed.cursor();

            int count = 1;
            double speed = 0.0;
            if (cursor < args.length && args[cursor].isNumber()) {
                count = args[cursor++].asInt();
            }
            if (cursor < args.length && args[cursor].isNumber()) {
                speed = args[cursor++].asDouble();
            }
            InteractionBindingSupport.warnExtraArgs("World:SpawnParticle", cursor, args.length);

            com.luatweaker.api.pal.Platform.getInteraction().spawnParticle(
                    dimension, particleId,
                    position[0], position[1], position[2],
                    velocity[0], velocity[1], velocity[2], count, speed);
            return engine.nilValue();
        });
        workspaceTable.rawset("spawnParticle", workspaceTable.rawget("SpawnParticle"));
    }

    private record SoundTail(double volume, double pitch, String source, int cursor) {
    }

    private static void bindPlaySound(ILuaEngine engine, ILuaTable workspaceTable) {
        workspaceTable.rawset("PlaySound", args -> {
            int off = LuaBinder.getOffset(args);
            int dimOff = InteractionArgReaders.resolveDimensionPrefix(args, off);
            if (dimOff >= 0) {
                off++;
            }
            if (!InteractionBindingSupport.hasMinArgs(args, off, 2)) {
                InteractionBindingSupport.warn("World:PlaySound requires a sound id and a position");
                return engine.nilValue();
            }
            String dimension = dimOff >= 0 ? args[dimOff].asString()
                    : InteractionBindingSupport.DEFAULT_DIMENSION;
            String soundId = args[off].asString();

            int cursor = off + 1;
            PositionAndVelocity placed = readPositionOnly(args, cursor);
            if (placed == null) {
                InteractionBindingSupport.warn(
                        "World:PlaySound('" + soundId + "') needs a Vector3 position or three numbers");
                return engine.nilValue();
            }
            double[] position = placed.position();
            cursor = placed.cursor();

            SoundTail tail = readSoundTail(args, cursor);
            InteractionBindingSupport.warnExtraArgs("World:PlaySound", tail.cursor(), args.length);

            com.luatweaker.api.pal.Platform.getInteraction().playSound(
                    dimension, soundId, position[0], position[1], position[2],
                    (float) tail.volume(), (float) tail.pitch(), tail.source());
            return engine.nilValue();
        });
        workspaceTable.rawset("playSound", workspaceTable.rawget("PlaySound"));
    }

    private static SoundTail readSoundTail(ILuaValue[] args, int start) {
        int cursor = start;
        double volume = 1.0;
        double pitch = 1.0;
        if (cursor < args.length && args[cursor].isNumber()) {
            volume = args[cursor++].asDouble();
        }
        if (cursor < args.length && args[cursor].isNumber()) {
            pitch = args[cursor++].asDouble();
        }
        String source = InteractionBindingSupport.DEFAULT_SOUND_SOURCE;
        if (cursor < args.length && args[cursor].isString()) {
            source = args[cursor++].asString();
        }
        return new SoundTail(volume, pitch, source, cursor);
    }

    private static void bindStopSound(ILuaEngine engine, ILuaTable workspaceTable) {
        // ===== StopSound API =====
        // StopSound(id[, source]) / StopSound(dimension, id[, source]).
        // A second string that names a sound category is treated as the source, so
        // StopSound("id", "PLAYERS") is never mistaken for a dimension.
        workspaceTable.rawset("StopSound", args -> {
            int off = LuaBinder.getOffset(args);
            int dimOff = InteractionArgReaders.resolveStopSoundDimensionPrefix(args, off);
            if (dimOff >= 0) {
                off++;
            }
            if (!InteractionBindingSupport.hasMinArgs(args, off, 1)) {
                InteractionBindingSupport.warn("World:StopSound requires a sound id");
                return engine.wrapBoolean(false);
            }
            String dimension = dimOff >= 0 ? args[dimOff].asString()
                    : InteractionBindingSupport.DEFAULT_DIMENSION;
            String soundId = args[off].asString();
            String source = InteractionBindingSupport.DEFAULT_SOUND_SOURCE;
            if (InteractionBindingSupport.hasMinArgs(args, off, 2) && args[off + 1].isString()) {
                source = args[off + 1].asString();
            }
            boolean stopped = com.luatweaker.api.pal.Platform.getInteraction()
                    .stopSound(dimension, soundId, source);
            return engine.wrapBoolean(stopped);
        });
        workspaceTable.rawset("stopSound", workspaceTable.rawget("StopSound"));
    }

    private record PositionAndVelocity(double[] position, double[] velocity, int cursor) {
    }

    private static final double[] DEFAULT_VELOCITY = { 0.0, 0.0, 0.0 };

    /**
     * Reads a position plus optional velocity starting at {@code cursor}.
     * Returns the read values or {@code null} when unparseable.
     */
    private static PositionAndVelocity readPositionVelocity(ILuaValue[] args, int start) {
        int cursor = start;
        double[] position;
        if (args[cursor].isTable()) {
            position = InteractionVectors.readVector(args[cursor++]);
        } else {
            position = InteractionVectors.readNumbers(args, cursor, 3);
            cursor += 3;
        }
        if (position.length == 0) {
            return null;
        }
        double[] velocity;
        if (cursor < args.length && args[cursor].isTable()) {
            double[] parsed = InteractionVectors.readVector(args[cursor]);
            if (parsed.length == 0) {
                return null;
            }
            velocity = parsed;
            cursor++;
        } else if (args.length - cursor >= 3 && args[cursor].isNumber()) {
            double[] parsed = InteractionVectors.readNumbers(args, cursor, 3);
            if (parsed.length == 0) {
                velocity = DEFAULT_VELOCITY;
            } else {
                velocity = parsed;
                cursor += 3;
            }
        } else {
            velocity = DEFAULT_VELOCITY;
        }
        return new PositionAndVelocity(position, velocity, cursor);
    }

    /**
     * Reads a position starting at {@code cursor}.
     * Returns the read values or {@code null} when unparseable.
     */
    private static PositionAndVelocity readPositionOnly(ILuaValue[] args, int start) {
        int cursor = start;
        double[] position;
        if (args[cursor].isTable()) {
            position = InteractionVectors.readVector(args[cursor++]);
        } else {
            position = InteractionVectors.readNumbers(args, cursor, 3);
            cursor += 3;
        }
        if (position.length == 0) {
            return null;
        }
        return new PositionAndVelocity(position, DEFAULT_VELOCITY, cursor);
    }
}

