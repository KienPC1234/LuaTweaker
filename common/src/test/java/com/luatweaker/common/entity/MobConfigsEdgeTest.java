package com.luatweaker.common.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/** Unknown ids, null keys and clear() must never leak state. */
class MobConfigsEdgeTest {

    @Test
    void unknownIdYieldsNull() {
        LuaMobConfigs.clear();

        assertNull(LuaMobConfigs.get("luatweaker:never_registered"),
                "an id that was never stored must yield null");
    }

    @Test
    void nullIdYieldsNull() {
        LuaMobConfigs.clear();

        assertNull(LuaMobConfigs.get(null),
                "a null id must yield null instead of throwing");
    }

    @Test
    void nullKeyPutIsIgnored() {
        LuaMobConfigs.clear();
        LuaMobConfigs.put(null, new LuaMobConfigs.Snapshot("humanoid", "none", null, null, null));

        assertNull(LuaMobConfigs.get(null),
                "a null key must never be stored");
    }

    @Test
    void nullSnapshotPutIsIgnored() {
        LuaMobConfigs.clear();
        LuaMobConfigs.put("luatweaker:x", null);

        assertNull(LuaMobConfigs.get("luatweaker:x"),
                "a null snapshot must never be stored");
    }

    @Test
    void clearForgetsAll() {
        LuaMobConfigs.put(MobConfigsPutTest.WISP_ID,
                new LuaMobConfigs.Snapshot("humanoid", "none", null, null, null));
        LuaMobConfigs.clear();

        assertNull(LuaMobConfigs.get(MobConfigsPutTest.WISP_ID),
                "clear must forget every stored snapshot");
    }
}
