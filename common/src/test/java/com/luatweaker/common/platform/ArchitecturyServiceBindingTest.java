package com.luatweaker.common.platform;

import com.luatweaker.api.platform.IArchitecturyService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.common.support.MinecraftBootstrapExtension;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;

/**
 * Verifies {@link IArchitecturyService} Lua binding conversion, {@code @LuaDefault} handling,
 * and {@link ArchitecturyServiceImpl} boundary guards.
 */
@ExtendWith(MinecraftBootstrapExtension.class)
class ArchitecturyServiceBindingTest {

    private static final String MOD_JEI = "jei";

    private ILuaEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    void luaBindingForwardsPlatformQueriesToolTransformsFuelAndTrades() {
        RecordingArchitecturyService recorder = new RecordingArchitecturyService();
        engine = LuaEngineFactory.create(false);
        LuaBinder.bind(engine, "Architectury", recorder, IArchitecturyService.class, "ArchitecturyService");

        engine.executeStringOrThrow("""
                local arch = require("LuaTweaker.Architectury")
                assert(arch:GetLoader() == "neoforge")
                assert(arch:GetMinecraftVersion() == "1.21.1")
                assert(arch:IsDevelopmentEnvironment() == true)
                assert(arch:IsModLoaded("jei") == true)
                assert(#arch:GetLoadedMods() == 2)
                local info = arch:GetModInfo("jei")
                assert(info.Name == "Just Enough Items" and info.Version == "19.0.0")
                assert(arch:GetModInfo("missing") == nil)

                assert(arch:RegisterFuel("minecraft:coal", 1600) == true)
                assert(arch:GetBurnTime("minecraft:coal") == 1600)
                assert(arch:AddToolTransform("strip", "minecraft:oak_log", "minecraft:stripped_oak_log") == true)
                assert(arch:AddWanderingTrade("minecraft:emerald", "minecraft:diamond") == true)
                assert(arch:AddWanderingTrade("minecraft:emerald", "minecraft:netherite_scrap", {
                    BuyCount = 12,
                    SellCount = 2,
                    Rare = true,
                    Buy2 = "minecraft:gold_ingot",
                    Buy2Count = 4
                }) == true)
                """, "architectury_service_lua_test");

        Assertions.assertAll("Architectury service recorded invocations",
                () -> Assertions.assertEquals("strip:minecraft:oak_log->minecraft:stripped_oak_log",
                        recorder.lastTransform, "tool transform must be forwarded"),
                () -> Assertions.assertEquals("minecraft:emerald->minecraft:netherite_scrap|12|2|true",
                        recorder.lastTrade, "wandering trade options must unpack cleanly"));
    }

    @Test
    void implRejectsBlankNonBurnableAndNonAxisInputsSafely() {
        ArchitecturyServiceImpl impl = new ArchitecturyServiceImpl();

        Assertions.assertAll("ArchitecturyServiceImpl defensive validation",
                () -> Assertions.assertFalse(impl.isModLoaded(""), "blank mod ID must return false"),
                () -> Assertions.assertNull(impl.getModInfo("  "), "blank mod info lookup must return null"),
                () -> Assertions.assertFalse(impl.registerFuel("", 100), "blank item ID must reject fuel registration"),
                () -> Assertions.assertFalse(impl.registerFuel("minecraft:stick", 0), "non-positive burn ticks must fail"),
                () -> Assertions.assertFalse(impl.registerFuel("minecraft:missing_item", 200), "unknown item must fail"),
                () -> Assertions.assertEquals(0, impl.getBurnTime(""), "blank item ID burn time must be 0"),
                () -> Assertions.assertFalse(impl.addToolTransform("unknown_tool", "minecraft:dirt", "minecraft:coarse_dirt"),
                        "unsupported tool kind must fail"),
                () -> Assertions.assertFalse(impl.addToolTransform("strip", "minecraft:stone", "minecraft:cobblestone"),
                        "blocks without AXIS property must reject strip registration"),
                () -> Assertions.assertFalse(impl.addWanderingTrade("", "minecraft:diamond", null),
                        "blank buy item must fail wandering trade registration"),
                () -> Assertions.assertFalse(impl.addWanderingTrade("minecraft:emerald", "minecraft:unknown_item", Map.of()),
                        "unknown sell item must fail wandering trade registration"));
    }

    private static final class RecordingArchitecturyService implements IArchitecturyService {
        String lastTransform = "";
        String lastTrade = "";
        int coalFuel;

        @Override public @NotNull String getLoader() { return "neoforge"; }
        @Override public @NotNull String getMinecraftVersion() { return "1.21.1"; }
        @Override public boolean isDevelopmentEnvironment() { return true; }
        @Override public boolean isModLoaded(@NotNull String modId) { return MOD_JEI.equals(modId); }
        @Override public @NotNull List<String> getLoadedMods() { return List.of("luatweaker", MOD_JEI); }

        @Override
        public @Nullable Map<String, Object> getModInfo(@NotNull String modId) {
            return MOD_JEI.equals(modId)
                    ? Map.of("Id", MOD_JEI, "Name", "Just Enough Items", "Version", "19.0.0")
                    : null;
        }

        @Override
        public boolean registerFuel(@NotNull String itemId, int burnTicks) {
            coalFuel = burnTicks;
            return !itemId.isBlank() && burnTicks > 0;
        }

        @Override
        public int getBurnTime(@NotNull String itemId) {
            return "minecraft:coal".equals(itemId) ? coalFuel : 0;
        }

        @Override
        public boolean addToolTransform(@NotNull String toolKind, @NotNull String inputBlockId,
                                        @NotNull String resultBlockId) {
            lastTransform = toolKind + ":" + inputBlockId + "->" + resultBlockId;
            return true;
        }

        @Override
        public boolean addWanderingTrade(@NotNull String buyItemId, @NotNull String sellItemId,
                                         @Nullable Map<String, Object> options) {
            Map<String, Object> opts = options == null ? Map.of() : options;
            Object buyCount = opts.getOrDefault("BuyCount", 1);
            Object sellCount = opts.getOrDefault("SellCount", 1);
            Object rare = opts.getOrDefault("Rare", false);
            lastTrade = buyItemId + "->" + sellItemId + "|" + buyCount + "|" + sellCount + "|" + rare;
            Assertions.assertTrue(opts.isEmpty() || opts.containsKey("BuyCount"), "options map passed cleanly");
            return true;
        }
    }
}
