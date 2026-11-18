package com.luatweaker.structures;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class StructureServiceImplTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine engine;
    private StructureServiceImpl service;

    @BeforeEach
    void setup() {
        engine = LuaEngineFactory.create(false);
        service = new StructureServiceImpl();
    }

    private ILuaTable config(Object... kv) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < kv.length; i += 2) {
            String key = (String) kv[i];
            Object value = kv[i + 1];
            if (value instanceof String s) table.rawset(key, s);
            // Unbox explicitly: a boxed Double would otherwise bind to
            // rawset(String, Object) and be stored as userdata, not a number.
            else if (value instanceof Double d) table.rawset(key, d.doubleValue());
            else if (value instanceof Integer n) table.rawset(key, n.doubleValue());
            else if (value instanceof ILuaTable t) table.rawset(key, t);
            else throw new IllegalArgumentException("unsupported test value: " + value);
        }
        return table;
    }

    private ILuaTable biomeList(String... biomes) {
        ILuaTable table = engine.createTable();
        for (int i = 0; i < biomes.length; i++) {
            table.rawset(i + 1, biomes[i]);
        }
        return table;
    }

    private ILuaTable fullConfig() {
        ILuaTable loot = engine.createTable();
        loot.rawset("chest", "mymod:chests/temple_treasure");
        return config("template", "mymod:structures/temple",
                "spawnBiomes", biomeList("minecraft:desert", "minecraft:badlands"),
                "spacing", 32, "separation", 16, "spawnChance", 0.8,
                "lootTables", loot);
    }

    @Test
    void register_StoresExactMapping() {
        service.register("mymod:ancient_temple", fullConfig());

        StructureServiceImpl.StructureEntry entry = service.getEntry("mymod:ancient_temple");
        assertNotNull(entry);
        assertEquals("mymod:ancient_temple", entry.structureId());
        assertEquals("mymod:structures/temple", entry.template());
        assertEquals(List.of("minecraft:desert", "minecraft:badlands"), entry.spawnBiomes());
        assertEquals(32, entry.spacing());
        assertEquals(16, entry.separation());
        assertEquals(0.8, entry.spawnChance(), 1e-9);
        assertEquals(Map.of("chest", "mymod:chests/temple_treasure"), entry.lootTables());
    }

    @Test
    void register_AppliesVanillaDefaults() {
        service.register("mymod:ruin", config("template", "mymod:structures/ruin",
                "spawnBiomes", biomeList("minecraft:plains")));

        StructureServiceImpl.StructureEntry entry = service.getEntry("mymod:ruin");
        assertNotNull(entry);
        assertEquals(32, entry.spacing(), "default spacing must match vanilla desert_pyramids (32)");
        assertEquals(8, entry.separation(), "default separation must match vanilla desert_pyramids (8)");
        assertEquals(1.0, entry.spawnChance(), 1e-9);
        assertTrue(entry.lootTables().isEmpty());
    }

    @Test
    void register_RejectsBlankStructureId() {
        assertThrows(IllegalArgumentException.class, () -> service.register("", fullConfig()));
        assertThrows(IllegalArgumentException.class, () -> service.register("   ", fullConfig()));
        assertThrows(IllegalArgumentException.class, () -> service.register("UPPER:case", fullConfig()));
    }

    @Test
    void register_RequiresTemplate() {
        assertThrows(IllegalArgumentException.class, () -> service.register("mymod:x",
                config("spawnBiomes", biomeList("minecraft:plains"))));
    }

    @Test
    void register_RequiresNonEmptySpawnBiomes() {
        assertThrows(IllegalArgumentException.class, () -> service.register("mymod:x",
                config("template", "mymod:structures/x")));
        assertThrows(IllegalArgumentException.class, () -> service.register("mymod:x",
                config("template", "mymod:structures/x", "spawnBiomes", engine.createTable())));
        assertThrows(IllegalArgumentException.class, () -> service.register("mymod:x",
                config("template", "mymod:structures/x", "spawnBiomes", biomeList("not a biome!"))));
    }

    @Test
    void register_RejectsSpacingBelowSeparation() {
        assertThrows(IllegalArgumentException.class, () -> service.register("mymod:x",
                config("template", "mymod:structures/x", "spawnBiomes", biomeList("minecraft:plains"),
                        "spacing", 8, "separation", 16)));
    }

    @Test
    void register_RejectsOutOfRangeSpawnChance() {
        assertThrows(IllegalArgumentException.class, () -> service.register("mymod:x",
                config("template", "mymod:structures/x", "spawnBiomes", biomeList("minecraft:plains"),
                        "spawnChance", -0.1)));
        assertThrows(IllegalArgumentException.class, () -> service.register("mymod:x",
                config("template", "mymod:structures/x", "spawnBiomes", biomeList("minecraft:plains"),
                        "spawnChance", 1.5)));
    }

    @Test
    void register_RejectsInvalidLootTables() {
        assertThrows(IllegalArgumentException.class, () -> service.register("mymod:x",
                config("template", "mymod:structures/x", "spawnBiomes", biomeList("minecraft:plains"),
                        "lootTables", "not-a-table")));
    }

    @Test
    void unregister_RemovesEntryAndReports() {
        service.register("mymod:temple", fullConfig());

        assertTrue(service.unregister("mymod:temple"));
        assertNull(service.getEntry("mymod:temple"));
        assertFalse(service.unregister("mymod:temple"), "second unregister must report absent");
        assertFalse(service.unregister("mymod:never_registered"));
    }

    @Test
    void unregister_RejectsBlankId() {
        assertThrows(IllegalArgumentException.class, () -> service.unregister(""));
    }

    @Test
    void getRegistrations_ReturnsSummary() {
        service.register("mymod:b", config("template", "mymod:structures/b",
                "spawnBiomes", biomeList("minecraft:plains")));
        service.register("mymod:a", config("template", "mymod:structures/a",
                "spawnBiomes", biomeList("minecraft:desert")));

        Map<String, Object> summary = service.getRegistrations();
        assertEquals(2, summary.get("structures"));
        assertEquals(List.of("mymod:a", "mymod:b"), summary.get("ids"));
        assertEquals(List.of("mymod:a", "mymod:b"), service.getStructureIds());
    }

    @Test
    void clearAll_RemovesEverything() {
        service.register("mymod:temple", fullConfig());
        service.clearAll();

        assertNull(service.getEntry("mymod:temple"));
        assertTrue(service.getStructureIds().isEmpty());
        assertEquals(0, service.getRegistrations().get("structures"));
    }

    @Test
    void register_ReplacesExistingEntry() {
        service.register("mymod:temple", fullConfig());
        service.register("mymod:temple", config("template", "mymod:structures/temple_v2",
                "spawnBiomes", biomeList("minecraft:forest")));

        assertEquals("mymod:structures/temple_v2", service.getEntry("mymod:temple").template());
        assertEquals(1, service.getRegistrations().get("structures"));
    }
}
