package com.luatweaker.core.lsp;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.entity.IPlayer;
import com.luatweaker.api.mod.IModExports;
import com.luatweaker.api.mod.IModFiles;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the stub generator emits typed classes with interface inheritance
 * for the dynamic entity/player Lua tables (Player: Entity), so IDE autocomplete
 * works for inherited methods such as player:getHealth().
 */
class LtvmStubGeneratorTest {

    private static final String ENTITY = "Entity";
    private static final String PLAYER = "Player";
    private static final String MOD_FILES = "ModFiles";
    private static final String MOD_EXPORTS = "ModExports";

    private static String entityStubs() {
        LtvmStubGenerator generator = new LtvmStubGenerator();
        generator.registerClassStub(IEntity.class, ENTITY);
        generator.registerClassStub(IPlayer.class, PLAYER);
        return generator.getResult();
    }

    private static String modFileStubs() {
        LtvmStubGenerator generator = new LtvmStubGenerator();
        generator.registerClassStub(IModFiles.class, MOD_FILES);
        generator.registerClassStub(IModExports.class, MOD_EXPORTS);
        return generator.getResult();
    }

    @Test
    void playerClassInheritsEntityAndListsMethodsClassPlayerEntity() {
        String stubs = entityStubs();
        assertTrue(stubs.contains("---@class Player: Entity"),
                "Player stub must inherit Entity:\n" + stubs);
    }

    @Test
    void playerClassInheritsEntityAndListsMethodsClassEntity() {
        String stubs = entityStubs();
        assertTrue(stubs.contains("---@class Entity"),
                "Entity stub must exist:\n" + stubs);
    }

    @Test
    void playerClassInheritsEntityAndListsMethodsFunctionPlayerSendMessageMessageEnd() {
        String stubs = entityStubs();
        assertTrue(stubs.contains("function Player:sendMessage(message) end"),
                "Player stub must include sendMessage:\n" + stubs);
    }

    @Test
    void playerClassInheritsEntityAndListsMethodsFunctionEntityGetHealthEnd() {
        String stubs = entityStubs();
        assertTrue(stubs.contains("function Entity:getHealth() end"),
                "Entity stub must include getHealth so Player inherits it:\n" + stubs);
    }

    @Test
    void modFileHandlesAreStubbedClassModFiles() {
        String stubs = modFileStubs();
        assertTrue(stubs.contains("---@class ModFiles"),
                "the mod file handle must be emitted as a class stub:\n" + stubs);
    }

    @Test
    void modFileHandlesAreStubbedFunctionModFilesReadTextPathEnd() {
        String stubs = modFileStubs();
        assertTrue(stubs.contains("function ModFiles:readText(path) end"),
                "the ModFiles stub must list readText(path):\n" + stubs);
    }

    @Test
    void modFileHandlesAreStubbedFunctionModExportsWriteTextPathTextEnd() {
        String stubs = modFileStubs();
        assertTrue(stubs.contains("function ModExports:writeText(path, text) end"),
                "the ModExports stub must list writeText(path, text):\n" + stubs);
    }

    @Test
    void modFileHandlesAreStubbedFunctionModExportsDeletePathEnd() {
        String stubs = modFileStubs();
        assertTrue(stubs.contains("function ModExports:delete(path) end"),
                "the ModExports stub must list delete(path):\n" + stubs);
    }
}
