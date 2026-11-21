package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Locale;
import java.util.Map;

/** Translates Lua block-builder fields into vanilla {@link BlockBehaviour.Properties}. */
final class BlockPropertyFactory {
    /** Vanilla default block friction; only applied when the Lua builder overrides it. */
    private static final float DEFAULT_BLOCK_FRICTION = 0.6f;
    /** Lua sound-type names mapped to vanilla sound types. */
    private static final Map<String, SoundType> SOUND_TYPES = Map.ofEntries(
            Map.entry("WOOD", SoundType.WOOD),
            Map.entry("GRAVEL", SoundType.GRAVEL),
            Map.entry("METAL", SoundType.METAL),
            Map.entry("GLASS", SoundType.GLASS),
            Map.entry("GRASS", SoundType.GRASS),
            Map.entry("SAND", SoundType.SAND),
            Map.entry("SNOW", SoundType.SNOW),
            Map.entry("WOOL", SoundType.WOOL),
            Map.entry("ANVIL", SoundType.ANVIL),
            Map.entry("NETHERITE", SoundType.NETHERITE_BLOCK));

    private BlockPropertyFactory() {
    }

    /** True when the builder needs a block entity (container, pipe, machine, BER or GeckoLib model). */
    static boolean hasBlockEntity(IBlockBuilder builder) {
        return builder.isContainer() || builder.isConnectionState()
                || builder.getTickHandler() != null || builder.getRenderHandler() != null
                || builder.getEnergyCapacity() > 0 || builder.getFluidCapacity() > 0
                || builder.getGeoModel() != null;
    }

    static BlockBehaviour.Properties create(IBlockBuilder builder) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .strength(builder.getHardness(), builder.getResistance())
                .lightLevel(state -> builder.getLightLevel());
        applySoundAndFriction(props, builder);
        applyMining(props, builder);
        applyAppearance(props, builder);
        applyPhysics(props, builder);
        return props.jumpFactor(builder.getJumpFactor()).speedFactor(builder.getSpeedFactor());
    }

    private static void applySoundAndFriction(BlockBehaviour.Properties props, IBlockBuilder builder) {
        if (builder.getSoundType() != null) {
            props.sound(parseSoundType(builder.getSoundType()));
        }
        if (builder.getFriction() != DEFAULT_BLOCK_FRICTION) {
            props.friction(builder.getFriction());
        }
    }

    private static void applyMining(BlockBehaviour.Properties props, IBlockBuilder builder) {
        if (builder.getRequiresTool() || builder.getMiningLevel() > 0 || builder.getMineableWith() != null) {
            props.requiresCorrectToolForDrops();
        }
        if (builder.getMapColor() != null) {
            var parsedColor = com.luatweaker.platform.content.BlockPropertyParser.parseMapColor(builder.getMapColor());
            if (parsedColor != null) {
                props.mapColor(parsedColor);
            }
        }
    }

    private static void applyAppearance(BlockBehaviour.Properties props, IBlockBuilder builder) {
        if (builder.isNoCollision()) {
            props.noCollission();
        }
        if (builder.isNoOcclusion() || builder.isConnectionState()
                || builder.getGeoModel() != null || !builder.getBoxes().isEmpty()) {
            props.noOcclusion();
        }
        applyPushReaction(props, builder);
        applyOffsetType(props, builder);
    }

    private static void applyPushReaction(BlockBehaviour.Properties props, IBlockBuilder builder) {
        if (builder.getPushReaction() == null) {
            return;
        }
        var reaction = com.luatweaker.platform.content.BlockPropertyParser.parsePushReaction(builder.getPushReaction());
        if (reaction != null) {
            props.pushReaction(reaction);
        }
    }

    private static void applyOffsetType(BlockBehaviour.Properties props, IBlockBuilder builder) {
        if (builder.getOffsetType() == null) {
            return;
        }
        var offset = com.luatweaker.platform.content.BlockPropertyParser.parseOffsetType(builder.getOffsetType());
        if (offset != null) {
            props.offsetType(offset);
        }
    }

    private static void applyPhysics(BlockBehaviour.Properties props, IBlockBuilder builder) {
        if (builder.isReplaceable()) {
            props.replaceable();
        }
        if (builder.isIgnitedByLava()) {
            props.ignitedByLava();
        }
        if (builder.isLiquid()) {
            props.liquid();
        }
        if (builder.getRedstoneConductor() != null) {
            boolean conductor = builder.getRedstoneConductor();
            props.isRedstoneConductor((state, level, pos) -> conductor);
        }
    }

    private static SoundType parseSoundType(String name) {
        return name == null
                ? SoundType.STONE
                : SOUND_TYPES.getOrDefault(name.toUpperCase(Locale.ROOT), SoundType.STONE);
    }
}



