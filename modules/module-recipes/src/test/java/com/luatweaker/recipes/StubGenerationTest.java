package com.luatweaker.recipes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.luatweaker.api.recipe.IRecipeManagerService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the EmmyLua stub export used by the Lua language server.
 */
class StubGenerationTest {

    private static final String STUB_FILE_NAME = "luatweaker-api.lua";

    private File generateStubs() throws IOException {
        File tempDir = new File("build/tmp/testStubs");
        File stubDir = new File(tempDir, "luamods/.luatweaker/stubs");
        File stubFile = new File(stubDir, STUB_FILE_NAME);
        if (stubFile.exists()) {
            stubFile.delete();
        }

        com.luatweaker.core.lsp.LtvmStubGenerator stubGen = new com.luatweaker.core.lsp.LtvmStubGenerator();
        stubGen.generateClassStub(IRecipeManagerService.class, "Recipes");

        com.luatweaker.core.lsp.LtvmStubExporter.exportToWorkspace(tempDir.toPath(), stubGen);
        return stubFile;
    }

    @Test
    void generatesStubFile() throws IOException {
        File stubFile = generateStubs();

        assertTrue(stubFile.exists(), "Stub file should be generated");
    }

    @Test
    void stubDeclaresMeta() throws IOException {
        File stubFile = generateStubs();

        String stubContent = Files.readString(stubFile.toPath());
        assertTrue(stubContent.contains("---@meta"), "stub must open with a meta annotation");
    }

    @Test
    void stubDeclaresMod() throws IOException {
        File stubFile = generateStubs();

        String stubContent = Files.readString(stubFile.toPath());
        assertTrue(stubContent.contains("---@class Mod"), "stub must declare the Mod class");
    }

    @Test
    void stubDeclaresRecipes() throws IOException {
        File stubFile = generateStubs();

        String stubContent = Files.readString(stubFile.toPath());
        assertTrue(stubContent.contains("---@class Recipes"), "stub must declare the Recipes class");
    }

    @Test
    void stubHasRemoveByOutput() throws IOException {
        File stubFile = generateStubs();

        String stubContent = Files.readString(stubFile.toPath());
        assertTrue(stubContent.contains("Recipes:removeByOutput"), "stub must document removeByOutput");
    }

    @Test
    void stubHasAddShaped() throws IOException {
        File stubFile = generateStubs();

        String stubContent = Files.readString(stubFile.toPath());
        assertTrue(stubContent.contains("Recipes:addShaped"), "stub must document addShaped");
    }

    @Test
    void stubDocumentsRemoveByOutput() throws IOException {
        File stubFile = generateStubs();

        String stubContent = Files.readString(stubFile.toPath());
        assertTrue(stubContent.contains("--- Removes a specific recipe by its registry ID."),
                "stub must carry the removeByOutput description");
    }

    @Test
    void stubDocumentsIdParam() throws IOException {
        File stubFile = generateStubs();

        String stubContent = Files.readString(stubFile.toPath());
        assertTrue(stubContent.contains("---@param id: string"), "stub must document the id parameter");
    }
}
