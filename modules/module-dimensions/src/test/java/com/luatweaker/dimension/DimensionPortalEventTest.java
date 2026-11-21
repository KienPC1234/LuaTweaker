package com.luatweaker.dimension;

import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.service.LuaServiceRegistry;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the PortalUsed event fired when a player walks into a registered portal. */
class DimensionPortalEventTest extends DimensionServiceTestBase {

    private static final String PORTAL_BLOCK = "arcane:crystal_portal";
    private static final String TARGET_DIMENSION = "arcane:crystal_realm";

    private record PortalCall(boolean used, List<String> fired, String payloadBlockId,
                              String payloadTarget) {
    }

    @Test
    void portalUseFiresPortalUsedEvent() {
        assertEquals(List.of("PortalUsed"), useRegisteredPortal().fired(),
                "using a registered portal must fire the PortalUsed event");
    }

    @Test
    void portalUsedPayloadCarriesBlockAndTarget() {
        PortalCall call = useRegisteredPortal();

        assertEquals(List.of(PORTAL_BLOCK, TARGET_DIMENSION),
                List.of(call.payloadBlockId(), call.payloadTarget()),
                "PortalUsed payload must carry the portal block id and target dimension");
    }

    @Test
    void registeredPortalReturnsTrue() {
        assertTrue(useRegisteredPortal().used(),
                "using a registered portal must report that the portal was used");
    }

    private PortalCall useRegisteredPortal() {
        Platform.setDimension(dimensionPlatform(true, null, null, null));
        List<String> fired = new ArrayList<>();
        List<ILuaTable> payloads = new ArrayList<>();
        LuaServiceRegistry.register("Events", new IEventService() {
            @Override public void listen(String eventName, Object callback) { }
            @Override public void post(String eventName, ILuaTable payload) { }
            @Override public void fireEvent(String eventName, ILuaTable payload) {
                fired.add(eventName);
                payloads.add(payload);
            }
            @Override public boolean fireCancellable(String eventName, ILuaTable payload) { return true; }
            @Override public ILuaEngine getEngine() { return engine; }
        });
        try {
            service.create(TARGET_DIMENSION, engine.createTable());
            service.registerPortal(PORTAL_BLOCK, TARGET_DIMENSION);
            boolean used = service.tryUsePortal(playerWithRaw("mock-raw"), PORTAL_BLOCK);
            ILuaTable payload = payloads.isEmpty() ? null : payloads.get(0);
            return new PortalCall(used, List.copyOf(fired),
                    payload == null ? null : payload.rawget("blockId").asString(),
                    payload == null ? null : payload.rawget("targetDimension").asString());
        } finally {
            LuaServiceRegistry.clear();
        }
    }
}
