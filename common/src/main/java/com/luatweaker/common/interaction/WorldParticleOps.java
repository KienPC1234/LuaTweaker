package com.luatweaker.common.interaction;

import com.luatweaker.api.particle.ParticleOptionsSpec;
import com.luatweaker.api.particle.ParticleSpawn;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

/** Particle spawn primitives, including option-bearing particle types (dust/block/item). */
final class WorldParticleOps {

    private static final String PARTICLE_QUOTE = "Particle '";

    private WorldParticleOps() {
    }

    static void spawnParticle(@NotNull String dimension, @NotNull String particleId,
                              @NotNull ParticleSpawn spawn) {
        ServerLevels.run(ServerLevels.resolve(dimension),
                level -> spawnIn(level, particleId, spawn));
    }

    static void spawnParticleOptions(@NotNull String dimension, @NotNull String particleId,
                                     @NotNull ParticleSpawn spawn,
                                     @NotNull ParticleOptionsSpec spec) {
        ServerLevels.run(ServerLevels.resolve(dimension),
                level -> spawnOptionsIn(level, particleId, spawn, spec));
    }

    private static void spawnIn(ServerLevel level, String particleId, ParticleSpawn spawn) {
        var rl = ParticleIds.particle(particleId);
        var particleOpt = BuiltInRegistries.PARTICLE_TYPE.getOptional(rl);
        if (particleOpt.isPresent()
                && particleOpt.get() instanceof net.minecraft.core.particles.ParticleOptions options) {
            send(level, options, spawn);
        }
    }

    private static void spawnOptionsIn(ServerLevel level, String particleId,
                                       ParticleSpawn spawn, ParticleOptionsSpec spec) {
        var rl = ParticleIds.particle(particleId);
        var typeOpt = BuiltInRegistries.PARTICLE_TYPE.getOptional(rl);
        if (typeOpt.isEmpty()) {
            throw new IllegalArgumentException("Unknown particle type: '" + particleId + "'");
        }
        send(level, createOptions(typeOpt.get(), spec, particleId), spawn);
    }

    private static void send(ServerLevel level, net.minecraft.core.particles.ParticleOptions options,
                             ParticleSpawn spawn) {
        level.sendParticles(options, spawn.x(), spawn.y(), spawn.z(),
                Math.max(1, spawn.count()), spawn.vx(), spawn.vy(), spawn.vz(), spawn.speed());
    }

    private static net.minecraft.core.particles.ParticleOptions createOptions(
            net.minecraft.core.particles.ParticleType<?> type,
            ParticleOptionsSpec spec, String particleId) {
        if (spec.colorRgb() != null) {
            return dust(spec, particleId, type);
        }
        if (spec.blockId() != null) {
            return block(spec, particleId, type);
        }
        if (spec.itemId() != null) {
            return item(spec, particleId, type);
        }
        throw new IllegalArgumentException(PARTICLE_QUOTE + particleId + "' has no option payload");
    }

    private static net.minecraft.core.particles.ParticleOptions dust(
            ParticleOptionsSpec spec, String particleId,
            net.minecraft.core.particles.ParticleType<?> type) {
        if (type != net.minecraft.core.particles.ParticleTypes.DUST) {
            throw new IllegalArgumentException(
                    PARTICLE_QUOTE + particleId + "' does not accept Color/Size options");
        }
        int rgb = spec.colorRgb();
        float r = ((rgb >> 16) & 0xFF) / 255.0F;
        float g = ((rgb >> 8) & 0xFF) / 255.0F;
        float b = (rgb & 0xFF) / 255.0F;
        float scale = spec.scale() == null ? 1.0F : spec.scale().floatValue();
        return new net.minecraft.core.particles.DustParticleOptions(
                new org.joml.Vector3f(r, g, b), scale);
    }

    private static net.minecraft.core.particles.ParticleOptions block(
            ParticleOptionsSpec spec, String particleId,
            net.minecraft.core.particles.ParticleType<?> type) {
        var block = BuiltInRegistries.BLOCK.getOptional(
                ParticleIds.resource(spec.blockId(), "particle block"));
        if (block.isEmpty()) {
            throw new IllegalArgumentException("Unknown particle block: '" + spec.blockId() + "'");
        }
        var state = block.get().defaultBlockState();
        if (type == net.minecraft.core.particles.ParticleTypes.BLOCK) {
            return new net.minecraft.core.particles.BlockParticleOption(
                    net.minecraft.core.particles.ParticleTypes.BLOCK, state);
        }
        if (type == net.minecraft.core.particles.ParticleTypes.FALLING_DUST) {
            return new net.minecraft.core.particles.BlockParticleOption(
                    net.minecraft.core.particles.ParticleTypes.FALLING_DUST, state);
        }
        throw new IllegalArgumentException(
                PARTICLE_QUOTE + particleId + "' does not accept a Block option");
    }

    private static net.minecraft.core.particles.ParticleOptions item(
            ParticleOptionsSpec spec, String particleId,
            net.minecraft.core.particles.ParticleType<?> type) {
        if (type != net.minecraft.core.particles.ParticleTypes.ITEM) {
            throw new IllegalArgumentException(
                    PARTICLE_QUOTE + particleId + "' does not accept an Item option");
        }
        var item = BuiltInRegistries.ITEM.getOptional(
                ParticleIds.resource(spec.itemId(), "particle item"));
        if (item.isEmpty()) {
            throw new IllegalArgumentException("Unknown particle item: '" + spec.itemId() + "'");
        }
        return new net.minecraft.core.particles.ItemParticleOption(
                net.minecraft.core.particles.ParticleTypes.ITEM,
                new net.minecraft.world.item.ItemStack(item.get()));
    }
}
