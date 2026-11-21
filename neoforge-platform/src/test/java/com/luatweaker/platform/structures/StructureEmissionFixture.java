package com.luatweaker.platform.structures;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.structures.StructureServiceImpl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Shared engine/service/datapack setup for the structure emission tests. */
final class StructureEmissionFixture {

    private StructureEmissionFixture() {
    }

    record EmissionContext(ILuaEngine engine, StructureServiceImpl service,
            DatapackServiceImpl datapack, File dataRoot) {
    }

    static EmissionContext prepare(String modId, Path tempDir) {
        File dataRoot = tempDir.resolve(modId).resolve("data").toFile();
        return new EmissionContext(LuaEngineFactory.create(false), new StructureServiceImpl(),
                new DatapackServiceImpl(), dataRoot);
    }

    static ILuaTable config(ILuaEngine engine, String template, String[] biomes,
            int spacing, int separation) {
        ILuaTable table = engine.createTable();
        table.rawset("template", template);
        ILuaTable biomeTable = engine.createTable();
        for (int index = 0; index < biomes.length; index++) {
            biomeTable.rawset(index + 1, biomes[index]);
        }
        table.rawset("spawnBiomes", biomeTable);
        table.rawset("spacing", (double) spacing);
        table.rawset("separation", (double) separation);
        return table;
    }

    static void writeNbt(File dataRoot, String namespace, String path) throws IOException {
        Path file = dataRoot.toPath().resolve(namespace).resolve("structure").resolve(path + ".nbt");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[] {0x0A, 0x00, 0x07});
    }

    static Map<String, String> apply(EmissionContext context) {
        new NeoForgeStructureProvider(context.service(), context.datapack(),
                List.of(context.dataRoot())).applyAll();
        return context.datapack().getVirtualFiles();
    }
}
