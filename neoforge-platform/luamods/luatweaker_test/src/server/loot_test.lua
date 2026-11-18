-- ===================================================================
-- LuaTweaker Test Suite: Loot Service
-- Verifies OBSERVABLE pending-state changes (via GetModifications):
-- additions increase counts, removals remove pending entries and are
-- recorded in the summary. No server runtime required.
-- ===================================================================
local Loot = require("LuaTweaker.Loot")

local function check(label, cond)
    if cond then
        print("[Test][Loot] " .. label .. " OK")
    else
        print("[ERROR][Test] " .. label .. " FAILED")
    end
end

local function count(key)
    local mods = Loot:GetModifications()
    if type(mods) ~= "table" then
        return -1
    end
    return mods[key] or -1
end

-- Establish a clean baseline before asserting absolute counts.
Loot:ClearAll()
check("ClearAll baseline (all counts 0)", count("mobDrops") == 0
    and count("chestLoot") == 0 and count("blockDrops") == 0
    and count("fishingLoot") == 0 and count("removals") == 0)

-- ==== MOB DROPS: ADD THEN REMOVE ====
Loot:AddMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 2, 1)
check("AddMobDrop increments mobDrops", count("mobDrops") == 1)

local removed = Loot:RemoveMobDrop("minecraft:zombie", "minecraft:diamond")
check("RemoveMobDrop returns true", removed == true)
check("RemoveMobDrop removes pending entry (mobDrops=0)", count("mobDrops") == 0)
check("RemoveMobDrop recorded in summary (removals=1)", count("removals") == 1)

Loot:AddMobDrop("minecraft:zombie", "minecraft:iron_ingot", 0.5, 1, 3)
check("AddMobDrop after removal increments mobDrops", count("mobDrops") == 1)

-- Removing an item this API never added only records the removal; it cannot
-- rewrite a vanilla table (documented limitation of the loot provider).
Loot:RemoveMobDrop("minecraft:zombie", "minecraft:rotten_flesh")
check("RemoveMobDrop of unmanaged item keeps mobDrops", count("mobDrops") == 1)
check("RemoveMobDrop of unmanaged item recorded (removals=2)", count("removals") == 2)

-- ==== CHEST LOOT: ADD THEN REMOVE ====
Loot:AddChestLoot("minecraft:chests/simple_dungeon", "minecraft:emerald", 0.2, 1, 4)
check("AddChestLoot increments chestLoot", count("chestLoot") == 1)

local chestRemoved = Loot:RemoveChestLoot("minecraft:chests/simple_dungeon", "minecraft:emerald")
check("RemoveChestLoot returns true", chestRemoved == true)
check("RemoveChestLoot removes pending entry (chestLoot=0)", count("chestLoot") == 0)
check("RemoveChestLoot recorded in summary (removals=3)", count("removals") == 3)

-- ==== BLOCK + FISHING ====
Loot:SetBlockDrop("minecraft:stone", "minecraft:cobblestone", 0, "")
check("SetBlockDrop increments blockDrops", count("blockDrops") == 1)
Loot:AddFishingLoot("minecraft:name_tag", 0.05, "TREASURE")
check("AddFishingLoot increments fishingLoot", count("fishingLoot") == 1)

-- ==== FINAL CLEAR ====
Loot:ClearAll()
local afterClear = Loot:GetModifications()
check("ClearAll zeroes every count", type(afterClear) == "table"
    and afterClear.mobDrops == 0 and afterClear.chestLoot == 0
    and afterClear.blockDrops == 0 and afterClear.fishingLoot == 0
    and afterClear.removals == 0)

print("[Test][Loot] Loot service verified")
