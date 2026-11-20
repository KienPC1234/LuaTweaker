package com.luatweaker.platform.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class LuaMobConfigsTest {

    @Test
    void putAndGet_RoundTripSnapshot() {
        LuaMobConfigs.clear();
        LuaMobConfigs.put("luatweaker:wisp", new LuaMobConfigs.Snapshot(
                "quadruped", "animal", "minecraft:entity.cow.ambient", null, "minecraft:entity.cow.death"));

        LuaMobConfigs.Snapshot snapshot = LuaMobConfigs.get("luatweaker:wisp");
        assertNotNull(snapshot);
        assertEquals("quadruped", snapshot.rig());
        assertEquals("animal", snapshot.defaultGoals());
        assertEquals("minecraft:entity.cow.ambient", snapshot.ambientSound());
        assertNull(snapshot.hurtSound());
    }

    @Test
    void get_UnknownOrNullIdYieldsNull() {
        LuaMobConfigs.clear();
        assertNull(LuaMobConfigs.get("luatweaker:never_registered"));
        assertNull(LuaMobConfigs.get(null));
    }

    @Test
    void put_NullKeyOrSnapshotIgnored() {
        LuaMobConfigs.clear();
        LuaMobConfigs.put(null, new LuaMobConfigs.Snapshot("humanoid", "none", null, null, null));
        LuaMobConfigs.put("luatweaker:x", null);
        assertNull(LuaMobConfigs.get(null));
        assertNull(LuaMobConfigs.get("luatweaker:x"));
    }

    @Test
    void clear_ForgetsAll() {
        LuaMobConfigs.put("luatweaker:wisp", new LuaMobConfigs.Snapshot("humanoid", "none", null, null, null));
        LuaMobConfigs.clear();
        assertNull(LuaMobConfigs.get("luatweaker:wisp"));
    }
}
