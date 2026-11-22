package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IBlockBuilder;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.Locale;
import java.util.Map;

/** Translates Lua block-builder fields into vanilla {@link BlockBehaviour.Properties}. */
final class BlockPropertyFactory {
    /** Vanilla default block friction; only applied when the Lua builder overrides it. */
    private static final float DEFAULT_BLOCK_FRICTION = 0.6f;
    /** Lua sound-type names mapped to vanilla sound types (verified against 1.21.1 SoundType fields). */
    private static final Map<String, SoundType> SOUND_TYPES = Map.ofEntries(
            Map.entry("STONE", SoundType.STONE),
            Map.entry("DEEPSLATE", SoundType.DEEPSLATE),
            Map.entry("DEEPSLATE_BRICKS", SoundType.DEEPSLATE_BRICKS),
            Map.entry("DEEPSLATE_TILES", SoundType.DEEPSLATE_TILES),
            Map.entry("TUFF", SoundType.TUFF),
            Map.entry("TUFF_BRICKS", SoundType.TUFF_BRICKS),
            Map.entry("POLISHED_TUFF", SoundType.POLISHED_TUFF),
            Map.entry("POLISHED_DEEPSLATE", SoundType.POLISHED_DEEPSLATE),
            Map.entry("CALCITE", SoundType.CALCITE),
            Map.entry("DRIPSTONE_BLOCK", SoundType.DRIPSTONE_BLOCK),
            Map.entry("POINTED_DRIPSTONE", SoundType.POINTED_DRIPSTONE),
            Map.entry("WOOD", SoundType.WOOD),
            Map.entry("NETHER_WOOD", SoundType.NETHER_WOOD),
            Map.entry("BAMBOO_WOOD", SoundType.BAMBOO_WOOD),
            Map.entry("CHERRY_WOOD", SoundType.CHERRY_WOOD),
            Map.entry("STEM", SoundType.STEM),
            Map.entry("GRAVEL", SoundType.GRAVEL),
            Map.entry("SUSPICIOUS_GRAVEL", SoundType.SUSPICIOUS_GRAVEL),
            Map.entry("SAND", SoundType.SAND),
            Map.entry("SUSPICIOUS_SAND", SoundType.SUSPICIOUS_SAND),
            Map.entry("SNOW", SoundType.SNOW),
            Map.entry("POWDER_SNOW", SoundType.POWDER_SNOW),
            Map.entry("WOOL", SoundType.WOOL),
            Map.entry("GLASS", SoundType.GLASS),
            Map.entry("METAL", SoundType.METAL),
            Map.entry("CHAIN", SoundType.CHAIN),
            Map.entry("COPPER", SoundType.COPPER),
            Map.entry("COPPER_GRATE", SoundType.COPPER_GRATE),
            Map.entry("COPPER_BULB", SoundType.COPPER_BULB),
            Map.entry("ANVIL", SoundType.ANVIL),
            Map.entry("NETHERITE_BLOCK", SoundType.NETHERITE_BLOCK),
            Map.entry("ANCIENT_DEBRIS", SoundType.ANCIENT_DEBRIS),
            Map.entry("LODESTONE", SoundType.LODESTONE),
            Map.entry("LADDER", SoundType.LADDER),
            Map.entry("SCAFFOLDING", SoundType.SCAFFOLDING),
            Map.entry("GRASS", SoundType.GRASS),
            Map.entry("WET_GRASS", SoundType.WET_GRASS),
            Map.entry("MOSS", SoundType.MOSS),
            Map.entry("MOSS_CARPET", SoundType.MOSS_CARPET),
            Map.entry("PINK_PETALS", SoundType.PINK_PETALS),
            Map.entry("BIG_DRIPLEAF", SoundType.BIG_DRIPLEAF),
            Map.entry("SMALL_DRIPLEAF", SoundType.SMALL_DRIPLEAF),
            Map.entry("CROP", SoundType.CROP),
            Map.entry("HARD_CROP", SoundType.HARD_CROP),
            Map.entry("SWEET_BERRY_BUSH", SoundType.SWEET_BERRY_BUSH),
            Map.entry("CAVE_VINES", SoundType.CAVE_VINES),
            Map.entry("TWISTING_VINES", SoundType.TWISTING_VINES),
            Map.entry("WEEPING_VINES", SoundType.WEEPING_VINES),
            Map.entry("VINE", SoundType.VINE),
            Map.entry("GLOW_LICHEN", SoundType.GLOW_LICHEN),
            Map.entry("SPORE_BLOSSOM", SoundType.SPORE_BLOSSOM),
            Map.entry("AZALEA", SoundType.AZALEA),
            Map.entry("AZALEA_LEAVES", SoundType.AZALEA_LEAVES),
            Map.entry("FLOWERING_AZALEA", SoundType.FLOWERING_AZALEA),
            Map.entry("CHERRY_SAPLING", SoundType.CHERRY_SAPLING),
            Map.entry("CHERRY_LEAVES", SoundType.CHERRY_LEAVES),
            Map.entry("ROOTS", SoundType.ROOTS),
            Map.entry("HANGING_ROOTS", SoundType.HANGING_ROOTS),
            Map.entry("MANGROVE_ROOTS", SoundType.MANGROVE_ROOTS),
            Map.entry("MUDDY_MANGROVE_ROOTS", SoundType.MUDDY_MANGROVE_ROOTS),
            Map.entry("MUD", SoundType.MUD),
            Map.entry("MUD_BRICKS", SoundType.MUD_BRICKS),
            Map.entry("PACKED_MUD", SoundType.PACKED_MUD),
            Map.entry("FUNGUS", SoundType.FUNGUS),
            Map.entry("NYLIUM", SoundType.NYLIUM),
            Map.entry("NETHERRACK", SoundType.NETHERRACK),
            Map.entry("NETHER_BRICKS", SoundType.NETHER_BRICKS),
            Map.entry("NETHER_ORE", SoundType.NETHER_ORE),
            Map.entry("NETHER_GOLD_ORE", SoundType.NETHER_GOLD_ORE),
            Map.entry("NETHER_SPROUTS", SoundType.NETHER_SPROUTS),
            Map.entry("NETHER_WART", SoundType.NETHER_WART),
            Map.entry("WART_BLOCK", SoundType.WART_BLOCK),
            Map.entry("SHROOMLIGHT", SoundType.SHROOMLIGHT),
            Map.entry("BASALT", SoundType.BASALT),
            Map.entry("SOUL_SAND", SoundType.SOUL_SAND),
            Map.entry("SOUL_SOIL", SoundType.SOUL_SOIL),
            Map.entry("BONE_BLOCK", SoundType.BONE_BLOCK),
            Map.entry("GILDED_BLACKSTONE", SoundType.GILDED_BLACKSTONE),
            Map.entry("HONEY_BLOCK", SoundType.HONEY_BLOCK),
            Map.entry("SLIME_BLOCK", SoundType.SLIME_BLOCK),
            Map.entry("FROGLIGHT", SoundType.FROGLIGHT),
            Map.entry("FROGSPAWN", SoundType.FROGSPAWN),
            Map.entry("CORAL_BLOCK", SoundType.CORAL_BLOCK),
            Map.entry("BAMBOO", SoundType.BAMBOO),
            Map.entry("BAMBOO_SAPLING", SoundType.BAMBOO_SAPLING),
            Map.entry("AMETHYST", SoundType.AMETHYST),
            Map.entry("AMETHYST_CLUSTER", SoundType.AMETHYST_CLUSTER),
            Map.entry("SCULK", SoundType.SCULK),
            Map.entry("SCULK_CATALYST", SoundType.SCULK_CATALYST),
            Map.entry("SCULK_SENSOR", SoundType.SCULK_SENSOR),
            Map.entry("SCULK_SHRIEKER", SoundType.SCULK_SHRIEKER),
            Map.entry("SCULK_VEIN", SoundType.SCULK_VEIN),
            Map.entry("VAULT", SoundType.VAULT),
            Map.entry("TRIAL_SPAWNER", SoundType.TRIAL_SPAWNER),
            Map.entry("HEAVY_CORE", SoundType.HEAVY_CORE),
            Map.entry("COBWEB", SoundType.COBWEB),
            Map.entry("SPONGE", SoundType.SPONGE),
            Map.entry("WET_SPONGE", SoundType.WET_SPONGE),
            Map.entry("CANDLE", SoundType.CANDLE),
            Map.entry("LANTERN", SoundType.LANTERN),
            Map.entry("LILY_PAD", SoundType.LILY_PAD),
            Map.entry("CHISELED_BOOKSHELF", SoundType.CHISELED_BOOKSHELF),
            Map.entry("DECORATED_POT", SoundType.DECORATED_POT),
            Map.entry("ROOTED_DIRT", SoundType.ROOTED_DIRT),
            Map.entry("HANGING_SIGN", SoundType.HANGING_SIGN),
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
            var parsedColor = com.luatweaker.common.content.BlockPropertyParser.parseMapColor(builder.getMapColor());
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
        var reaction = com.luatweaker.common.content.BlockPropertyParser.parsePushReaction(builder.getPushReaction());
        if (reaction != null) {
            props.pushReaction(reaction);
        }
    }

    private static void applyOffsetType(BlockBehaviour.Properties props, IBlockBuilder builder) {
        if (builder.getOffsetType() == null) {
            return;
        }
        var offset = com.luatweaker.common.content.BlockPropertyParser.parseOffsetType(builder.getOffsetType());
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
        if (name == null) {
            return SoundType.STONE;
        }
        SoundType resolved = SOUND_TYPES.get(name.toUpperCase(Locale.ROOT));
        if (resolved == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "Unknown block soundType '" + name + "': falling back to STONE.");
            return SoundType.STONE;
        }
        return resolved;
    }
}



