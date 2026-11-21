package com.luatweaker.structures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbtStructureLoaderValidationTest {

    private static final String TEMPLE_ID = "mymod:structures/temple";

    private List<File> roots(Path tempDir) {
        return List.of(tempDir.toFile());
    }

    @Test
    void rejectsBlankTemplateId(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class,
            () -> NbtStructureLoader.findTemplateFile(roots(tempDir), ""),
            "a blank template id must be rejected");
    }

    @Test
    void rejectsTemplateIdWithoutNamespace(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class,
            () -> NbtStructureLoader.findTemplateFile(roots(tempDir), "no-namespace"),
            "a template id without a namespace must be rejected");
    }

    @Test
    void rejectsPathTraversal(@TempDir Path tempDir) {
        assertThrows(IllegalArgumentException.class,
            () -> NbtStructureLoader.findTemplateFile(roots(tempDir), "mymod:../escape"),
            "a template id with path traversal must be rejected");
    }

    @Test
    void templateLocationStripsNbtSuffix() {
        assertEquals(TEMPLE_ID, NbtStructureLoader.templateLocation(TEMPLE_ID + ".nbt"),
            "templateLocation must strip the trailing .nbt suffix");
    }

    @Test
    void templateLocationKeepsPlainId() {
        assertEquals(TEMPLE_ID, NbtStructureLoader.templateLocation(TEMPLE_ID),
            "templateLocation must leave an already-normalized id unchanged");
    }
}
