package com.luatweaker.common.structures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The structure and biome tag JSON must reference the template and biomes. */
class StructureJsonContentTest {

    private static final String STRUCTURE_KEY = "data/mymod/worldgen/structure/ancient_temple.json";
    private static final String TAG_KEY = "data/mymod/tags/worldgen/biome/has_structure/ancient_temple.json";
    private static final String DESERT_BIOME = "minecraft:desert";
    private static final String BADLANDS_BIOME = "minecraft:badlands";

    private static Map<String, String> templeFiles(Path tempDir) throws IOException {
        StructureEmissionFixture.EmissionContext context =
                StructureEmissionFixture.prepare("mymod", tempDir);
        StructureEmissionFixture.writeNbt(context.dataRoot(), "mymod", "structures/temple");
        context.service().register("mymod:ancient_temple",
                StructureEmissionFixture.config(context.engine(), "mymod:structures/temple",
                        new String[] {DESERT_BIOME, BADLANDS_BIOME}, 32, 16));
        return StructureEmissionFixture.apply(context);
    }

    @Test
    void structureUsesJigsawType(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(STRUCTURE_KEY).contains("\"type\":\"minecraft:jigsaw\""),
                "the structure must use the jigsaw type");
    }

    @Test
    void structureReferencesItself(@TempDir Path tempDir) throws IOException {
        String structure = templeFiles(tempDir).get(STRUCTURE_KEY);

        assertTrue(structure.contains("mymod:ancient_temple")
                        || structure.contains("has_structure/ancient_temple"),
                "the structure must reference its own id or biome tag");
    }

    @Test
    void structureUsesItsBiomeTag(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(STRUCTURE_KEY)
                        .contains("\"biomes\":\"#mymod:has_structure/ancient_temple\""),
                "the structure must spawn only in its own biome tag");
    }

    @Test
    void structureUsesItsStartPool(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(STRUCTURE_KEY)
                        .contains("\"start_pool\":\"mymod:ancient_temple\""),
                "the structure must start from its own template pool");
    }

    @Test
    void tagListsDesert(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(TAG_KEY).contains(DESERT_BIOME),
                "the biome tag must include the desert spawn biome");
    }

    @Test
    void tagListsBadlands(@TempDir Path tempDir) throws IOException {
        assertTrue(templeFiles(tempDir).get(TAG_KEY).contains(BADLANDS_BIOME),
                "the biome tag must include the badlands spawn biome");
    }
}
