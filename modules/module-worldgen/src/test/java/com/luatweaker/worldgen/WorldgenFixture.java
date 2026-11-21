package com.luatweaker.worldgen;

/**
 * Shared ids, JSON paths and Lua-table specs for the worldgen tests.
 */
final class WorldgenFixture {

    static final String PLAINS = "minecraft:plains";
    static final String RUBY_ORE = "mymod:ruby_ore";
    static final String RUBY_VEIN = "mymod:ruby_vein";
    static final String OVERWORLD = "minecraft:overworld";

    private static final String BLOCK_KEY = "block";

    static final String CONFIGURED_DIR = "luatweaker/worldgen/configured_feature/";
    static final String PLACED_DIR = "luatweaker/worldgen/placed_feature/";
    static final String MODIFIER_DIR = "luatweaker/neoforge/biome_modifier/";

    private WorldgenFixture() {
    }

    /** Minimal valid addFeature spec: a ruby ore vein in the plains. */
    static FakeLuaTable featureSpec() {
        return new FakeLuaTable()
            .put(BLOCK_KEY, RUBY_ORE)
            .put("biomes", FakeLuaTables.listOf(PLAINS));
    }

    /** GTNH-style mixed vein spec with three weighted ores. */
    static FakeLuaTable magnetiteVeinSpec() {
        FakeLuaTable magnetite = new FakeLuaTable().put(BLOCK_KEY, "mymod:magnetite_ore").put("weight", 70);
        FakeLuaTable vanadium = new FakeLuaTable()
            .put(BLOCK_KEY, "mymod:vanadium_magnetite_ore").put("weight", 20);
        FakeLuaTable gold = new FakeLuaTable().put(BLOCK_KEY, "mymod:gold_ore").put("weight", 10);
        return new FakeLuaTable()
            .put("ores", FakeLuaTables.listOf(magnetite, vanadium, gold))
            .put("biomes", FakeLuaTables.listOf(PLAINS))
            .put("size", 32)
            .put("count", 4);
    }

    /** modifyBiome options that remove one vanilla feature from the plains. */
    static FakeLuaTable removeOreDirtOptions() {
        return new FakeLuaTable()
            .put("biomes", PLAINS)
            .put("remove", "minecraft:ore_dirt");
    }

    /** Returns the message of the {@link IllegalArgumentException} the action must throw. */
    static String errorMessage(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException error) {
            return String.valueOf(error.getMessage());
        }
        throw new AssertionError("expected an IllegalArgumentException but the action completed normally");
    }

    /** True when the action throws {@link IllegalArgumentException}. */
    static boolean rejects(Runnable action) {
        try {
            action.run();
            return false;
        } catch (IllegalArgumentException ignored) {
            return true;
        }
    }
}
