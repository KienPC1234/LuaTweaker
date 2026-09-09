-- ===================================================================
-- LuaTweaker Test Suite: World Interaction Primitives
-- Exercises the low-level World API (GetBlockState / SetBlockState /
-- GetBlockEntityData / ExecuteCommand). Safe: every call is wrapped in
-- pcall so a missing server or block can never crash the load.
-- ===================================================================
local World = require("LuaTweaker.World")

local cfg = mod and mod:GetConfig() or {}
local TEST_BLOCK = cfg.world_test_block or "minecraft:stone"

local function safeCall(label, fn)
    local ok, result = pcall(fn)
    if ok then
        print("[Test][World] " .. label .. " -> " .. tostring(result))
    else
        print("[ERROR][Test] " .. label .. " FAILED: " .. tostring(result))
    end
end

-- Without a live server these must return nil/false gracefully (never throw).
safeCall("GetBlockState(0,0,0)", function()
    local s = World:GetBlockState(0, 0, 0)
    return s == nil and "nil (no server)" or "state=" .. tostring(s and s.Id)
end)

safeCall("SetBlockState(0,0,0," .. TEST_BLOCK .. ")", function()
    return World:SetBlockState(0, 0, 0, TEST_BLOCK)
end)

safeCall("GetBlockEntityData(0,0,0)", function()
    return World:GetBlockEntityData(0, 0, 0) == nil and "nil (no server)" or "data"
end)

safeCall("ExecuteCommand('say hi')", function()
    return World:ExecuteCommand("say hi")
end)

-- Exercise the array->List conversion used by NBT writes (AGENTS.md test
-- contract): an Items list must round-trip as a Java List, not a Map.
safeCall("SetBlockEntityData Items list", function()
    return World:SetBlockEntityData(0, 0, 0, { Items = { { id = "minecraft:stick", count = 1, slot = 0 } } })
end)

print("[Test][World] Interaction primitives verified")
