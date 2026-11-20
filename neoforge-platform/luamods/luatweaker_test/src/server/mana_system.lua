-- ================================================================
-- LuaTweaker Test: Mana System
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

-- Tick tuning resolved ONCE at load (AGENTS.md 5.8: config-driven, no per-tick
-- config walks). /lt reload re-runs this file, so edits + reload refresh these.
local manaCfg = (type(CONFIG) == "table" and type(CONFIG.mana) == "table") and CONFIG.mana or {}
local MANA_MAX = tonumber(manaCfg.max_mana) or 200
local START_MANA = tonumber(manaCfg.start_mana) or 50
local REGEN_INTERVAL = math.max(1, math.floor(tonumber(manaCfg.regen_interval_ticks) or 5))
local REGEN_PER_ROUND = (tonumber(manaCfg.regen_per_second) or 5) * (REGEN_INTERVAL / 20.0)
local SYNC_INTERVAL = math.max(1, math.floor(tonumber(manaCfg.sync_interval_ticks) or 20))

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
    return MANA_MAX
end

function ManaSystem:GetMana(player)
    local uuid = player:getUuid()
    local store = Storage:GetPlayerStorage(uuid)
    local current = store:GetAsync("luatweaker_test.mana")
    if current == nil then
        current = cfgValue({ "mana", "start_mana" }, 50)
        store:SetAsync("luatweaker_test.mana", current)
    end
    return current
end

function ManaSystem:SetMana(player, amount)
    local maxMana = ManaSystem:GetMaxMana(player)
    amount = math.max(0, math.min(amount, maxMana))
    local uuid = player:getUuid()
    local store = Storage:GetPlayerStorage(uuid)
    store:SetAsync("luatweaker_test.mana", amount)
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

local syncCounter = 0
local regenCounter = 0

function ManaSystem:RegenTick()
    -- Throttled: one storage read per player per round, one write only when
    -- the value actually changes. SetAsync clones the whole player doc, so
    -- doing it every tick (20x/s) is pure write amplification.
    regenCounter = regenCounter + 1
    if regenCounter < REGEN_INTERVAL then
        return
    end
    regenCounter = 0
    local allPlayers = Players.GetPlayers()
    if #allPlayers == 0 then
        return
    end
    for i = 1, #allPlayers do
        local store = Storage:GetPlayerStorage(allPlayers[i]:getUuid())
        local current = store:GetAsync("luatweaker_test.mana")
        if current == nil then
            store:SetAsync("luatweaker_test.mana", START_MANA)
        elseif current < MANA_MAX then
            local grown = current + REGEN_PER_ROUND
            if grown > MANA_MAX then
                grown = MANA_MAX
            end
            store:SetAsync("luatweaker_test.mana", grown)
        end
    end
end

function ManaSystem:SyncMana(player)
    if player == nil then return end
    local current = ManaSystem:GetMana(player)
    local maxMana = ManaSystem:GetMaxMana(player)
    manaSyncEvent:FireClient(player, current, maxMana)
end

Events:Listen("ServerTick", function()
    ManaSystem:RegenTick()
    -- Sync mana to clients once per sync interval (every 20 ticks by default)
    -- to avoid packet spam; interval comes from config (mana.sync_interval_ticks).
    syncCounter = syncCounter + 1
    if syncCounter >= SYNC_INTERVAL then
        syncCounter = 0
        local allPlayers = Players.GetPlayers()
        for i = 1, #allPlayers do
            ManaSystem:SyncMana(allPlayers[i])
        end
    end
end)

return ManaSystem
