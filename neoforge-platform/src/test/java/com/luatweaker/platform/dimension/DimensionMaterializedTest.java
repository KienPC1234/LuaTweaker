package com.luatweaker.platform.dimension;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.dimension.DimensionLuaBinding;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Materializing dimensions must fire DimensionsMaterialized and handle emptiness. */
class DimensionMaterializedTest {

    @BeforeEach
    void setup() {
        LuaServiceRegistry.clear();
    }

    private static List<String> materializeWithRealm(List<ILuaTable> payloads) {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);
        engine.executeString(
            "Dimensions:Create('arcane:crystal_realm', {\n"
            + "    seaLevel = 40,\n"
            + "    minHeight = -32,\n"
            + "    maxHeight = 128\n"
            + "})\n",
            "setup_evt"
        );

        List<String> fired = new ArrayList<>();
        LuaServiceRegistry.register("Events", new IEventService() {
            @Override
            public void listen(String eventName, Object callback) {
            }

            @Override
            public void post(String eventName, ILuaTable payload) {
            }

            @Override
            public void fireEvent(String eventName, ILuaTable payload) {
                fired.add(eventName);
                payloads.add(payload);
            }

            @Override
            public boolean fireCancellable(String eventName, ILuaTable payload) {
                return true;
            }

            @Override
            public ILuaEngine getEngine() {
                return engine;
            }
        });
        try {
            NeoForgeDimensionProvider.applyAll(new FakeDimensionDatapack());
        } finally {
            LuaServiceRegistry.clear();
        }
        return fired;
    }

    @Test
    void emptyRegistryWritesNothing() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        DimensionLuaBinding.registerBindings(engine);
        FakeDimensionDatapack datapack = new FakeDimensionDatapack();
        NeoForgeDimensionProvider.applyAll(datapack);

        assertTrue(datapack.files.isEmpty(),
                "no dimensions -> no datapack files");
    }

    @Test
    void materializedEventFires() {
        List<String> fired = materializeWithRealm(new ArrayList<>());

        assertTrue(fired.contains("DimensionsMaterialized"),
                "DimensionsMaterialized must fire: " + fired);
    }

    @Test
    void materializedPayloadCarriesTheDimensionId() {
        List<ILuaTable> payloads = new ArrayList<>();
        List<String> fired = materializeWithRealm(payloads);
        ILuaTable dimensions = payloads.get(fired.indexOf("DimensionsMaterialized"))
                .rawget("dimensions").asTable();

        assertEquals("arcane:crystal_realm", dimensions.rawget(1).asString(),
                "the materialized payload must carry the new dimension id");
    }
}
