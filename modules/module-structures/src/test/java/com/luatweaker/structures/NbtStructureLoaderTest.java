package com.luatweaker.structures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class NbtStructureLoaderTest {

    private static final String MOD_ID = "mymod";
    private static final String TEMPLE_PATH = "structures/temple";
    private static final String TEMPLE_ID = "mymod:structures/temple";
    private static final String MISSING_ID = "mymod:structures/ghost";

    private File dataRoot(Path tempDir) throws IOException {
        Path root = tempDir.resolve(MOD_ID).resolve("data");
        Files.createDirectories(root);
        return root.toFile();
    }

    private void writeTemple(File dataRoot) throws IOException {
        Path file = dataRoot.toPath().resolve(MOD_ID).resolve("structure").resolve(TEMPLE_PATH + ".nbt");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{0x0A, 0x00, 0x01, 0x02});
    }

    private Optional<File> findTemple(Path tempDir) throws IOException {
        File root = dataRoot(tempDir);
        writeTemple(root);
        return NbtStructureLoader.findTemplateFile(List.of(root), TEMPLE_ID);
    }

    @Test
    void findsTemplateUnderDataRoot(@TempDir Path tempDir) throws IOException {
        assertTrue(findTemple(tempDir).isPresent(),
            "an existing template must be found under <root>/<namespace>/structure");
    }

    @Test
    void resolvesNbtFileExtension(@TempDir Path tempDir) throws IOException {
        String located = findTemple(tempDir).orElseThrow().getPath();
        assertTrue(located.endsWith(".nbt"), "the located template must be the .nbt file, got: " + located);
    }

    @Test
    void acceptsExplicitNbtSuffix(@TempDir Path tempDir) throws IOException {
        File root = dataRoot(tempDir);
        writeTemple(root);

        Optional<File> found = NbtStructureLoader.findTemplateFile(List.of(root), TEMPLE_ID + ".nbt");

        assertTrue(found.isPresent(), "a template id with an explicit .nbt suffix must resolve");
    }

    @Test
    void returnsEmptyForMissingTemplate(@TempDir Path tempDir) throws IOException {
        File root = dataRoot(tempDir);

        Optional<File> found = NbtStructureLoader.findTemplateFile(List.of(root), MISSING_ID);

        assertTrue(found.isEmpty(), "a template that no data root provides must yield empty");
    }

    @Test
    void returnsEmptyWhenNoDataRoots() {
        Optional<File> found = NbtStructureLoader.findTemplateFile(List.of(), MISSING_ID);

        assertTrue(found.isEmpty(), "an empty data root list must yield empty instead of failing");
    }
}
