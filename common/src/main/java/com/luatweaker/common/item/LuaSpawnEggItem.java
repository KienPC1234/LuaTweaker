package com.luatweaker.common.item;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Deferred spawn egg for Lua-defined entities.
 *
 * <p>The parent is constructed with a {@code null} default type because the
 * entity type is registered after the item. {@link #getType(ItemStack)} lets
 * the parent resolve any NBT entity override first, then falls back to the
 * lazy supplier. {@link #requiredFeatures()} mirrors the parent's behaviour
 * for the resolved type.</p>
 *
 * <p>The resolved type is cached. Registration into the static vanilla
 * {@code BY_ID} map is serialized on {@link #BY_ID_LOCK} because that map is
 * a plain identity hash map shared by the server and render threads.</p>
 */
public final class LuaSpawnEggItem extends SpawnEggItem {
    private static final ReentrantLock BY_ID_LOCK = new ReentrantLock();

    private final Supplier<? extends EntityType<? extends Mob>> typeSupplier;
    private final ReentrantLock resolveLock = new ReentrantLock();
    private final AtomicReference<EntityType<? extends Mob>> cachedType = new AtomicReference<>();

    public LuaSpawnEggItem(@NotNull Supplier<? extends EntityType<? extends Mob>> typeSupplier,
                           int backgroundColor, int highlightColor,
                           @NotNull Item.Properties properties) {
        super(null, backgroundColor, highlightColor, properties);
        this.typeSupplier = typeSupplier;
    }

    @Override
    public EntityType<?> getType(ItemStack stack) {
        EntityType<?> fromNbt = super.getType(stack);
        return fromNbt != null ? fromNbt : resolveType();
    }

    @Override
    public FeatureFlagSet requiredFeatures() {
        EntityType<?> type = resolveType();
        return type != null ? type.requiredFeatures() : FeatureFlags.VANILLA_SET;
    }

    private EntityType<? extends Mob> resolveType() {
        EntityType<? extends Mob> resolved = this.cachedType.get();
        return resolved != null ? resolved : resolveAndCache();
    }

    private EntityType<? extends Mob> resolveAndCache() {
        this.resolveLock.lock();
        try {
            EntityType<? extends Mob> resolved = this.cachedType.get();
            if (resolved != null) {
                return resolved;
            }
            resolved = this.typeSupplier.get();
            if (resolved == null) {
                throw new IllegalStateException("LuaSpawnEggItem entity type supplier returned null");
            }
            registerInVanillaLookup(resolved);
            this.cachedType.set(resolved);
            return resolved;
        } finally {
            this.resolveLock.unlock();
        }
    }

    private void registerInVanillaLookup(EntityType<? extends Mob> type) {
        BY_ID_LOCK.lock();
        try {
            BY_ID.put(type, this);
        } finally {
            BY_ID_LOCK.unlock();
        }
    }
}
