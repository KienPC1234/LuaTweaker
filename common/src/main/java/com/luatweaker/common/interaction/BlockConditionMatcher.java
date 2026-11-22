package com.luatweaker.common.interaction;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Matches a Lua block-condition value (id string, "#tag", list of alternatives or a
 * table with block/tag/properties/nbt keys) against a live block state.
 */
final class BlockConditionMatcher {

    private static final String WILDCARD = "*";
    private static final String MINECRAFT_PREFIX = "minecraft:";

    private BlockConditionMatcher() {
    }

    static boolean matches(@NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull BlockState state,
                           @Nullable Object condition) {
        if (condition == null) {
            return true;
        }
        if (condition instanceof String text) {
            return matchesText(state, text);
        }
        if (condition instanceof List<?> alternatives) {
            for (Object item : alternatives) {
                if (matches(level, pos, state, item)) {
                    return true;
                }
            }
            return false;
        }
        return condition instanceof Map<?, ?> spec && matchesMap(level, pos, state, spec);
    }

    private static boolean matchesText(@NotNull BlockState state, @NotNull String text) {
        if (WILDCARD.equals(text)) {
            return true;
        }
        if (text.startsWith("#")) {
            return inTag(state, text);
        }
        return BlockStateCodec.idOf(state).equalsIgnoreCase(withNamespace(text));
    }

    private static boolean matchesMap(ServerLevel level, BlockPos pos, BlockState state, Map<?, ?> spec) {
        Object blockObj = spec.get("block") != null ? spec.get("block") : spec.get("id");
        if (blockObj != null && !BlockStateCodec.idOf(state).equalsIgnoreCase(withNamespace(blockObj.toString()))) {
            return false;
        }
        Object tagObj = spec.get("tag");
        if (tagObj != null && !inTag(state, tagObj.toString())) {
            return false;
        }
        Object propsObj = spec.get("properties");
        if (propsObj instanceof Map<?, ?> propsMap && !matchesProperties(state, propsMap)) {
            return false;
        }
        Object nbtObj = spec.get("nbt");
        return !(nbtObj instanceof Map<?, ?> nbtMap) || matchesNbt(level, pos, nbtMap);
    }

    private static boolean matchesProperties(BlockState state, Map<?, ?> propsMap) {
        for (Map.Entry<?, ?> entry : propsMap.entrySet()) {
            Property<?> property = state.getBlock().getStateDefinition().getProperty(entry.getKey().toString());
            if (property == null) {
                return false;
            }
            if (!state.getValue(property).toString().equalsIgnoreCase(entry.getValue().toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesNbt(ServerLevel level, BlockPos pos, Map<?, ?> nbtMap) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return false;
        }
        Map<String, Object> data = NbtCodec.toMap(blockEntity.saveWithFullMetadata(level.registryAccess()));
        for (Map.Entry<?, ?> entry : nbtMap.entrySet()) {
            Object actual = data.get(entry.getKey().toString());
            if (actual == null || !actual.toString().equals(entry.getValue().toString())) {
                return false;
            }
        }
        return true;
    }

    private static boolean inTag(BlockState state, String rawTag) {
        String tagText = rawTag.startsWith("#") ? rawTag.substring(1) : rawTag;
        ResourceLocation tagId = ResourceLocation.tryParse(tagText);
        if (tagId == null) {
            return false;
        }
        TagKey<Block> tagKey = TagKey.create(Registries.BLOCK, tagId);
        return state.is(tagKey);
    }

    private static String withNamespace(String id) {
        return id.contains(":") ? id : MINECRAFT_PREFIX + id;
    }
}
