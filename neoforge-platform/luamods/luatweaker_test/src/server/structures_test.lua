-- ===================================================================
-- LuaTweaker Test Suite: Structures Service (MVP)
-- Verifies OBSERVABLE registry changes (via GetRegistrations):
-- additions increment the count, removals drop it, invalid configs
-- raise loud errors (never silent no-ops). No server runtime needed.
-- ===================================================================
local Structures = require("LuaTweaker.Structures")

local function check(label, cond)
    if cond then
        print("[Test][Structures] " .. label .. " OK")
    else
        print("[ERROR][Test] " .. label .. " FAILED")
    end
end

local function count()
    local summary = Structures:GetRegistrations()
    if type(summary) ~= "table" then
        return -1
    end
    return summary.structures or -1
end

-- Establish a clean baseline before asserting absolute counts.
Structures:ClearAll()
check("ClearAll baseline (structures=0)", count() == 0)

-- ==== REGISTER: full config ====
Structures:Register("luatweaker_test:ancient_temple", {
    template = "luatweaker_test:structures/temple",
    spawnBiomes = { "minecraft:desert", "minecraft:badlands" },
    spacing = 32,
    separation = 16,
    spawnChance = 0.8,
    lootTables = { chest = "minecraft:chests/simple_dungeon" },
})
check("Register increments structures", count() == 1)

-- ==== REGISTER: minimal config (vanilla desert_pyramid defaults) ====
Structures:Register("luatweaker_test:ruin", {
    template = "luatweaker_test:structures/ruin",
    spawnBiomes = { "minecraft:plains" },
})
check("Minimal register increments structures", count() == 2)

-- ==== UNREGISTER ====
local removed = Structures:Unregister("luatweaker_test:ancient_temple")
check("Unregister returns true", removed == true)
check("Unregister drops count", count() == 1)
check("Unregister of unknown returns false", Structures:Unregister("luatweaker_test:ghost") == false)

-- ==== ERROR PATHS (must raise, never silently accept) ====
local okBlank = pcall(function()
    Structures:Register("", { template = "m:structures/x", spawnBiomes = { "minecraft:plains" } })
end)
check("Blank id rejected", not okBlank)

local okSpacing = pcall(function()
    Structures:Register("luatweaker_test:bad", {
        template = "luatweaker_test:structures/x",
        spawnBiomes = { "minecraft:plains" },
        spacing = 8,
        separation = 16,
    })
end)
check("spacing < separation rejected", not okSpacing)

local okChance = pcall(function()
    Structures:Register("luatweaker_test:bad_chance", {
        template = "luatweaker_test:structures/x",
        spawnBiomes = { "minecraft:plains" },
        spawnChance = 1.5,
    })
end)
check("Out-of-range spawnChance rejected", not okChance)
check("Failed registers leave count unchanged", count() == 1)

-- ==== FINAL CLEAR ====
Structures:ClearAll()
check("ClearAll zeroes the count", count() == 0)

print("[Test][Structures] Structures service verified")
