-- ================================================================
-- ARCANE RPG: Skill System
-- 4 skills with cooldowns, mana costs, network sync
-- ================================================================
local Storage = require("LuaTweaker.Storage")
local Events = require("LuaTweaker.Events")
local Network = require("LuaTweaker.Network")

local Skills = {}

local skillSyncEvent = Network:GetOrCreateRemoteEvent("ArcaneSkillSync")
local cooldownEvent = Network:GetOrCreateRemoteEvent("ArcaneCooldownSync")

local function getConfig()
    return mod:GetConfig()
end

local function getCooldownStore(player)
    local uuid = player:getUuid()
    return Storage:GetPlayerStorage(uuid)
end

local function getCachedCooldowns(player)
    local uuid = player:getUuid()
    local sessionStorage = Storage:GetSessionStorage()
    local key = "arcane_rpg.cooldowns." .. uuid
    local cached = sessionStorage:GetAsync(key)
    if cached == nil then
        cached = {}
        sessionStorage:SetAsync(key, cached)
    end
    return cached
end

local function setCachedCooldowns(player, data)
    local uuid = player:getUuid()
    local sessionStorage = Storage:GetSessionStorage()
    local key = "arcane_rpg.cooldowns." .. uuid
    sessionStorage:SetAsync(key, data)
end

function Skills:IsOnCooldown(player, skillName)
    local cooldowns = getCachedCooldowns(player)
    local expiry = cooldowns[skillName]
    if expiry == nil then return false end
    return task.getTimeClock() < expiry
end

function Skills:GetCooldownRemaining(player, skillName)
    local cooldowns = getCachedCooldowns(player)
    local expiry = cooldowns[skillName]
    if expiry == nil then return 0 end
    local remaining = expiry - task.getTimeClock()
    return remaining > 0 and remaining or 0
end

function Skills:SetCooldown(player, skillName, durationSeconds)
    local cooldowns = getCachedCooldowns(player)
    cooldowns[skillName] = task.getTimeClock() + durationSeconds
    setCachedCooldowns(player, cooldowns)
end

function Skills:CastSkill(player, skillName)
    local cfg = getConfig()
    local skillCfg = cfg.skills[skillName]
    if skillCfg == nil then
        player:sendActionBar("§cUnknown skill: " .. skillName)
        return false
    end

    if Skills:IsOnCooldown(player, skillName) then
        local remaining = Skills:GetCooldownRemaining(player, skillName)
        player:sendActionBar("§cOn cooldown! " .. string.format("%.1f", remaining) .. "s remaining")
        return false
    end

    local ManaSystem = require("LuaTweaker.ManaSystem")
    if not ManaSystem:HasEnough(player, skillCfg.cost) then
        player:sendActionBar("§cNot enough mana! Need " .. skillCfg.cost)
        return false
    end

    ManaSystem:SpendMana(player, skillCfg.cost)
    Skills:SetCooldown(player, skillName, skillCfg.cooldown)

    if skillName == "crystal_bolt" then
        Skills:_castCrystalBolt(player, skillCfg)
    elseif skillName == "frost_nova" then
        Skills:_castFrostNova(player, skillCfg)
    elseif skillName == "arcane_shield" then
        Skills:_castArcaneShield(player, skillCfg)
    elseif skillName == "meteor_strike" then
        Skills:_castMeteorStrike(player, skillCfg)
    end

    skillSyncEvent:FireClient(player, skillName, "cast")
    return true
end

function Skills:_castCrystalBolt(player, cfg)
    player:shootProjectile("luatweaker:crystal_bolt", cfg.projectile_speed, 0.5)
    player:playSound("minecraft:entity.blaze.shoot", 1.0, 1.5)
    player:sendActionBar("§bCrystal Bolt!")
end

function Skills:_castFrostNova(player, cfg)
    local px = player:getX()
    local py = player:getY()
    local pz = player:getZ()
    local Workspace = require("LuaTweaker.Workspace")
    local nearby = Workspace:GetEntitiesInRadius(player, cfg.radius)
    local hitCount = 0
    for i = 1, #nearby do
        local entity = nearby[i]
        if entity:getUuid() ~= player:getUuid() and entity:isLiving() then
            entity:addEffect("slowness", cfg.slow_duration, 2)
            entity:damage(cfg.damage)
            entity:spawnParticle("minecraft:cloud", 5, 0.5)
            hitCount = hitCount + 1
        end
    end
    player:spawnParticle("minecraft:cloud", 30, 1.0)
    player:playSound("minecraft:block.glass.break", 1.0, 0.5)
    player:sendActionBar("§bFrost Nova! Hit " .. hitCount .. " enemies!")
end

function Skills:_castArcaneShield(player, cfg)
    player:addEffect("absorption", cfg.duration, 2)
    player:addEffect("resistance", cfg.duration, 0)
    player:spawnParticle("minecraft:enchant", 20, 0.3)
    player:playSound("minecraft:entity.ender_dragon.growl", 0.5, 2.0)
    player:sendActionBar("§bArcane Shield activated! (" .. cfg.duration / 20 .. "s)")
end

function Skills:_castMeteorStrike(player, cfg)
    local px = player:getX()
    local py = player:getY()
    local pz = player:getZ()
    local Workspace = require("LuaTweaker.Workspace")
    task.delay(0.5, function()
        Workspace:SetBlockState("minecraft:fire", px, py - 1, pz)
        local nearby = Workspace:GetEntitiesInRadius(player, 8.0)
        for i = 1, #nearby do
            local entity = nearby[i]
            if entity:getUuid() ~= player:getUuid() and entity:isLiving() then
                entity:damage(cfg.damage)
                entity:setIgniteSeconds(5)
            end
        end
    end)
    player:spawnParticle("minecraft:flame", 50, 2.0)
    player:playSound("minecraft:entity.generic.explode", 2.0, 0.5)
    player:sendActionBar("§d§lMETEOR STRIKE!")
end

function Skills:GetAllCooldowns(player)
    return getCachedCooldowns(player)
end

return Skills
