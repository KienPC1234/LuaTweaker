package com.luatweaker.content;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Datapack read/remove/list bindings work end to end through Lua. */
class DatapackBindingAdvancedTest extends ContentFixture {

    private static final String CHUNK = "datapack_advanced_chunk";
    private static final String SCRIPT = """
            datapack:addData("mymod/recipe/iron.json", '{"type":"minecraft:crafting_shapeless"}')
            datapack:addData("mymod/recipe/gold.json", '{"type":"minecraft:crafting_shapeless"}')
            assert(datapack:hasData("mymod/recipe/iron.json") == true, 'hasData must find the file')
            assert(datapack:HasData("data/mymod/recipe/iron.json") == true, 'HasData must accept the data/ prefix')
            assert(datapack:getData("mymod/recipe/iron.json") ~= nil, 'getData must return content')
            assert(datapack:GetData("mymod/recipe/missing.json") == nil, 'getData must return nil when missing')
            local listed = datapack:listData("data/mymod/recipe/")
            assert(#listed == 2, 'listData must find both recipes')
            local namespaces = datapack:listNamespaces("data")
            assert(#namespaces == 1 and namespaces[1] == "mymod", 'listNamespaces must report mymod')
            assert(datapack:removeData("mymod/recipe/gold.json") == true, 'removeData must delete')
            assert(datapack:HasData("mymod/recipe/gold.json") == false, 'removed file must be gone')
            assert(datapack:removeData("mymod/recipe/gold.json") == false, 'second remove must return false')
            assert(datapack:hasLoaded("minecraft/recipe/anything.json") == false, 'loaded reads must be false without a server')
            assert(datapack:getLoaded("minecraft/recipe/anything.json") == nil, 'loaded reads must be nil without a server')
            assert(#datapack:listLoaded("data/minecraft/recipe/") == 0, 'loaded lists must be empty without a server')
            assert(#datapack:loadedNamespaces("data") == 0, 'loaded namespaces must be empty without a server')
        """;

    @Test
    void datapackReadsWorkThroughLua() throws IOException {
        engine.executeScript(scriptFile(SCRIPT), CHUNK);

        assertTrue(datapackService.hasData("data/mymod/recipe/iron.json"),
                "the surviving recipe must remain in the virtual pack");
    }
}
