package com.luatweaker.common.command.core;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Shared lua-folder fixtures for the path-containment tests. */
final class CommandFixtures {

    static final String SERVER_DIR = "server";

    private CommandFixtures() {
    }

    static File prepareServerDir(File root) throws IOException {
        File server = new File(root, SERVER_DIR);
        if (!server.mkdirs() && !server.isDirectory()) {
            throw new IOException("cannot create fixture dir " + server);
        }
        Files.writeString(new File(server, "ok.lua").toPath(), "local x = 1\nreturn x\n");
        Files.writeString(new File(server, "a.lua").toPath(), "return 1");
        return server;
    }
}
