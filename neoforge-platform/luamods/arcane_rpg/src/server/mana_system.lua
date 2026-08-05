-- ================================================================
-- ARCANE RPG: Mana System
-- Persistent mana storage + regeneration via task scheduler
-- ================================================================
local Storage = require("LuaTweaker.Storage")
local Events = require("LuaTweaker.Events")
local Players = require("LuaTweaker.Players")
local Network = require("LuaTweaker.Network")

-- Capture mod table at load time. `mod` is a GLOBAL shared across all mods,
-- so referencing it later may point to a different mod's table (or nil).
local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

local ManaSystem = {}

local manaSyncEvent = Network:GetOrCreateRemoteEvent("ArcaneManaSync")

local function getConfig()
    return CONFIG
end

function ManaSystem:GetMaxMana(player)
    local cfg = getConfig()
    return cfg.mana.max_mana
end

function ManaSystem:GetMana(player)
    local uuid = player:getUuid()
    local store = Storage:GetPlayerStorage(uuid)
    local current = store:GetAsync("arcane_rpg.mana")
    if current == nil then
        local cfg = getConfig()
        current = cfg.mana.start_mana
        store:SetAsync("arcane_rpg.mana", current)
    end
    return current
end

function ManaSystem:SetMana(player, amount)
    local cfg = getConfig()
    local maxMana = cfg.mana.max_mana
    amount = math.max(0, math.min(amount, maxMana))
    local uuid = player:getUuid()
    local store = Storage:GetPlayerStorage(uuid)
    store:SetAsync("arcane_rpg.mana", amount)
    return amount
end

function ManaSystem:SpendMana(player, amount)
    local current = ManaSystem:GetMana(player)
    if current < amount then
        return false
    end
    ManaSystem:SetMana(player, current - amount)
    return true
end

function ManaSystem:RestoreMana(player, amount)
    local current = ManaSystem:GetMana(player)
    local newAmount = ManaSystem:SetMana(player, current + amount)
    return newAmount - current
end

function ManaSystem:GetManaPercent(player)
    local cfg = getConfig()
    local current = ManaSystem:GetMana(player)
    return current / cfg.mana.max_mana
end

function ManaSystem:HasEnough(player, amount)
    return ManaSystem:GetMana(player) >= amount
end

function ManaSystem:RegenTick()
    local cfg = getConfig()
    local regenPerTick = cfg.mana.regen_per_second / 20.0
    local allPlayers = Players.GetPlayers()
    for i = 1, #allPlayers do
        local player = allPlayers[i]
        local current = ManaSystem:GetMana(player)
        local maxMana = cfg.mana.max_mana
        if current < maxMana then
            ManaSystem:SetMana(player, current + regenPerTick)
        end
    end
end

function ManaSystem:SyncMana(player)
    if player == nil then return end
    local current = ManaSystem:GetMana(player)
    local maxMana = ManaSystem:GetMaxMana(player)
    manaSyncEvent:FireClient(player, current, maxMana)
end

local syncCounter = 0
Events:Listen("ServerTick", function()
    ManaSystem:RegenTick()
    -- Sync mana to clients once per second (every 20 ticks) to avoid packet spam.
    syncCounter = syncCounter + 1
    if syncCounter >= 20 then
        syncCounter = 0
        local allPlayers = Players.GetPlayers()
        for i = 1, #allPlayers do
            ManaSystem:SyncMana(allPlayers[i])
        end
    end
end)

Events:Listen("PlayerJoin", function(event)
    if event == nil then return end
    -- event is a DynamicJavaProxy of PlayerLoggedInEvent -> getEntity() returns the raw player.
    -- NOTE: this is a RAW Java Player proxy, NOT an IPlayer Lua table. Only Entity-level
    -- methods are safe here (getUuid etc.); IPlayer-only methods like sendActionBar do NOT exist.
    local player = event.Entity
    if player == nil then
        player = event:getEntity()
    end
    if player == nil then return end
    local cfg = getConfig()
    ManaSystem:SetMana(player, cfg.mana.start_mana)
    ManaSystem:SyncMana(player)
end)

return ManaSystem
