package com.luatweaker.content;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/** Low-level datapack primitives: read, remove, list, and key canonicalization. */
class DatapackAdvancedTest {

    private static final String RECIPE_KEY = "data/mymod/recipe/iron.json";
    private static final String IRON_REL = "mymod/recipe/iron.json";
    private static final String RECIPE_JSON = "{\"type\":\"minecraft:crafting_shapeless\"}";
    private static final String GOLD_REL = "mymod/recipe/gold.json";
    private static final String RECIPE_DIR = "data/mymod/recipe/";
    private static final String RECIPE_A_REL = "mymod/recipe/a.json";
    private static final String LOOT_KEY = "data/mymod/loot_table/blocks/ruby_ore.json";
    private static final String ASSET_KEY = "assets/mymod/models/item/ruby.json";
    private static final String TAG_KEY = "data/mymod/tags/item/gems.json";
    private static final String EMPTY_JSON = "{}";

    /** One behavior case: a pre-arranged pack, a query, and the expected result. */
    private record Case(String label, DatapackServiceImpl pack,
            Function<DatapackServiceImpl, Object> query, Object expected) {
    }

    private static Case freshCase(String label, Consumer<DatapackServiceImpl> arrange,
            Function<DatapackServiceImpl, Object> query, Object expected) {
        DatapackServiceImpl pack = new DatapackServiceImpl();
        arrange.accept(pack);
        return new Case(label, pack, query, expected);
    }

    @Test
    void datapackPrimitivesBehave() {
        List<Case> cases = List.of(
                freshCase("missing file reports false", pack -> {
                }, pack -> pack.hasData(RECIPE_KEY), false),
                freshCase("stored file is found", pack -> pack.addData(IRON_REL, RECIPE_JSON),
                        pack -> pack.hasData(RECIPE_KEY), true),
                freshCase("data/ prefix matches", pack -> pack.addData(IRON_REL, RECIPE_JSON),
                        pack -> pack.hasData("data/mymod/recipe/iron.json"), true),
                freshCase("removed file is gone", pack -> {
                    pack.addData(IRON_REL, RECIPE_JSON);
                    pack.removeData(RECIPE_KEY);
                }, pack -> pack.hasData(RECIPE_KEY), false),
                freshCase("content round-trips", pack -> pack.addData(IRON_REL, RECIPE_JSON),
                        pack -> pack.getData(RECIPE_KEY), RECIPE_JSON),
                freshCase("missing file reads as null", pack -> {
                }, pack -> pack.getData(RECIPE_KEY), null),
                freshCase("remove returns true when present",
                        pack -> pack.addData(GOLD_REL, RECIPE_JSON),
                        pack -> pack.removeData(GOLD_REL), true),
                freshCase("remove returns false when missing", pack -> {
                }, pack -> pack.removeData(RECIPE_KEY), false),
                freshCase("prefix listing is sorted", pack -> {
                    pack.addData("mymod/recipe/b.json", EMPTY_JSON);
                    pack.addData(RECIPE_A_REL, EMPTY_JSON);
                    pack.addData(LOOT_KEY, EMPTY_JSON);
                }, pack -> pack.listData(RECIPE_DIR),
                        List.of("data/mymod/recipe/a.json", "data/mymod/recipe/b.json")),
                freshCase("empty prefix lists all", pack -> {
                    pack.addData(RECIPE_A_REL, EMPTY_JSON);
                    pack.addData(LOOT_KEY, EMPTY_JSON);
                    pack.addData(ASSET_KEY, EMPTY_JSON);
                }, pack -> pack.listData("").size(), 3),
                freshCase("unknown prefix lists nothing", pack -> pack.addData(RECIPE_A_REL, EMPTY_JSON),
                        pack -> pack.listData("data/other/"), List.of()),
                freshCase("data namespaces are sorted", pack -> {
                    pack.addData(RECIPE_A_REL, EMPTY_JSON);
                    pack.addData("minecraft/tags/block/mineable/pickaxe.json", EMPTY_JSON);
                }, pack -> pack.listNamespaces("data"), List.of("minecraft", "mymod")),
                freshCase("assets namespaces stay separate", pack -> pack.addData(ASSET_KEY, EMPTY_JSON),
                        pack -> pack.listNamespaces("assets"), List.of("mymod")),
                freshCase("unknown pack dir lists nothing", pack -> {
                }, pack -> pack.listNamespaces("other"), List.of()),
                freshCase("tag file is visible", pack -> pack.addTag("item", "mymod:gems", List.of("mymod:ruby")),
                        pack -> pack.hasData(TAG_KEY), true),
                freshCase("tag file holds the value",
                        pack -> pack.addTag("item", "mymod:gems", List.of("mymod:ruby")),
                        pack -> pack.getData(TAG_KEY).contains("mymod:ruby"), true));

        for (Case current : cases) {
            assertEquals(current.expected(), current.query().apply(current.pack()), current.label());
        }
    }

    @Test
    void invalidKeysFailLoudly() {
        for (Runnable invalid : List.<Runnable>of(() -> new DatapackServiceImpl().addData("", EMPTY_JSON),
                () -> new DatapackServiceImpl().addData("../evil.json", EMPTY_JSON),
                () -> new DatapackServiceImpl().hasData(""))) {
            assertThrows(IllegalArgumentException.class, invalid::run, "invalid keys must be rejected");
        }
    }
}
