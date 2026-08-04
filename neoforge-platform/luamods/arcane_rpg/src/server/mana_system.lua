-- ================================================================
-- ARCANE RPG: Mana System
-- Persistent mana storage + regeneration via task scheduler
-- ================================================================
local Storage = require("LuaTweaker.Storage")

local ManaSystem = {}

local function getConfig()
    return mod:GetConfig()
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
    local Players = require("LuaTweaker.Players")
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

local Events = require("LuaTweaker.Events")
Events:Listen("ServerTick", function()
    ManaSystem:RegenTick()
end)

Events:Listen("PlayerJoin", function(event)
    local player = event
    if player == nil then return end
    local cfg = getConfig()
    ManaSystem:SetMana(player, cfg.mana.start_mana)
    player:sendActionBar("§bWelcome to Arcane RPG! Mana: " .. cfg.mana.start_mana .. "/" .. cfg.mana.max_mana)
end)

return ManaSystem
