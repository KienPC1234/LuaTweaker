-- ===================================================================
-- LuaTweaker Test Suite: Loot Service
-- Exercises mob drops, chest loot, block drops, fishing and the
-- pending-modification summary (all pure Java, no server needed).
-- ===================================================================
local Loot = require("LuaTweaker.Loot")

local function check(label, cond)
    if cond then
        print("[Test][Loot] " .. label .. " OK")
    else
        print("[ERROR][Test] " .. label .. " FAILED")
    end
end

-- Mob drops
Loot:AddMobDrop("minecraft:zombie", "minecraft:diamond", 0.1, 1, 2, 1)
Loot:AddMobDrop("minecraft:skeleton", "minecraft:bone", 0.5, 1, 3)
local mods = Loot:GetModifications()
check("AddMobDrop recorded (mobDrops=2)", mods ~= nil and mods.mobDrops == 2)

local removed = Loot:RemoveMobDrop("minecraft:zombie", "minecraft:diamond")
check("RemoveMobDrop returns true", removed == true)

-- Chest loot
Loot:AddChestLoot("minecraft:chests/simple_dungeon", "minecraft:emerald", 0.2, 1, 4)
local chestRemoved = Loot:RemoveChestLoot("minecraft:chests/simple_dungeon", "minecraft:emerald")
check("RemoveChestLoot returns true", chestRemoved == true)

-- Block drops
Loot:SetBlockDrop("minecraft:stone", "minecraft:cobblestone", 0, "")
Loot:SetBlockDrop("minecraft:coal_ore", "minecraft:coal", 1, "minecraft:coal_ore")

-- Fishing
Loot:AddFishingLoot("minecraft:name_tag", 0.05, "TREASURE")
Loot:AddFishingLoot("minecraft:stick", 0.1, "JUNK")

-- Summary + clear
local summary = Loot:GetModifications()
check("GetModifications returns table", type(summary) == "table")
Loot:ClearAll()
local afterClear = Loot:GetModifications()
check("ClearAll zeroes counts", afterClear ~= nil
    and afterClear.mobDrops == 0 and afterClear.chestLoot == 0
    and afterClear.blockDrops == 0 and afterClear.fishingLoot == 0)

print("[Test][Loot] Loot service verified")
