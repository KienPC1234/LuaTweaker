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

-- Defensive fallbacks: if the config file was missing/unreadable CONFIG is empty,
-- so every tunable gets a sane default instead of crashing the regen tick.
local function cfgValue(path, fallback)
    local node = getConfig()
    if type(node) ~= "table" then return fallback end
    for _, key in ipairs(path) do
        if type(node) ~= "table" then return fallback end
        node = node[key]
    end
    return (node ~= nil and type(node) ~= "table") and node or fallback
end

function ManaSystem:GetMaxMana(player)
    return cfgValue({ "mana", "max_mana" }, 200)
end

function ManaSystem:GetMana(player)
    local uuid = player:getUuid()
    local store = Storage:GetPlayerStorage(uuid)
    local current = store:GetAsync("arcane_rpg.mana")
    if current == nil then
        current = cfgValue({ "mana", "start_mana" }, 50)
        store:SetAsync("arcane_rpg.mana", current)
    end
    return current
end

function ManaSystem:SetMana(player, amount)
    local maxMana = ManaSystem:GetMaxMana(player)
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
    local current = ManaSystem:GetMana(player)
    return current / ManaSystem:GetMaxMana(player)
end

function ManaSystem:HasEnough(player, amount)
    return ManaSystem:GetMana(player) >= amount
end

function ManaSystem:RegenTick()
    local regenPerTick = cfgValue({ "mana", "regen_per_second" }, 5) / 20.0
    local maxMana = ManaSystem:GetMaxMana(nil)
    local allPlayers = Players.GetPlayers()
    for i = 1, #allPlayers do
        local player = allPlayers[i]
        local current = ManaSystem:GetMana(player)
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
    -- Sync mana to clients once per sync interval (every 20 ticks by default)
    -- to avoid packet spam; interval comes from config (mana.sync_interval_ticks).
    local syncInterval = math.max(1, cfgValue({ "mana", "sync_interval_ticks" }, 20))
    syncCounter = syncCounter + 1
    if syncCounter >= syncInterval then
        syncCounter = 0
        local allPlayers = Players.GetPlayers()
        for i = 1, #allPlayers do
            ManaSystem:SyncMana(allPlayers[i])
        end
    end
end)

return ManaSystem
