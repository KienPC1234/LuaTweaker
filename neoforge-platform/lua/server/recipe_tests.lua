-- ===========================================================================
--  LuaTweaker — Recipe Module Full Test Suite
--  File : neoforge-platform/lua/server/recipe_tests.lua
--  Runs on every server start / /lt reload
-- ===========================================================================

local recipes = Mod:GetService("Recipes")

log.info("=== LuaTweaker Recipe Test Suite START ===")

-- ---------------------------------------------------------------------------
-- SECTION 1 : RECIPE REMOVALS
-- ---------------------------------------------------------------------------

-- Remove all recipes that output a diamond sword
recipes:removeByOutput("minecraft:diamond_sword")
log.info("[Remove] By output: minecraft:diamond_sword")

-- Remove all recipes that use netherite scrap as an ingredient
recipes:removeByInput("minecraft:netherite_scrap")
log.info("[Remove] By input: minecraft:netherite_scrap")

-- Remove a specific recipe by its full registry ID
recipes:removeById("minecraft:cake")
log.info("[Remove] By ID: minecraft:cake")

-- Remove all recipes from a specific mod (uncomment to test — removes a LOT)
-- recipes:removeByMod("minecraft")

-- Remove all recipes that involve items tagged as #minecraft:logs
recipes:removeByTag("#minecraft:logs")
log.info("[Remove] By tag: #minecraft:logs")

-- ---------------------------------------------------------------------------
-- SECTION 2 : SHAPELESS CRAFTING
-- ---------------------------------------------------------------------------

-- 1 bread from 1 wheat + 1 sugar (no layout required)
-- Output as plain string -> count defaults to 1
recipes:addShapeless(
    "luatweaker:instant_bread",
    "minecraft:bread",
    { "minecraft:wheat", "minecraft:sugar" }
)
log.info("[Add] Shapeless: instant_bread")

-- 9x redstone from 1 redstone block (unpack)
-- Mix of string and ingredient() wrapper in ingredient list
recipes:addShapeless(
    "luatweaker:redstone_unpack",
    item("minecraft:redstone", 9),
    { ingredient("minecraft:redstone_block") }
)
log.info("[Add] Shapeless: redstone_unpack")

-- ---------------------------------------------------------------------------
-- SECTION 3 : SHAPED CRAFTING
-- ---------------------------------------------------------------------------

-- Custom sword pattern: keys as plain strings
recipes:addShaped(
    "luatweaker:cheap_iron_sword",
    item("minecraft:iron_sword", 1),
    { " I ", " I ", " S " },
    {
        I = "minecraft:iron_ingot",
        S = "minecraft:stick"
    }
)
log.info("[Add] Shaped: cheap_iron_sword")

-- 8 torches: keys as ingredient() wrappers
recipes:addShaped(
    "luatweaker:bulk_torch",
    item("minecraft:torch", 8),
    { "C", "S" },
    {
        C = ingredient("minecraft:coal"),
        S = ingredient("minecraft:stick")
    }
)
log.info("[Add] Shaped: bulk_torch")

-- ---------------------------------------------------------------------------
-- SECTION 4 : FURNACE & COOKING WORKSTATIONS
-- ---------------------------------------------------------------------------

-- Furnace: double iron yield from raw iron
recipes:addSmelting(
    "luatweaker:double_smelt_iron",
    item("minecraft:iron_ingot", 2),
    "minecraft:raw_iron",   -- input as plain string
    1.5,                    -- XP reward
    200                     -- cook time in ticks
)
log.info("[Add] Smelting: double_smelt_iron")

-- Blast furnace: double gold yield
recipes:addBlasting(
    "luatweaker:blast_double_gold",
    item("minecraft:gold_ingot", 2),
    ingredient("minecraft:raw_gold"),   -- input as ingredient()
    2.0,
    100
)
log.info("[Add] Blasting: blast_double_gold")

-- Smoker: cook beef
recipes:addSmoking(
    "luatweaker:quick_beef",
    item("minecraft:cooked_beef", 1),
    "minecraft:beef",
    0.35,
    100
)
log.info("[Add] Smoking: quick_beef")

-- Campfire: cook cod (600 ticks = standard campfire time)
recipes:addCampfire(
    "luatweaker:campfire_cod",
    item("minecraft:cooked_cod", 1),
    ingredient("minecraft:cod"),
    0.35,
    600
)
log.info("[Add] Campfire: campfire_cod")

-- ---------------------------------------------------------------------------
-- SECTION 5 : STONECUTTER
-- ---------------------------------------------------------------------------

-- Chiseled stone bricks from stone bricks
recipes:addStonecutting(
    "luatweaker:stonecut_chiseled",
    item("minecraft:chiseled_stone_bricks", 1),
    "minecraft:stone_bricks"
)
log.info("[Add] Stonecutting: stonecut_chiseled")

-- Unpack copper block → 9 copper ingots via stonecutter
recipes:addStonecutting(
    "luatweaker:stonecut_copper_unpack",
    item("minecraft:copper_ingot", 9),
    ingredient("minecraft:copper_block")
)
log.info("[Add] Stonecutting: stonecut_copper_unpack")

-- ---------------------------------------------------------------------------
-- SECTION 6 : SMITHING TABLE
-- ---------------------------------------------------------------------------

-- Upgrade diamond pickaxe to netherite
recipes:addSmithing(
    "luatweaker:netherite_pick_upgrade",
    item("minecraft:netherite_pickaxe", 1),
    ingredient("minecraft:netherite_upgrade_smithing_template"),  -- template slot
    ingredient("minecraft:diamond_pickaxe"),                       -- base item slot
    ingredient("minecraft:netherite_ingot")                        -- addition slot
)
log.info("[Add] Smithing: netherite_pick_upgrade")

-- ---------------------------------------------------------------------------
-- SECTION 7 : ANVIL COMBINATION
-- ---------------------------------------------------------------------------
-- Anvil recipes are matched at runtime via AnvilUpdateEvent.
-- Enchantments from the left item are automatically preserved
-- if the output is the same item type.

-- Diamond sword + diamond → diamond sword (re-outputs with components preserved)
recipes:addAnvil(
    "luatweaker:empower_sword",
    item("minecraft:diamond_sword", 1),
    "minecraft:diamond_sword",    -- left slot (plain string)
    "minecraft:diamond",          -- right slot (material)
    5                             -- XP levels cost
)
log.info("[Add] Anvil: empower_sword (5 XP)")

-- Golden sword + blaze powder → golden sword (fire enchant theme)
recipes:addAnvil(
    "luatweaker:fire_golden_sword",
    item("minecraft:golden_sword", 1),
    ingredient("minecraft:golden_sword"),
    ingredient("minecraft:blaze_powder"),
    8
)
log.info("[Add] Anvil: fire_golden_sword (8 XP)")

-- ---------------------------------------------------------------------------
-- SECTION 8 : BREWING STAND
-- ---------------------------------------------------------------------------
-- IMPORTANT: Brewing recipes register at game startup via RegisterBrewingRecipesEvent.
-- If you add a brewing recipe via /lt reload, it takes effect on the NEXT server start.
-- The addMix() call registers for ALL container types:
--   → Potion, Splash Potion, and Lingering Potion automatically.

-- Awkward + glistering melon slice → Healing
recipes:addBrewing(
    "luatweaker:healing_brew",
    "minecraft:healing",                    -- output potion ID
    "minecraft:awkward",                    -- input potion ID
    "minecraft:glistering_melon_slice"      -- catalyst item (top slot)
)
log.info("[Add] Brewing: healing_brew (next start)")

-- Healing + glistering melon slice → Strong Healing (upgrade chain)
recipes:addBrewing(
    "luatweaker:strong_healing_brew",
    "minecraft:strong_healing",
    "minecraft:healing",
    "minecraft:glistering_melon_slice"
)
log.info("[Add] Brewing: strong_healing_brew (next start)")

-- ---------------------------------------------------------------------------
-- SECTION 9 : VILLAGER TRADES
-- ---------------------------------------------------------------------------
-- Villager trades are applied via VillagerTradesEvent.
-- They persist across reloads within the same server session.

-- Cleric (level 3) sells 16 redstone for 5 emeralds
recipes:addTrade(
    "minecraft:cleric",         -- profession (full ResourceLocation)
    3,                          -- level / tier (1–5)
    item("minecraft:emerald", 5),       -- primary payment (buy1)
    nil,                                -- secondary payment (buy2, nil = none)
    item("minecraft:redstone", 16),     -- item the villager sells
    12,                                 -- max uses before restock
    10                                  -- XP the villager earns per trade
)
log.info("[Add] Trade: cleric lv3 redstone")

-- Farmer (level 1) buys 20 wheat for 1 emerald
recipes:addTrade(
    "minecraft:farmer",
    1,
    item("minecraft:wheat", 20),    -- buy1: player gives 20 wheat
    nil,
    item("minecraft:emerald", 1),   -- sell: player receives 1 emerald
    16,
    5
)
log.info("[Add] Trade: farmer lv1 buys wheat")

-- ---------------------------------------------------------------------------
-- SECTION 10 : GLOBAL INGREDIENT / OUTPUT REPLACEMENT
-- ---------------------------------------------------------------------------

-- All recipes using coal now also accept charcoal as equivalent
recipes:replaceInput("minecraft:coal", "minecraft:charcoal")
log.info("[Replace] Input: coal -> charcoal")

-- All recipes that yield dirt now yield cobblestone instead
recipes:replaceOutput("minecraft:dirt", "minecraft:cobblestone")
log.info("[Replace] Output: dirt -> cobblestone")

-- ---------------------------------------------------------------------------
-- DONE
-- ---------------------------------------------------------------------------

log.info("=== LuaTweaker Recipe Test Suite DONE ===")
