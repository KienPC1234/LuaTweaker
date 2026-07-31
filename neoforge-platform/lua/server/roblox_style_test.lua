-- ===================================================================
-- Comprehensive Roblox-style Server Script (Actions, Storage, Networking)
-- Performs player title announcements, chat messages, item rewards, and database persistence
-- ===================================================================

print("Starting Roblox Server Controller script...")

-- 1. Services
local Workspace = Mod:GetService("Workspace")
local EntityService = Mod:GetService("EntityService")
local WorldStorage = Mod:GetService("WorldStorage")
local PlayerStorage = Mod:GetService("PlayerStorage")
local NetworkService = Mod:GetService("NetworkService")

-- 2. Workspace & Vector3 Block Manipulation
local targetBlock = Workspace:GetBlock(Vector3.new(100, 64, 200))
if targetBlock then
    print("[Server] Found block ID: " .. targetBlock.Id .. " at pos " .. tostring(targetBlock.Position))
    targetBlock.Id = "minecraft:gold_block"
    print("[Server] Updated block ID to gold_block")
end

-- 3. Reactive EntitySpawned Signal Connection & Player Actions
EntityService.EntitySpawned:Connect(function(entity)
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

-- 4. BSON Document Database Persistence (World & Player DataStore)
local playCount = WorldStorage:GetAsync("play_count") or 0
WorldStorage:SetAsync("play_count", playCount + 1)
print("[Server] BSON Database World play_count updated to: " .. tostring(WorldStorage:GetAsync("play_count")))

-- 5. Rocket Network RemoteEvent & RemoteFunction Server Handlers
local actionEvent = NetworkService:GetOrCreateRemoteEvent("PlayerActionEvent")
actionEvent.OnServerEvent:Connect(function(player, actionType, keyName)
    local playerName = player and player.Name or "Unknown Player"
    print("[Server] Received PlayerActionEvent from " .. playerName .. ": action=" .. tostring(actionType) .. " key=" .. tostring(keyName))

    if player then
        player:SendMessage("§e[Keybind] Server processed action key '" .. tostring(keyName) .. "'")
        player:SendTitle("§6ACTION TRIGGERED!", "§fKey: " .. tostring(keyName))
        player:GiveItem("minecraft:emerald", 1)
    end
end)

local requestHealthFunc = NetworkService:GetOrCreateRemoteFunction("RequestPlayerHealth")
requestHealthFunc.OnServerInvoke = function(player)
    print("[Server] RemoteFunction RequestPlayerHealth invoked by " .. (player and player.Name or "Client"))
    return 100.0
end

print("Roblox Server Controller script loaded successfully!")
