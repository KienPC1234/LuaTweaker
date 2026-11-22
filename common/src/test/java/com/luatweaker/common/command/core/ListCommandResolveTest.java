package com.luatweaker.common.command.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Path resolution must keep nested paths inside the root and refuse escapes. */
class ListCommandResolveTest {

    @Test
    void allowsNestedDirs(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        assertNotNull(ListCommand.resolveInsideRoot(root, "server"),
                "a direct child dir must resolve inside the root");
    }

    @Test
    void allowsNestedFiles(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        assertNotNull(ListCommand.resolveInsideRoot(root, "server/a.lua"),
                "a nested file path must resolve inside the root");
    }

    @Test
    void rejectsParent(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        assertNull(ListCommand.resolveInsideRoot(root, ".."),
                "the parent dir must never resolve inside the root");
    }

    @Test
    void rejectsEscapes(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        assertNull(ListCommand.resolveInsideRoot(root, "../secret"),
                "a sibling escape must never resolve inside the root");
    }

    @Test
    void rejectsAbsolutePaths(@TempDir File root) throws IOException {
        CommandFixtures.prepareServerDir(root);

        assertNull(ListCommand.resolveInsideRoot(root, root.getAbsolutePath()),
                "an absolute path must never resolve inside the root");
    }
}
