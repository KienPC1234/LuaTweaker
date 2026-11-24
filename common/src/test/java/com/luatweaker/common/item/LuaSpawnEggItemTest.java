package com.luatweaker.common.item;

import com.luatweaker.common.support.MinecraftBootstrapExtension;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.CustomData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MinecraftBootstrapExtension.class)
class LuaSpawnEggItemTest {
    private static final int PRIMARY_COLOR = 0x112233;
    private static final int SECONDARY_COLOR = 0x445566;

    private static LuaSpawnEggItem eggFor(EntityType<? extends Mob> type) {
        return new LuaSpawnEggItem(() -> type, PRIMARY_COLOR, SECONDARY_COLOR, new Item.Properties());
    }

    @Test
    void deferredTypeResolvesOnDemand() {
        LuaSpawnEggItem egg = eggFor(EntityType.COW);
        ItemStack stack = new ItemStack(egg);

        assertAll("LuaSpawnEggItem must lazily resolve type, colors, and required features",
                () -> assertEquals(EntityType.COW, egg.getType(stack), "getType should lazily resolve to COW"),
                () -> assertEquals(PRIMARY_COLOR, egg.getColor(0), "primary tint color must match"),
                () -> assertEquals(SECONDARY_COLOR, egg.getColor(1), "secondary tint color must match"),
                () -> assertNotNull(egg.requiredFeatures(), "required features must not be null")
        );
    }

    @Test
    void nbtEntityTypeOverridesSuppliedDefault() {
        LuaSpawnEggItem egg = eggFor(EntityType.COW);
        CompoundTag entityData = new CompoundTag();
        entityData.putString("id", "minecraft:sheep");
        ItemStack stack = new ItemStack(egg);
        stack.set(DataComponents.ENTITY_DATA, CustomData.of(entityData));

        assertEquals(EntityType.SHEEP, egg.getType(stack),
                "an ENTITY_DATA component must override the deferred default type");
    }

    @Test
    void resolvedEggIsVisibleToVanillaByIdLookup() {
        LuaSpawnEggItem egg = eggFor(EntityType.VEX);
        egg.getType(new ItemStack(egg));

        assertEquals(egg, SpawnEggItem.byId(EntityType.VEX),
                "a resolved Lua egg must be registered in the vanilla spawn-egg lookup");
    }

    @Test
    void supplierIsInvokedOnceAcrossRepeatedReads() {
        AtomicInteger calls = new AtomicInteger();
        LuaSpawnEggItem egg = new LuaSpawnEggItem(() -> {
            calls.incrementAndGet();
            return EntityType.FROG;
        }, PRIMARY_COLOR, SECONDARY_COLOR, new Item.Properties());
        ItemStack stack = new ItemStack(egg);

        egg.getType(stack);
        egg.getType(stack);
        egg.requiredFeatures();

        assertEquals(1, calls.get(), "the deferred type supplier must only be resolved once");
    }
}
