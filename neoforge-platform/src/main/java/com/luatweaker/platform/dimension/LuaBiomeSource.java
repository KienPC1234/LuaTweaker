package com.luatweaker.platform.dimension;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Biome source for Lua-generated dimensions.
 *
 * <p>At decode time (world load) the configured biome ids are resolved into
 * holders through the {@link RegistryOps} of the loading operation, so
 * datapack biomes work. Columns pick a biome deterministically from the
 * weighted list, or via the Lua biome provider callback when one is set.</p>
 */
public class LuaBiomeSource extends BiomeSource {

    public static final MapCodec<LuaBiomeSource> MAP_CODEC = new MapCodec<>() {
        @Override
        public <T> com.mojang.serialization.DataResult<LuaBiomeSource> decode(
                DynamicOps<T> ops, MapLike<T> input) {
            return Codec.STRING.parse(ops, input.get("dimension_id"))
                    .map(dimensionId -> LuaBiomeSource.decode(dimensionId, ops));
        }

        @Override
        public <T> com.mojang.serialization.RecordBuilder<T> encode(
                LuaBiomeSource input, DynamicOps<T> ops, com.mojang.serialization.RecordBuilder<T> prefix) {
            return prefix.add("dimension_id", Codec.STRING.encodeStart(ops, input.dimensionId));
        }

        @Override
        public <T> java.util.stream.Stream<T> keys(DynamicOps<T> ops) {
            return java.util.stream.Stream.of(ops.createString("dimension_id"));
        }
    };

    private final String dimensionId;
    private final List<BiomeHolderEntry> biomes;

    private LuaBiomeSource(@NotNull String dimensionId, @NotNull List<BiomeHolderEntry> biomes) {
        this.dimensionId = dimensionId;
        this.biomes = List.copyOf(biomes);
    }

    public String getDimensionId() {
        return dimensionId;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return MAP_CODEC;
    }

    @Override
    protected java.util.stream.Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream().map(BiomeHolderEntry::holder);
    }

    /**
     * Resolves the configured biome ids via the registry access of the
     * loading operation. Fails loudly when the dimension is unknown or has
     * no biomes, so a broken dimension is caught at world load.
     */
    private static LuaBiomeSource decode(String dimensionId, DynamicOps<?> ops) {
        if (ops instanceof RegistryOps<?> registryOps) {
            Optional<HolderGetter<Biome>> getter = registryOps.getter(Registries.BIOME);
            if (getter.isPresent()) {
                return new LuaBiomeSource(dimensionId, resolveBiomes(dimensionId, getter.get()));
            }
        }
        throw new IllegalStateException("Cannot resolve biomes for dimension '" + dimensionId
                + "': no registry access available while decoding the biome source");
    }

    private static List<BiomeHolderEntry> resolveBiomes(String dimensionId, HolderGetter<Biome> getter) {
        Object serviceObj = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        if (!(serviceObj instanceof DimensionServiceImpl service)) {
            throw new IllegalStateException("Dimension service is not registered; dimensions cannot be generated");
        }
        DimensionConfig config = service.getConfig(dimensionId);
        if (config == null) {
            throw new IllegalStateException("Dimension '" + dimensionId
                    + "' is not registered; call Dimensions:Create(\"" + dimensionId + "\", {...}) before loading the world");
        }
        List<BiomeHolderEntry> out = new ArrayList<>();
        for (DimensionConfig.BiomeEntry entry : config.biomes()) {
            ResourceLocation id = ResourceLocation.tryParse(entry.id());
            if (id == null) continue;
            Holder<Biome> holder = getter.getOrThrow(ResourceKey.create(Registries.BIOME, id));
            out.add(new BiomeHolderEntry(entry.id(), holder, entry.weight()));
        }
        if (out.isEmpty()) {
            throw new IllegalStateException("Dimension '" + dimensionId
                    + "' has no usable biomes; configure at least one biome in Dimensions:Create");
        }
        return out;
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        DimensionServiceImpl service = currentService();
        String biomeId = service != null ? service.computeBiomeId(dimensionId, x, z) : null;
        if (biomeId != null) {
            for (BiomeHolderEntry entry : biomes) {
                if (entry.id().equals(biomeId)) return entry.holder();
            }
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Biome decision '" + biomeId + "' for dimension '" + dimensionId
                            + "' is not in the resolved biome list; falling back to weighted pick");
        }
        return weightedPick(x, z);
    }

    /**
     * Deterministic weighted pick from the configured biome list.
     * Columns are grouped into cells of `biomeSize` blocks (from the config)
     * so biomes form regions instead of per-column speckle.
     */
    private Holder<Biome> weightedPick(int x, int z) {
        int cellSize = DimensionConfig.DEFAULT_BIOME_SIZE;
        DimensionServiceImpl service = currentService();
        DimensionConfig config = service != null ? service.getConfig(dimensionId) : null;
        if (config != null) {
            cellSize = config.biomeSize();
        }
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        long hash = (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xBF58476D1CE4E5B9L);
        hash ^= hash >>> 32;
        int total = 0;
        for (BiomeHolderEntry entry : biomes) total += entry.weight();
        int roll = (int) ((hash & 0x7FFFFFFFFFFFFFFFL) % Math.max(1, total));
        for (BiomeHolderEntry entry : biomes) {
            roll -= entry.weight();
            if (roll < 0) return entry.holder();
        }
        return biomes.get(0).holder();
    }

    private static DimensionServiceImpl currentService() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        return service instanceof DimensionServiceImpl dim ? dim : null;
    }

    /** One configured biome: id, resolved holder and weight. */
    public record BiomeHolderEntry(@NotNull String id, @NotNull Holder<Biome> holder, int weight) {}
}
