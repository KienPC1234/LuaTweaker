package com.luatweaker.common.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Stored mob snapshots must round-trip every field. */
class MobConfigsPutTest {

    static final String WISP_ID = "luatweaker:wisp";

    static LuaMobConfigs.Snapshot storedSnapshot() {
        LuaMobConfigs.clear();
        LuaMobConfigs.put(WISP_ID, new LuaMobConfigs.Snapshot(
                "quadruped", "animal", "minecraft:entity.cow.ambient", null,
                "minecraft:entity.cow.death"));
        return LuaMobConfigs.get(WISP_ID);
    }

    @Test
    void storedSnapshotIsNotNull() {
        assertNotNull(storedSnapshot(),
                "a stored snapshot must be retrievable by id");
    }

    @Test
    void storedSnapshotKeepsRig() {
        assertEquals("quadruped", storedSnapshot().rig(),
                "the rig must round-trip through the store");
    }

    @Test
    void storedSnapshotKeepsDefaultGoals() {
        assertEquals("animal", storedSnapshot().defaultGoals(),
                "the default goals must round-trip through the store");
    }

    @Test
    void storedSnapshotKeepsAmbientSound() {
        assertEquals("minecraft:entity.cow.ambient", storedSnapshot().ambientSound(),
                "the ambient sound must round-trip through the store");
    }

    @Test
    void storedSnapshotKeepsNullHurtSound() {
        assertNull(storedSnapshot().hurtSound(),
                "a null hurt sound must round-trip as null");
    }
}
