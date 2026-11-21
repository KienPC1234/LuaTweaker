package com.luatweaker.interaction;

import com.luatweaker.api.pal.IPlatformInteraction;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins the multiblock primitives: redstone reads, pattern checks, cross-block transfers, cuboid
 * validation and batched block-state updates, including the exact argument order they use.
 */
class MultiblockPrimitivesBindingTest {

    private final List<String> calls = new ArrayList<>();
    private ILuaEngine engine;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        InteractionLuaBinding.registerBindings(engine);
        Platform.setInteraction(recordingInteraction(calls));
    }

    @AfterEach
    void tearDown() {
        engine.shutdown();
        Platform.setInteraction(null);
    }

    private static IPlatformInteraction recordingInteraction(List<String> calls) {
        return (IPlatformInteraction) Proxy.newProxyInstance(
                Thread.currentThread().getContextClassLoader(),
                new Class<?>[] { IPlatformInteraction.class },
                new RecordingInteraction(calls));
    }

    @Test
    void worldMultiblockPrimitivesForwardTheirArguments() {
        engine.executeStringOrThrow("""
                local signal = World:GetRedstoneSignal(10, 64, 20, "UP")
                assert(signal == 15, "signal must be 15")

                local pattern = {
                    { dx = 0, dy = -1, dz = 0, block = "minecraft:blast_furnace" },
                    { dx = 1, dy = 0, dz = 0, tag = "c:storage_blocks/iron" }
                }
                local matched = World:CheckPattern(10, 64, 20, pattern)
                assert(matched == true, "pattern must match")

                local feMoved = World:TransferEnergy({X=10, Y=64, Z=20}, {X=11, Y=64, Z=20}, 500)
                assert(feMoved == 500, "feMoved must be 500")

                local fluidMoved = World:TransferFluid({X=10, Y=64, Z=20}, {X=10, Y=65, Z=20}, 1000)
                assert(fluidMoved == 1000, "fluidMoved must be 1000")

                local itemMoved = World:TransferItem({X=10, Y=64, Z=20}, 0, {X=10, Y=63, Z=20}, 1, 16)
                assert(itemMoved == 16, "itemMoved must be 16")
                """, "multiblock_test");

        assertEquals(List.of(
                        "getRedstoneSignal 10,64,20 dir=UP",
                        "checkPattern origin=10,64,20 size=2",
                        "transferEnergy from=10,64,20 to=11,64,20 max=500 sim=false",
                        "transferFluid from=10,64,20 to=10,65,20 max=1000 sim=false",
                        "transferItem from=10,64,20[0] to=10,63,20[1] max=16 sim=false"),
                calls,
                "redstone/pattern/energy/fluid/item multiblock primitives must forward coordinates in order");
    }

    @Test
    void worldCuboidValidationAndBatchUpdateForwardTheirArguments() {
        engine.executeStringOrThrow("""
                local spec = {
                    frame = "mymod:casing",
                    walls = "mymod:casing",
                    interior = "minecraft:air"
                }
                local res = World:ValidateCuboid({X=0, Y=64, Z=0}, {X=2, Y=66, Z=2}, spec)
                assert(res.valid == true, "validation must succeed")
                assert(res.width == 3, "width must be 3")

                local updated = World:SetBlockStatesInBox({X=0, Y=64, Z=0}, {X=2, Y=66, Z=2},
                        "mymod:casing", { formed = true })
                assert(updated == 26, "updated count must be 26")
                """, "cuboid_test");

        assertEquals(List.of(
                        "validateCuboid 0,64,0 to 2,66,2",
                        "setBlockStatesInBox 0,64,0 to 2,66,2 filter=mymod:casing"),
                calls,
                "cuboid validation and batch updates must forward both corners and the filter block");
    }

    /**
     * Records the multiblock calls as {@code name x,y,z ...} strings. A dynamic proxy is used
     * instead of a MockPlatformInteraction subclass because {@code transferItem} declares 11
     * parameters: a concrete test double would have to repeat that signature, which is a
     * parameter-list smell in its own right.
     */
    private static final class RecordingInteraction implements InvocationHandler {
        private static final int FROM_X = 1;
        private static final int TO_X = 4;
        private static final int ITEM_TO_X = 5;
        private static final int TRANSFER_MAX = 7;
        private static final int TRANSFER_SIMULATE = 8;

        private final List<String> calls;

        private RecordingInteraction(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            return switch (method.getName()) {
                case "getRedstoneSignal" -> getRedstoneSignal(args);
                case "checkPattern" -> checkPattern(args);
                case "transferEnergy" -> transferEnergy(args);
                case "transferFluid" -> transferFluid(args);
                case "transferItem" -> transferItem(args);
                case "validateCuboid" -> validateCuboid(args);
                case "setBlockStatesInBox" -> recordBlockStatesInBox(args);
                default -> defaultValue(method.getReturnType());
            };
        }

        private Object getRedstoneSignal(Object... args) {
            calls.add("getRedstoneSignal " + coordinates(args, FROM_X) + " dir=" + args[4]);
            return 15;
        }

        private Object checkPattern(Object... args) {
            List<?> pattern = (List<?>) args[4];
            calls.add("checkPattern origin=" + coordinates(args, FROM_X) + " size=" + pattern.size());
            return true;
        }

        private Object transferEnergy(Object... args) {
            calls.add("transferEnergy from=" + coordinates(args, FROM_X)
                    + " to=" + coordinates(args, TO_X)
                    + " max=" + args[TRANSFER_MAX] + " sim=" + args[TRANSFER_SIMULATE]);
            return 500;
        }

        private Object transferFluid(Object... args) {
            calls.add("transferFluid from=" + coordinates(args, FROM_X)
                    + " to=" + coordinates(args, TO_X)
                    + " max=" + args[TRANSFER_MAX] + " sim=" + args[TRANSFER_SIMULATE]);
            return 1000;
        }

        private Object transferItem(Object... args) {
            calls.add("transferItem from=" + coordinates(args, FROM_X) + "[" + args[4] + "]"
                    + " to=" + coordinates(args, ITEM_TO_X) + "[" + args[8] + "]"
                    + " max=" + args[9] + " sim=" + args[10]);
            return 16;
        }

        private Object validateCuboid(Object... args) {
            calls.add("validateCuboid " + coordinates(args, FROM_X) + " to " + coordinates(args, TO_X));
            return Map.of("valid", true, "width", 3, "height", 3, "depth", 3);
        }

        private Object recordBlockStatesInBox(Object... args) {
            calls.add("setBlockStatesInBox " + coordinates(args, FROM_X) + " to "
                    + coordinates(args, TO_X) + " filter=" + args[7]);
            return 26;
        }

        private static String coordinates(Object[] args, int first) {
            return args[first] + "," + args[first + 1] + "," + args[first + 2];
        }

        private static Object defaultValue(Class<?> type) {
            return type.isPrimitive() ? Array.get(Array.newInstance(type, 1), 0) : null;
        }
    }
}
