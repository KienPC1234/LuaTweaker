package com.luatweaker.common.structures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The pool and set JSON must carry the template, fallback and spacing. */
class StructurePoolSetTest {

    private static final String POOL_KEY = "data/mymod/worldgen/template_pool/ancient_temple.json";
    private static final String SET_KEY = "data/mymod/worldgen/structure_set/ancient_temple.json";

    private static Map<String, String> templeFiles(Path tempDir) throws IOException {
        StructureEmissionFixture.EmissionContext context =
                StructureEmissionFixture.prepare("mymod", tempDir);
        StructureEmissionFixture.writeNbt(context.dataRoot(), "mymod", "structures/temple");
        context.service().register("mymod:ancient_temple",
                StructureEmissionFixture.config(context.engine(), "mymod:structures/temple",
                        new String[] {"minecraft:desert"}, 32, 16));
        return StructureEmissionFixture.apply(context);
    }

    @Test
    void poolUsesSingleElementType(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(POOL_KEY)
                        .contains("\"element_type\":\"minecraft:single_pool_element\""),
                "the pool must use single pool elements");
    }

    @Test
    void poolReferencesTheTemplate(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(POOL_KEY)
                        .contains("\"location\":\"mymod:structures/temple\""),
                "the pool must reference the NBT template");
    }

    @Test
    void poolFallsBackToEmpty(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(POOL_KEY).contains("\"fallback\":\"minecraft:empty\""),
                "the pool must fall back to empty");
    }

    @Test
    void setUsesRandomSpread(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(SET_KEY).contains("\"type\":\"minecraft:random_spread\""),
                "the set must spread structures randomly");
    }

    @Test
    void setCarriesSpacing(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(SET_KEY).contains("\"spacing\":32"),
                "the set must carry the configured spacing");
    }

    @Test
    void setCarriesSeparation(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(SET_KEY).contains("\"separation\":16"),
                "the set must carry the configured separation");
    }

    @Test
    void setReferencesTheStructure(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(SET_KEY).contains("\"structure\":\"mymod:ancient_temple\""),
                "the set must reference the structure id");
    }
}
