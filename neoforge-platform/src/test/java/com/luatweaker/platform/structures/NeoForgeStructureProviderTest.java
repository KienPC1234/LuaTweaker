package com.luatweaker.platform.structures;

import com.google.gson.JsonParser;
import com.luatweaker.api.event.IEventService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.structures.StructureServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The structure provider must turn every validated registration into datapack
 * files that reference the template, spacing and biomes - and must skip (loudly,
 * not crashingly) registrations whose NBT template is absent.
 */
public class NeoForgeStructureProviderTest {

    @TempDir
    Path tempDir;

    private ILuaEngine engine;
    private StructureServiceImpl service;
    private DatapackServiceImpl datapack;
    private File dataRoot;

    private void setup(String modId) throws Exception {
        engine = LuaEngineFactory.create(false);
        service = new StructureServiceImpl();
        datapack = new DatapackServiceImpl();
        dataRoot = tempDir.resolve(modId).resolve("data").toFile();
    }

    private ILuaTable config(String template, String[] biomes, int spacing, int separation) {
        ILuaTable table = engine.createTable();
        table.rawset("template", template);
        ILuaTable biomeTable = engine.createTable();
        for (int i = 0; i < biomes.length; i++) {
            biomeTable.rawset(i + 1, biomes[i]);
        }
        table.rawset("spawnBiomes", biomeTable);
        table.rawset("spacing", (double) spacing);
        table.rawset("separation", (double) separation);
        return table;
    }

    private void writeNbt(String namespace, String path) throws Exception {
        Path file = dataRoot.toPath().resolve(namespace).resolve("structure").resolve(path + ".nbt");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{0x0A, 0x00, 0x07});
    }

    private Map<String, String> apply() {
        new NeoForgeStructureProvider(service, datapack, List.of(dataRoot)).applyAll();
        return datapack.getVirtualFiles();
    }

    @Test
    public void emitsStructurePoolSetAndBiomeTag() throws Exception {
        setup("mymod");
        writeNbt("mymod", "structures/temple");
        service.register("mymod:ancient_temple",
                config("mymod:structures/temple",
                        new String[]{"minecraft:desert", "minecraft:badlands"}, 32, 16));

        Map<String, String> files = apply();

        assertEquals(4, files.size(), "structure + pool + set + biome tag expected, got: " + files.keySet());
        assertTrue(files.containsKey("data/mymod/worldgen/structure/ancient_temple.json"));
        assertTrue(files.containsKey("data/mymod/worldgen/template_pool/ancient_temple.json"));
        assertTrue(files.containsKey("data/mymod/worldgen/structure_set/ancient_temple.json"));
        assertTrue(files.containsKey("data/mymod/tags/worldgen/biome/has_structure/ancient_temple.json"));

        for (Map.Entry<String, String> entry : files.entrySet()) {
            assertDoesNotThrow(() -> JsonParser.parseString(entry.getValue()),
                    "emitted JSON must parse (balanced brackets): " + entry.getKey());
        }

        String structure = files.get("data/mymod/worldgen/structure/ancient_temple.json");
        assertTrue(structure.contains("\"type\":\"minecraft:jigsaw\""));
        assertTrue(structure.contains("mymod:ancient_temple") || structure.contains("has_structure/ancient_temple"));
        assertTrue(structure.contains("\"biomes\":\"#mymod:has_structure/ancient_temple\""));
        assertTrue(structure.contains("\"start_pool\":\"mymod:ancient_temple\""));

        String pool = files.get("data/mymod/worldgen/template_pool/ancient_temple.json");
        assertTrue(pool.contains("\"element_type\":\"minecraft:single_pool_element\""));
        assertTrue(pool.contains("\"location\":\"mymod:structures/temple\""));
        assertTrue(pool.contains("\"fallback\":\"minecraft:empty\""));

        String set = files.get("data/mymod/worldgen/structure_set/ancient_temple.json");
        assertTrue(set.contains("\"type\":\"minecraft:random_spread\""));
        assertTrue(set.contains("\"spacing\":32"));
        assertTrue(set.contains("\"separation\":16"));
        assertTrue(set.contains("\"structure\":\"mymod:ancient_temple\""));

        String tag = files.get("data/mymod/tags/worldgen/biome/has_structure/ancient_temple.json");
        assertTrue(tag.contains("minecraft:desert"));
        assertTrue(tag.contains("minecraft:badlands"));
    }

    @Test
    public void missingTemplateSkipsLoudlyWithoutFiles() throws Exception {
        setup("mymod");
        writeNbt("mymod", "structures/kept");
        service.register("mymod:ghost",
                config("mymod:structures/ghost", new String[]{"minecraft:plains"}, 32, 8));
        service.register("mymod:kept",
                config("mymod:structures/kept", new String[]{"minecraft:plains"}, 32, 8));

        Map<String, String> files = apply();

        assertEquals(4, files.size(), "only the resolvable registration may emit files");
        assertTrue(files.keySet().stream().allMatch(k -> k.contains("kept")));
        assertTrue(files.keySet().stream().noneMatch(k -> k.contains("ghost")));
    }

    @Test
    public void placementSaltIsDeterministic() throws Exception {
        setup("mymod");
        writeNbt("mymod", "structures/temple");
        service.register("mymod:ancient_temple",
                config("mymod:structures/temple", new String[]{"minecraft:desert"}, 48, 12));

        Map<String, String> first = apply();
        datapack.clear();
        Map<String, String> second = apply();

        assertEquals(first.get("data/mymod/worldgen/structure_set/ancient_temple.json"),
                second.get("data/mymod/worldgen/structure_set/ancient_temple.json"));
        assertTrue(second.get("data/mymod/worldgen/structure_set/ancient_temple.json").contains("\"spacing\":48"));
        assertTrue(second.get("data/mymod/worldgen/structure_set/ancient_temple.json").contains("\"separation\":12"));
    }

    @Test
    public void structuresAppliedEventFiresWithCounts() throws Exception {
        setup("mymod");
        writeNbt("mymod", "structures/kept");
        service.register("mymod:ghost",
                config("mymod:structures/ghost", new String[]{"minecraft:plains"}, 32, 8));
        service.register("mymod:kept",
                config("mymod:structures/kept", new String[]{"minecraft:plains"}, 32, 8));

        List<String> fired = new ArrayList<>();
        List<ILuaTable> payloads = new ArrayList<>();
        LuaServiceRegistry.register("Events", new IEventService() {
            @Override public void listen(String eventName, Object callback) {}
            @Override public void post(String eventName, ILuaTable payload) {}
            @Override public void fireEvent(String eventName, ILuaTable payload) {
                fired.add(eventName);
                payloads.add(payload);
            }
            @Override public boolean fireCancellable(String eventName, ILuaTable payload) { return true; }
            @Override public ILuaEngine getEngine() { return engine; }
        });
        try {
            apply();
            assertTrue(fired.contains("StructuresApplied"), "StructuresApplied must fire: " + fired);
            ILuaTable payload = payloads.get(fired.indexOf("StructuresApplied"));
            assertEquals(1, payload.rawget("applied").asInt());
            assertEquals(1, payload.rawget("skipped").asInt());
        } finally {
            LuaServiceRegistry.clear();
        }
    }
}
