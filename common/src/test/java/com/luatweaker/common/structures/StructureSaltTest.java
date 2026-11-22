package com.luatweaker.common.structures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Placement salt must be deterministic across identical registrations. */
class StructureSaltTest {

    private static final String SET_KEY = "data/mymod/worldgen/structure_set/ancient_temple.json";

    private static String[] twoRuns(Path tempDir) throws IOException {
        StructureEmissionFixture.EmissionContext context =
                StructureEmissionFixture.prepare("mymod", tempDir);
        StructureEmissionFixture.writeNbt(context.dataRoot(), "mymod", "structures/temple");
        context.service().register("mymod:ancient_temple",
                StructureEmissionFixture.config(context.engine(), "mymod:structures/temple",
                        new String[] {"minecraft:desert"}, 48, 12));

        Map<String, String> first = StructureEmissionFixture.apply(context);
        context.datapack().clear();
        Map<String, String> second = StructureEmissionFixture.apply(context);
        return new String[] {first.get(SET_KEY), second.get(SET_KEY)};
    }

    @Test
    void setJsonIsIdenticalAcrossRuns(@TempDir Path tempDir) throws IOException {
        String[] runs = twoRuns(tempDir);

        assertEquals(runs[0], runs[1],
                "identical registrations must emit identical structure sets");
    }

    @Test
    void setCarriesSpacing(@TempDir Path tempDir) throws IOException {
        assertTrue(twoRuns(tempDir)[1].contains("\"spacing\":48"),
                "the set must carry the configured spacing");
    }

    @Test
    void setCarriesSeparation(@TempDir Path tempDir) throws IOException {
        assertTrue(twoRuns(tempDir)[1].contains("\"separation\":12"),
                "the set must carry the configured separation");
    }
}
