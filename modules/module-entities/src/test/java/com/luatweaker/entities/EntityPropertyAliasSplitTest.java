package com.luatweaker.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.Test;

/** Player-only aliases must stay off plain entity tables (and read as nil). */
class EntityPropertyAliasSplitTest {

    private static final String SUBJECT_GLOBAL = "subject";

    @Test
    void entityTablesHidePlayerOnlyProperties() throws IOException {
        ILuaEngine engine = LuaEngineFactory.create(false);
        subjectEntity(engine);

        runLua(engine, "split_hidden", SUBJECT_GLOBAL + ".GameMode");

        assertNull(toJava(engine), "GameMode must be nil on plain entity tables");
    }

    @Test
    void entityTablesHideSelectedSlot() throws IOException {
        ILuaEngine engine = LuaEngineFactory.create(false);
        subjectEntity(engine);

        runLua(engine, "split_slot", SUBJECT_GLOBAL + ".SelectedSlot");

        assertNull(toJava(engine), "SelectedSlot must be nil on plain entity tables");
    }

    @Test
    void entityTablesKeepSharedProperties() throws IOException {
        ILuaEngine engine = LuaEngineFactory.create(false);
        subjectEntity(engine);

        runLua(engine, "split_shared", SUBJECT_GLOBAL + ".Type");

        assertEquals("zombie_copy", toJava(engine), "shared aliases must keep working on entity tables");
    }

    private static void subjectEntity(ILuaEngine engine) {
        ILuaTable table = EntitiesLuaBinding.createEntityLuaTable(engine, new SplitMock());
        engine.getGlobalEnvironment().rawset(SUBJECT_GLOBAL, table);
    }

    private static void runLua(ILuaEngine engine, String name, String expression) throws IOException {
        File file = File.createTempFile(name, ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), "RESULT = (" + expression + ")");
        engine.executeScript(file, "TEST");
    }

    private static Object toJava(ILuaEngine engine) {
        ILuaValue result = engine.getGlobalEnvironment().rawget("RESULT");
        if (result == null || result.isNil()) {
            return null;
        }
        return result.toJavaObject();
    }

    private static final class SplitMock implements IEntity {
        private final Map<String, Object> customData = new ConcurrentHashMap<>();

        @Override public String getType() { return "zombie_copy"; }
        @Override public String getName() { return "Split"; }
        @Override public float getHealth() { return 20; }
        @Override public void setHealth(float health) { }
        @Override public float getMaxHealth() { return 20; }
        @Override public void setMaxHealth(float maxHealth) { }
        @Override public String getCustomName() { return ""; }
        @Override public void setCustomName(String name) { }
        @Override public void setMotion(double vx, double vy, double vz) { }
        @Override public double getMotionX() { return 0; }
        @Override public double getMotionY() { return 0; }
        @Override public double getMotionZ() { return 0; }
        @Override public double getLookVectorX() { return 0; }
        @Override public double getLookVectorY() { return 0; }
        @Override public double getLookVectorZ() { return 1; }
        @Override public double getX() { return 0; }
        @Override public double getY() { return 0; }
        @Override public double getZ() { return 0; }
        @Override public boolean isAlive() { return true; }
        @Override public void remove() { }
        @Override public Object getRawEntity() { return this; }
        @Override public Object getCustomData(String key) { return customData.get(key); }
        @Override public void setCustomData(String key, Object value) { customData.put(key, value); }
        @Override public String getDimension() { return "minecraft:overworld"; }
        @Override public float getWidth() { return 0.6f; }
        @Override public float getHeight() { return 1.8f; }
        @Override public boolean isNoGravity() { return false; }
        @Override public void setNoGravity(boolean noGravity) { }
        @Override public boolean isSilent() { return false; }
        @Override public void setSilent(boolean silent) { }
        @Override public int getAirSupply() { return 300; }
        @Override public void setAirSupply(int air) { }
        @Override public float getAbsorption() { return 0; }
        @Override public void setAbsorption(float amount) { }
        @Override public float getFallDistance() { return 0; }
        @Override public void setFallDistance(float distance) { }
        @Override public void teleport(double x, double y, double z) { }
    }
}
