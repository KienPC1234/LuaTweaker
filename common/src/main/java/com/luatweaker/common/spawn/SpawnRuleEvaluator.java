package com.luatweaker.common.spawn;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.spawn.SpawnRule;
import com.luatweaker.spawn.SpawnRuleServiceImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates declarative {@link SpawnRule}s for one candidate position. The vanilla natural
 * spawner calls {@code ChunkGenerator#getMobsAt} for every spawn attempt, so conditions are
 * checked with the real block light, sky light, biome, time and weather at that exact block.
 *
 * <p>Runs on the server thread; the only Lua executed here is a rule's optional {@code check}
 * predicate, which is intended for rare special cases, not for every spawn.</p>
 */
public final class SpawnRuleEvaluator {

    /** A rule with its entity type resolved once per rules version. */
    public record Resolved(@NotNull SpawnRule rule, @NotNull EntityType<?> type) {}

    /** Per-attempt values shared by every rule: computed once, not once per rule. */
    public record Attempt(int y, int blockLight, int skyLight,
                          boolean day, boolean raining, boolean thundering) {}

    private static final Map<String, TagKey<Block>> TAG_CACHE = new ConcurrentHashMap<>();

    private SpawnRuleEvaluator() {}

    /** Reads the position-dependent values once for all rules of this spawn attempt. */
    @NotNull
    public static Attempt attemptAt(@NotNull ServerLevel level, @NotNull BlockPos pos) {
        return new Attempt(pos.getY(),
                level.getBrightness(LightLayer.BLOCK, pos),
                level.getBrightness(LightLayer.SKY, pos),
                level.isDay(), level.isRainingAt(pos), level.isThundering());
    }

    /** Resolves entity ids; a rule naming an unknown entity is reported once and skipped. */
    @NotNull
    public static List<Resolved> resolve(@NotNull String dimensionId, @NotNull List<SpawnRule> rules) {
        List<Resolved> resolved = new ArrayList<>(rules.size());
        for (SpawnRule rule : rules) {
            Optional<EntityType<?>> type =
                    com.luatweaker.common.interaction.EntitySpawner.resolveEntityType(rule.entity());
            if (type.isEmpty()) {
                LuaTweakerLog.get().error(LogStage.SYSTEM,
                        "Spawn rule for '" + dimensionId + "' references unknown entity '"
                                + rule.entity() + "'; the rule is ignored");
                continue;
            }
            if (rule.category() != null
                    && !rule.category().equalsIgnoreCase(type.get().getCategory().getSerializedName())) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "Spawn rule for '" + dimensionId + "': entity '" + rule.entity()
                                + "' is category '" + type.get().getCategory().getSerializedName()
                                + "' but the rule says '" + rule.category()
                                + "'; the rule will never match");
            }
            resolved.add(new Resolved(rule, type.get()));
        }
        return List.copyOf(resolved);
    }

    /**
     * True when the rule allows a spawn of {@code category} at {@code pos}.
     *
     * @param biome   the biome holder the natural spawner is using for this attempt
     * @param attempt light/time/weather values already read for this position
     */
    public static boolean matches(@NotNull Resolved resolved, @NotNull MobCategory category,
                                  @NotNull ServerLevel level, @NotNull Holder<Biome> biome,
                                  @NotNull BlockPos pos, @NotNull String dimensionId,
                                  @NotNull SpawnRuleServiceImpl spawnRules,
                                  @NotNull Attempt attempt) {
        SpawnRule rule = resolved.rule();
        if (resolved.type().getCategory() != category) { return false; }
        if (!categoryMatches(rule, category)) { return false; }
        if (!scalarConditionsAllow(rule, attempt.y(), attempt.blockLight(), attempt.skyLight(),
                attempt.day(), attempt.raining(), attempt.thundering())) {
            return false;
        }
        if (!biomeMatches(rule, biome)) { return false; }
        if (!blockConditionsAllow(rule, level, pos)) { return false; }
        return rule.check() == null
                || spawnRules.runCheck(rule, buildContext(spawnRules, dimensionId, rule, biome, pos, attempt));
    }

    /** {@code category} is optional; when set it must agree with the entity's real category. */
    private static boolean categoryMatches(@NotNull SpawnRule rule, @NotNull MobCategory category) {
        return rule.category() == null || rule.category().equalsIgnoreCase(category.getSerializedName());
    }

    private static boolean biomeMatches(@NotNull SpawnRule rule, @NotNull Holder<Biome> biome) {
        if (rule.biomes().isEmpty()) { return true; }
        String biomeId = biome.unwrapKey().map(key -> key.location().toString()).orElse("");
        return rule.biomes().contains(biomeId);
    }

    /** Block-below filters and the solid-ground requirement; both are optional. */
    private static boolean blockConditionsAllow(@NotNull SpawnRule rule, @NotNull ServerLevel level,
                                                @NotNull BlockPos pos) {
        if (rule.blocksBelow().isEmpty() && !rule.requireSolidGround()) { return true; }
        BlockState below = level.getBlockState(pos.below());
        if (!rule.blocksBelow().isEmpty() && !matchesBlockFilter(below, rule.blocksBelow())) { return false; }
        return !rule.requireSolidGround() || below.isSolid();
    }

    /**
     * Pure scalar conditions shared by every rule: Y range, block light, sky light, time and
     * weather. Kept free of Minecraft types so the spawn gating is unit-tested.
     */
    static boolean scalarConditionsAllow(@NotNull SpawnRule rule, int y, int blockLight, int skyLight,
                                         boolean day, boolean raining, boolean thundering) {
        if (y < rule.minY() || y > rule.maxY()) { return false; }
        if (blockLight < rule.minBlockLight() || blockLight > rule.maxBlockLight()) { return false; }
        if (skyLight < rule.minSkyLight() || skyLight > rule.maxSkyLight()) { return false; }
        if (rule.requireDay() != null && rule.requireDay() != day) { return false; }
        if (rule.requireRaining() != null && rule.requireRaining() != raining) { return false; }
        return rule.requireThundering() == null || rule.requireThundering() == thundering;
    }

    private static boolean matchesBlockFilter(@NotNull BlockState state, @NotNull List<String> filters) {
        for (String filter : filters) {
            if (filter.startsWith("#")) {
                TagKey<Block> tag = TAG_CACHE.computeIfAbsent(filter,
                        key -> TagKey.create(Registries.BLOCK, ResourceLocation.parse(key.substring(1))));
                if (state.is(tag)) { return true; }
            } else if (state.getBlock().builtInRegistryHolder().key().location().toString().equals(filter)) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    private static ILuaTable buildContext(@NotNull SpawnRuleServiceImpl spawnRules,
                                          @NotNull String dimensionId, @NotNull SpawnRule rule,
                                          @NotNull Holder<Biome> biome, @NotNull BlockPos pos,
                                          @NotNull Attempt attempt) {
        ILuaTable context = spawnRules.getEngine().createTable();
        context.rawset("dimensionId", dimensionId);
        context.rawset("entity", rule.entity());
        context.rawset("x", pos.getX());
        context.rawset("y", pos.getY());
        context.rawset("z", pos.getZ());
        context.rawset("blockLight", attempt.blockLight());
        context.rawset("skyLight", attempt.skyLight());
        context.rawset("time", attempt.day() ? "day" : "night");
        context.rawset("weather", attempt.thundering() ? "thunder" : attempt.raining() ? "rain" : "clear");
        biome.unwrapKey().ifPresent(key -> context.rawset("biome", key.location().toString()));
        return context;
    }
}
