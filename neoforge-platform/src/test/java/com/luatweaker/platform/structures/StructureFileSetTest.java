package com.luatweaker.platform.structures;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A validated registration must emit the structure, pool, set and biome tag files. */
class StructureFileSetTest {

    private static final String STRUCTURE_KEY = "data/mymod/worldgen/structure/ancient_temple.json";
    private static final String POOL_KEY = "data/mymod/worldgen/template_pool/ancient_temple.json";
    private static final String SET_KEY = "data/mymod/worldgen/structure_set/ancient_temple.json";
    private static final String TAG_KEY = "data/mymod/tags/worldgen/biome/has_structure/ancient_temple.json";

    private static Map<String, String> templeFiles(Path tempDir) throws IOException {
        StructureEmissionFixture.EmissionContext context =
                StructureEmissionFixture.prepare("mymod", tempDir);
        StructureEmissionFixture.writeNbt(context.dataRoot(), "mymod", "structures/temple");
        context.service().register("mymod:ancient_temple",
                StructureEmissionFixture.config(context.engine(), "mymod:structures/temple",
                        new String[] {"minecraft:desert", "minecraft:badlands"}, 32, 16));
        return StructureEmissionFixture.apply(context);
    }

    @Test
    void emitsFourFiles(@TempDir Path tempDir) throws IOException {
        Map<String, String> files = templeFiles(tempDir);

        assertEquals(4, files.size(),
                "structure + pool + set + biome tag expected, got: " + files.keySet());
    }

    @Test
    void structureFileExists(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).containsKey(STRUCTURE_KEY),
                "the structure JSON must be emitted");
    }

    @Test
    void poolFileExists(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).containsKey(POOL_KEY),
                "the template pool JSON must be emitted");
    }

    @Test
    void setFileExists(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).containsKey(SET_KEY),
                "the structure set JSON must be emitted");
    }

    @Test
    void biomeTagFileExists(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).containsKey(TAG_KEY),
                "the biome tag JSON must be emitted");
    }

    @Test
    void emittedJsonParses(@TempDir Path tempDir) throws IOException {
        Map<String, String> files = templeFiles(tempDir);
        for (Map.Entry<String, String> entry : files.entrySet()) {
            assertDoesNotThrow(() -> JsonParser.parseString(entry.getValue()),
                    "emitted JSON must parse (balanced brackets): " + entry.getKey());
        }
    }
}
