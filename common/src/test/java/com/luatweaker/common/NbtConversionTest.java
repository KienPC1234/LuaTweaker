package com.luatweaker.common;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.content.ContentServiceImpl;
import com.luatweaker.content.DatapackServiceImpl;
import com.luatweaker.content.StorageServiceImpl;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.common.bootstrap.LuaServiceBootstrap;
import com.luatweaker.common.recipe.CommonRecipeManager;
import com.luatweaker.common.support.FakePlatform;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Block-state/NBT bindings must return nil/false gracefully without a server,
 * while Lua array-like tables (Items lists) must arrive as Java Lists, not
 * Maps - otherwise NbtCodec would write a CompoundTag instead of a ListTag and
 * ContainerHelper.loadAllItems would fail, wiping the crate contents.
 */
class NbtConversionTest {

    private static final String BINDINGS_SCRIPT =
            "local World = require('LuaTweaker.World')\n"
            + "assert(World:GetBlockState(0, 0, 0) == nil, 'GetBlockState should be nil without server')\n"
            + "assert(World:GetBlockEntityData(0, 0, 0) == nil, 'GetBlockEntityData should be nil without server')\n"
            + "assert(World:SetBlockState(0, 0, 0, 'minecraft:stone') == false, 'SetBlockState should fail without server')\n"
            + "assert(World:SetBlockEntityData(0, 0, 0, {Items = {{id='minecraft:stick', count=1, slot=0}}}) == true, 'SetBlockEntityData should accept valid data')\n"
            + "assert(World:EjectContainerItem(0, 0, 0, 0, 1) == false, 'EjectContainerItem should fail without server')\n"
            + "assert(World:ExecuteCommand('say hi') == false, 'ExecuteCommand should fail without server')\n";

    private static Map<String, Object> lastWrittenNbt;

    private static List<?> convertedItems() {
        File baseDir = AutonomousModsFixture.getBaseDir();
        ILuaEngine engine = LuaEngineFactory.create(true);
        engine.setLuaDirectory(baseDir);
        ContentServiceImpl contentService = new ContentServiceImpl();
        StorageServiceImpl storageService = new StorageServiceImpl(new File(baseDir, "storage.json"));
        DatapackServiceImpl datapackService = new DatapackServiceImpl();
        CommonRecipeManager recipeManager = new CommonRecipeManager();
        LuaServiceRegistry.clear();
        FakePlatform.installPermissive("IntegrationTest", data -> lastWrittenNbt = data);
        LuaServiceBootstrap.registerAllServices(engine, contentService, storageService,
                datapackService, recipeManager);

        // No server is running in tests: block-state/NBT calls must return
        // nil/false gracefully and never throw.
        engine.executeStringOrThrow(BINDINGS_SCRIPT, "testBlockStateAndNbtBindings");
        return (List<?>) lastWrittenNbt.get("Items");
    }

    @Test
    void itemsConvertToAJavaList() {
        assertTrue(convertedItems() instanceof List,
                "Lua array table must convert to a Java List for NBT lists");
    }

    @Test
    void itemsListHasOneEntry() {
        assertEquals(1, convertedItems().size(),
                "the written crate must contain exactly one item entry");
    }

    @Test
    void firstEntryIsAMap() {
        assertTrue(convertedItems().get(0) instanceof Map,
                "nested item entry should be a Map");
    }

    @Test
    void firstEntryKeepsTheItemId() {
        assertEquals("minecraft:stick",
                ((Map<?, ?>) convertedItems().get(0)).get("id"),
                "the nested item entry must keep its id for NBT serialization");
    }
}
