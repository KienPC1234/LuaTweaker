-- ================================================================
-- LuaTweaker Test: Multiblock Machine & World Primitives
-- Demonstrates multiblock pattern validation, redstone checks,
-- and automated resource routing (energy, fluid, item transfers).
-- ================================================================
local World  = require("LuaTweaker.World")
local Events = require("LuaTweaker.Events")
local Task   = require("LuaTweaker.Task")

local MultiblockMachine = {}

-- 3x3 multiblock pattern definition relative to controller origin (0, 0, 0)
-- A 3x3 ring of reinforced crystal blocks with an altar core
local INFUSER_CHAMBER_PATTERN = {
    { x = -1, y = -1, z = -1, block = "luatweaker:crystal_block" },
    { x =  0, y = -1, z = -1, block = "luatweaker:crystal_block" },
    { x =  1, y = -1, z = -1, block = "luatweaker:crystal_block" },
    { x = -1, y = -1, z =  0, block = "luatweaker:crystal_block" },
    { x =  0, y = -1, z =  0, block = "minecraft:obsidian" },
    { x =  1, y = -1, z =  0, block = "luatweaker:crystal_block" },
    { x = -1, y = -1, z =  1, block = "luatweaker:crystal_block" },
    { x =  0, y = -1, z =  1, block = "luatweaker:crystal_block" },
    { x =  1, y = -1, z =  1, block = "luatweaker:crystal_block" },
}

--- Validates whether the multiblock structure at the given position is complete.
--- @param level string
--- @param origin table {X, Y, Z}
--- @return boolean
function MultiblockMachine.ValidateStructure(level, origin)
    return World:CheckPattern(level, origin.X, origin.Y, origin.Z, INFUSER_CHAMBER_PATTERN)
end

--- Runs one tick cycle of the multiblock machine.
--- Pulls redstone signal, transfers energy, extracts fluid/items if operational.
--- @param level string
--- @param pos table {X, Y, Z}
function MultiblockMachine.Tick(level, pos)
    -- 1. Check redstone signal (controller halts if powered or unpowered based on mode)
    local signal = World:GetRedstoneSignal(level, pos.X, pos.Y, pos.Z, "down")
    if signal > 0 then
        -- 2. Verify multiblock physical structure
        local isFormed = MultiblockMachine.ValidateStructure(level, pos)
        if isFormed then
            -- 3. Draw energy from adjacent power bank (e.g. at pos.Y - 1)
            local drawnEnergy = World:TransferEnergy(
                level,
                { X = pos.X, Y = pos.Y - 1, Z = pos.Z },
                { X = pos.X, Y = pos.Y, Z = pos.Z },
                100,
                false
            )

            -- 4. Transfer input fluid (coolant) from neighbor at pos.X - 1
            local drawnFluid = World:TransferFluid(
                level,
                { X = pos.X - 1, Y = pos.Y, Z = pos.Z },
                { X = pos.X, Y = pos.Y, Z = pos.Z },
                50,
                false
            )

            -- 5. Extract completed item from slot 1 to export chest at pos.X + 1
            local exportedItems = World:TransferItem(
                level,
                { X = pos.X, Y = pos.Y, Z = pos.Z },
                { X = pos.X + 1, Y = pos.Y, Z = pos.Z },
                1,  -- slot 1 output
                0,  -- slot 0 of chest
                1,  -- 1 item
                false
            )

            if drawnEnergy > 0 or exportedItems > 0 then
                World:SpawnParticle("minecraft:electric_spark", pos.X + 0.5, pos.Y + 1.2, pos.Z + 0.5, 0.1, 0.1, 0.1, 3, 0.05)
            end
        end
    end
end

-- Hook into block interaction or tick events
Events:Listen("BlockInteract", function(event)
    if event and event.block and event.block.Id == "luatweaker:crystal_infuser" then
        local player = event.player
        local pos = event.block.Position
        local level = event.block.Dimension or "minecraft:overworld"

        local isFormed = MultiblockMachine.ValidateStructure(level, pos)
        if isFormed then
            if player then player:sendActionBar("§aMultiblock structure: Formed and Valid!") end
        else
            if player then player:sendActionBar("§cMultiblock structure: Incomplete or Misaligned.") end
        end
    end
end)

print("[LuaTweakerTest] Multiblock machine controller initialized.")
return MultiblockMachine
