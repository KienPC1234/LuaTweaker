package com.luatweaker.content;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared arrange logic for the content builder tests: fresh services per
 * test, Lua script temp files, and strict/lenient entity/block/item lookup.
 */
class ContentFixture {

    static final String TEST_CHUNK = "TEST";

    static final String MACHINE_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local machine = Content.NewBlock("test_machine")
                :Container(3, 3, "packed")
                :EnergyStorage(10000, 500, 250)
                :FluidStorage(8000)
                :GuiBar("energy_bar", 8, 60, 90, 10, "energy", 0xFF00E676)
                :GuiBar("tank", 150, 40, 8, 40, "fluid", 0xFF2196F3)
                :BooleanState("running", "luatweaker:block/test_machine", "luatweaker:block/test_machine_running")
                :Register()
            local pipe = Content.NewBlock("test_pipe")
                :Texture("luatweaker:block/test_pipe")
                :Container(1, 1, "none")
                :ConnectionState(true)
                :EnergyStorage(2000, 200, 200)
                :FluidStorage(4000)
                :OnTick(function(data)
                    print("tick at " .. data.X .. "," .. data.Y .. "," .. data.Z)
                end)
                :Register()
            local machine = Content.NewBlock("tick_machine")
                :Container(2, 2, "packed")
                :EnergyStorage(1000, 100, 100)
                :OnTick(function(data)
                    print("machine energy " .. data.Energy)
                end)
                :Register()
        """;

    ILuaEngine engine;
    ContentServiceImpl contentService;
    StorageServiceImpl storageService;
    DatapackServiceImpl datapackService;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        contentService = new ContentServiceImpl();
        storageService = new StorageServiceImpl(new File("build/tmp/test_storage.json"));
        datapackService = new DatapackServiceImpl();

        ContentLuaBinding.registerBindings(engine, contentService, storageService, datapackService);
    }

    File scriptFile(String content) throws IOException {
        File file = File.createTempFile("test_script_", ".lua");
        file.deleteOnExit();
        Files.writeString(file.toPath(), content);
        return file;
    }

    void runMachineScript() throws IOException {
        engine.executeScript(scriptFile(MACHINE_SCRIPT), TEST_CHUNK);
    }

    static final String PROPS_CHUNK = "props_chunk";
    static final String PROPS_SCRIPT = """
            local Content = require("LuaTweaker.Content")
            local b = Content.NewBlock("prop_block")
                :MapColor("RED")
                :JumpFactor(1.5)
                :SpeedFactor(0.5)
                :NoCollision(true)
                :NoOcclusion(true)
                :PushReaction("BLOCK")
                :Replaceable(true)
                :IgnitedByLava(true)
                :OffsetType("XZ")
                :RedstoneConductor(false)
                :Register()
        """;

    IBlockBuilder propBlock() throws IOException {
        engine.executeScript(scriptFile(PROPS_SCRIPT), PROPS_CHUNK);
        return block("prop_block");
    }

    static final String ENTITY_CHUNK = "entity_chunk";
    static final String ENTITY_SCRIPT = """
            startup:createEntity("custom_zombie", function(entity)
                entity:category("MONSTER")
                      :dimensions(0.8, 2.0)
                      :maxHealth(50.0)
                      :movementSpeed(0.3)
                      :attackDamage(8.0)
                      :spawnEgg(0x00FF00, 0x0000FF)
            end)
        """;

    IEntityBuilder customZombie() throws IOException {
        engine.executeScript(scriptFile(ENTITY_SCRIPT), ENTITY_CHUNK);
        return entity("custom_zombie");
    }

    static final String FURNACE_CHUNK = "furnace_chunk";
    static final String FURNACE_SCRIPT = """
            startup:createBlock("advanced_furnace", function(block)
                block:container(3, 3)
                     :button("power_btn", 10, 20, 30, 15, "Power")
                     :label(5, 10, "Heat: {Temp}", 0xFF0000)
                     :onButtonClick(function(pos, btnId) end)
                     :onNeighborChanged(function(pos, nPos) end)
            end)
        """;

    IBlockBuilder furnace() throws IOException {
        engine.executeScript(scriptFile(FURNACE_SCRIPT), FURNACE_CHUNK);
        return block("advanced_furnace");
    }

    IBlockBuilder block(String id) {
        return contentService.getRegisteredBlocks().stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("block must be registered: " + id));
    }

    IItemBuilder item(String id) {
        return contentService.getRegisteredItems().stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("item must be registered: " + id));
    }

    IEntityBuilder entity(String id) {
        return contentService.getRegisteredEntities().stream()
                .filter(candidate -> id.equals(candidate.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("entity must be registered: " + id));
    }
}
