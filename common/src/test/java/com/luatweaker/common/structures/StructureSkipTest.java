package com.luatweaker.common.structures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registrations whose NBT template is absent must skip loudly without files. */
class StructureSkipTest {

    private static Map<String, String> mixedFiles(Path tempDir) throws IOException {
        StructureEmissionFixture.EmissionContext context =
                StructureEmissionFixture.prepare("mymod", tempDir);
        StructureEmissionFixture.writeNbt(context.dataRoot(), "mymod", "structures/kept");
        context.service().register("mymod:ghost",
                StructureEmissionFixture.config(context.engine(), "mymod:structures/ghost",
                        new String[] {"minecraft:plains"}, 32, 8));
        context.service().register("mymod:kept",
                StructureEmissionFixture.config(context.engine(), "mymod:structures/kept",
                        new String[] {"minecraft:plains"}, 32, 8));
        return StructureEmissionFixture.apply(context);
    }

    @Test
    void onlyResolvableRegistrationsEmit(@TempDir Path tempDir) throws IOException {
        assertEquals(4, mixedFiles(tempDir).size(),
                "only the resolvable registration may emit files");
    }

    @Test
    void keptStructureEmits(@TempDir Path tempDir) throws IOException {
        assertTrue(mixedFiles(tempDir).keySet().stream().allMatch(key -> key.contains("kept")),
                "every emitted file must belong to the resolvable registration");
    }

    @Test
    void ghostStructureEmitsNothing(@TempDir Path tempDir) throws IOException {
        assertTrue(mixedFiles(tempDir).keySet().stream().noneMatch(key -> key.contains("ghost")),
                "the registration without a template must emit no files");
    }
}
