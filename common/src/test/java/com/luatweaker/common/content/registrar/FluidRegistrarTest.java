package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.content.FluidBuilderImpl;
import dev.architectury.core.fluid.ArchitecturyFluidAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Rarity;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers custom fluid attribute defaults, rarity parsing, and server-only onTouch dispatch.
 */
class FluidRegistrarTest {

    private static IPlayer stubPlayer() {
        return (IPlayer) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[]{IPlayer.class},
                (proxy, method, args) -> "PlayerStub");
    }

    @Test
    void defaultFluidAttributesProvideNonNullVanillaWaterTextures() {
        IFluidBuilder builder = new FluidBuilderImpl("liquid_crystal").color(0x33CCFF);
        ArchitecturyFluidAttributes attrs = FluidAttributesFactory.build(
                builder,
                id -> ResourceLocation.parse(id.contains(":") ? id : "luatweaker:" + id),
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>(),
                new AtomicReference<>());

        assertEquals(
                List.of(
                        ResourceLocation.withDefaultNamespace("block/water_still"),
                        ResourceLocation.withDefaultNamespace("block/water_flow"),
                        0xFF33CCFF),
                List.of(
                        attrs.getSourceTexture(),
                        attrs.getFlowingTexture(),
                        attrs.getColor()),
                "unset fluid textures must fall back to vanilla water sprites with opaque ARGB tint");
    }

    @Test
    void parseRarityMapsAllSupportedTiersAndDefaultsToCommon() {
        assertEquals(
                List.of(Rarity.COMMON, Rarity.COMMON, Rarity.UNCOMMON, Rarity.RARE, Rarity.EPIC),
                List.of(
                        FluidAttributesFactory.parseRarity(null),
                        FluidAttributesFactory.parseRarity("unknown"),
                        FluidAttributesFactory.parseRarity("uncommon"),
                        FluidAttributesFactory.parseRarity("RARE"),
                        FluidAttributesFactory.parseRarity("epic")),
                "parseRarity must map case-insensitive rarity names and fall back to COMMON");
    }

    @Test
    void dispatchTouchFiresOnlyForServerSideNonNullPlayers() {
        AtomicInteger calls = new AtomicInteger();
        FluidBuilderImpl builder = new FluidBuilderImpl("acid");
        builder.onTouch(player -> calls.incrementAndGet());
        IPlayer player = stubPlayer();

        LuaLiquidBlock.dispatchTouch(builder, true, player);
        LuaLiquidBlock.dispatchTouch(builder, false, null);
        LuaLiquidBlock.dispatchTouch(builder, false, player);

        assertEquals(1, calls.get(),
                "fluid onTouch must run once on the server for a live player and skip client/null calls");
    }

    @Test
    void dispatchTouchContainsCallbackExceptionsInsideFirewall() {
        FluidBuilderImpl builder = new FluidBuilderImpl("unstable_fluid");
        builder.onTouch(player -> {
            throw new IllegalStateException("boom");
        });

        assertDoesNotThrow(() -> LuaLiquidBlock.dispatchTouch(builder, false, stubPlayer()),
                "throwing fluid onTouch callbacks must be isolated by CallbackFirewall");
    }
}
