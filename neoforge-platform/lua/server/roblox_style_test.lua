-- ===================================================================
-- Comprehensive Server Script (Actions, Storage, Networking)
-- Performs player title announcements, chat messages, item rewards
-- ===================================================================
local World    = require("LuaTweaker.World")
local Entities = require("LuaTweaker.Entities")
local Storage  = require("LuaTweaker.Storage")
local Network  = require("LuaTweaker.Network")
local Vector3  = require("LuaTweaker.Math.Vector3")

print("Starting Server Controller script...")

-- 1. World & Vector3 Block Manipulation
local targetBlock = World:GetBlock(Vector3.new(100, 64, 200))
if targetBlock then
    print("[Server] Found block ID: " .. targetBlock.Id .. " at pos " .. tostring(targetBlock.Position))
    targetBlock.Id = "minecraft:gold_block"
    print("[Server] Updated block ID to gold_block")
end

-- 2. Reactive EntitySpawned Signal Connection & Player Actions
Entities.EntitySpawned:Connect(function(entity)
    print("[Server] Entity spawned: Name=" .. entity.Name .. ", Type=" .. entity.Type)

    -- If the entity is a player, perform direct player HUD actions
    if entity.Type == "minecraft:player" then
        entity:SendMessage("§a[LuaTweaker] Welcome to the server, " .. entity.Name .. "!")
        entity:SendTitle("§6WELCOME!", "§eLuaTweaker Engine v1.0 Active")
        entity:SendOverlayMessage("§b+100 Bonus Coins Granted!")
        entity:GiveItem("minecraft:diamond", 3)
        print("[Server] Player HUD actions & diamond reward sent to " .. entity.Name)
    end
end)

-- 3. Database Persistence
local playCount = Storage:get("play_count", 0)
Storage:set("play_count", playCount + 1)
print("[Server] Database play_count updated to: " .. tostring(Storage:get("play_count", 0)))

-- 4. RemoteEvent & RemoteFunction Server Handlers
local actionEvent = Network.GetOrCreateRemoteEvent("PlayerActionEvent")
actionEvent.OnServerEvent:Connect(function(player, actionType, keyName)
    local playerName = player and player.Name or "Unknown Player"
    print("[Server] Received PlayerActionEvent from " .. playerName .. ": action=" .. tostring(actionType) .. " key=" .. tostring(keyName))

    if player then
        player:SendMessage("§e[Keybind] Server processed action key '" .. tostring(keyName) .. "'")
        player:SendTitle("§6ACTION TRIGGERED!", "§fKey: " .. tostring(keyName))
        player:GiveItem("minecraft:emerald", 1)
    end
end)

local requestHealthFunc = Network.GetOrCreateRemoteFunction("RequestPlayerHealth")
requestHealthFunc.OnServerInvoke = function(player)
    print("[Server] RemoteFunction RequestPlayerHealth invoked by " .. (player and player.Name or "Client"))
    return 100.0
end

print("Server Controller script loaded successfully!")
