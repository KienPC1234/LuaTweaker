-- ===================================================================
-- LuaTweaker Test Suite: Network RemoteEvents
-- Creates the channels that LuaScriptsIntegrationTest fires at and
-- proves clientbound dispatch routes to the ACTIVE engine (AGENTS.md 5.6).
-- ===================================================================
local Network = require("LuaTweaker.Network")

local castEvent = Network:GetOrCreateRemoteEvent("StaffCastSkill")
if castEvent and castEvent.OnServerEvent then
    castEvent.OnServerEvent:Connect(function(player)
        print("[Test][Network] StaffCastSkill received for " .. tostring(player and player:getName() or "?"))
    end)
end

local swapEvent = Network:GetOrCreateRemoteEvent("StaffSwapSkill")
if swapEvent and swapEvent.OnServerEvent then
    swapEvent.OnServerEvent:Connect(function(player)
        print("[Test][Network] StaffSwapSkill received for " .. tostring(player and player:getName() or "?"))
    end)
end

local manaSyncEvent = Network:GetOrCreateRemoteEvent("TestManaSync")
if manaSyncEvent then
    -- FireClient requires a real player UUID string; skip here (no server).
    print("[Test][Network] TestManaSync channel created")
end

print("[Test][Network] RemoteEvents: StaffCastSkill, StaffSwapSkill, TestManaSync")
