package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the {@code net.http} permission gate: anonymous calls and installs without a granting mod
 * are denied by name, a grant is per installation (not per runtime {@code mod} global), and a
 * spoofed global cannot unlock HTTP.
 */
class WebPermissionTest extends WebServiceFixture {

    private static final String UNREACHABLE_URL = "http://127.0.0.1:1/x";
    private static final String SUCCESS = "Success";

    @Test
    void anonymousCallIsDenied() {
        ILuaValue result = webService.HttpGet(UNREACHABLE_URL, 1);
        assertEquals(false, result.asTable().rawget(SUCCESS).asBoolean(),
                "an HttpGet call without a mod context must be denied");
    }

    @Test
    void denialNamesTheMissingModContext() {
        ILuaValue result = webService.HttpGet(UNREACHABLE_URL, 1);
        assertTrue(result.asTable().rawget("Error").asString().contains("owning mod context"),
                "the denial must explain that no owning mod context was found");
    }

    @Test
    void callWithoutPermissionIsDenied() throws IOException {
        loadMod(SAFE_MOD);
        ILuaValue result = webService.HttpGet(UNREACHABLE_URL, 1);
        assertEquals(false, result.asTable().rawget(SUCCESS).asBoolean(),
                "an install whose mods never declared net.http must be denied");
    }

    @Test
    void denialNamesTheMissingPermission() throws IOException {
        loadMod(SAFE_MOD);
        ILuaValue result = webService.HttpGet(UNREACHABLE_URL, 1);
        assertTrue(result.asTable().rawget("Error").asString().contains(NET_HTTP),
                "the denial must name the missing net.http permission");
    }

    @Test
    void grantedInstallCanFetch() throws IOException {
        startServer(FEED_PATH, 200, "hello world");
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet(baseUrl() + FEED_PATH, 5).asTable();
        assertEquals(List.of("true", "200.0", "hello world"), responseFields(table),
                "a granting install must return Success/StatusCode/Body from the real server");
    }

    @Test
    void grantIsPerInstallationNotPerModGlobal() throws IOException {
        startServer(FEED_PATH, 200, "ok");
        loadMod(SAFE_MOD);
        loadMod(WEB_MOD, NET_HTTP);
        ILuaTable table = webService.HttpGet(baseUrl() + FEED_PATH, 5).asTable();
        assertEquals(true, table.rawget(SUCCESS).asBoolean(),
                "the grant must hold even when the runtime mod global points at a non-granting mod");
    }

    @Test
    void spoofedModGlobalCannotGrantPermission() throws IOException {
        loadMod(SAFE_MOD);
        engine.getGlobalEnvironment().rawget("mod").asTable().rawset("ID", WEB_MOD);
        ILuaTable table = webService.HttpGet(UNREACHABLE_URL, 1).asTable();
        assertTrue(!table.rawget(SUCCESS).asBoolean()
                        && table.rawget("Error").asString().contains(NET_HTTP),
                "writing a granting id into the mod global must not unlock HTTP");
    }
}
