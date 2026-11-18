package com.luatweaker.structures;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class NbtStructureLoaderTest {

    @TempDir
    Path tempDir;

    private File dataRoot(String modId) throws Exception {
        Path root = tempDir.resolve(modId).resolve("data");
        Files.createDirectories(root);
        return root.toFile();
    }

    private void writeNbt(File dataRoot, String namespace, String path) throws Exception {
        Path file = dataRoot.toPath().resolve(namespace).resolve("structure").resolve(path + ".nbt");
        Files.createDirectories(file.getParent());
        Files.write(file, new byte[]{0x0A, 0x00, 0x01, 0x02});
    }

    @Test
    void findTemplateFile_LocatesNbtUnderDataRoot() throws Exception {
        File root = dataRoot("mymod");
        writeNbt(root, "mymod", "structures/temple");

        Optional<File> found = NbtStructureLoader.findTemplateFile(List.of(root), "mymod:structures/temple");

        assertTrue(found.isPresent());
        assertTrue(found.get().getPath().endsWith(".nbt"));
    }

    @Test
    void findTemplateFile_AcceptsNbtSuffix() throws Exception {
        File root = dataRoot("mymod");
        writeNbt(root, "mymod", "structures/temple");

        assertTrue(NbtStructureLoader.findTemplateFile(List.of(root), "mymod:structures/temple.nbt").isPresent());
    }

    @Test
    void findTemplateFile_EmptyWhenMissing() throws Exception {
        File root = dataRoot("mymod");

        assertTrue(NbtStructureLoader.findTemplateFile(List.of(root), "mymod:structures/ghost").isEmpty());
        assertTrue(NbtStructureLoader.findTemplateFile(List.of(), "mymod:structures/ghost").isEmpty());
    }

    @Test
    void findTemplateFile_RejectsTraversalAndBlank() throws Exception {
        File root = dataRoot("mymod");

        assertThrows(IllegalArgumentException.class,
                () -> NbtStructureLoader.findTemplateFile(List.of(root), ""));
        assertThrows(IllegalArgumentException.class,
                () -> NbtStructureLoader.findTemplateFile(List.of(root), "no-namespace"));
        assertThrows(IllegalArgumentException.class,
                () -> NbtStructureLoader.findTemplateFile(List.of(root), "mymod:../escape"));
    }

    @Test
    void templateLocation_StripsNbtSuffix() {
        assertEquals("mymod:structures/temple", NbtStructureLoader.templateLocation("mymod:structures/temple"));
        assertEquals("mymod:structures/temple", NbtStructureLoader.templateLocation("mymod:structures/temple.nbt"));
    }
}
