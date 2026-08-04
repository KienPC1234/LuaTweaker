-- ================================================================
-- ARCANE RPG: Crystal Golem Boss AI
-- Custom behavior via AIGoals + event-driven phase transitions
-- ================================================================
local Events = require("LuaTweaker.Events")
local AIGoals = require("LuaTweaker.AIGoals")

local function getConfig()
    return mod:GetConfig()
end

local function setupBossGoals(entity)
    AIGoals:clearGoals(entity)
    AIGoals:addMeleeAttackGoal(entity, 1, 1.2, false)
    AIGoals:addNearestAttackableTargetGoal(entity, 2, "player")
    AIGoals:addHurtByTargetGoal(entity, 1)
end

Events:Listen("EntitySpawn", function(event)
    if event == nil then return end
    local entity = event
    local entityType = entity:getType()
    if entityType ~= "luatweaker:crystal_golem" then return end

    local cfg = getConfig()
    entity:setMaxHealth(cfg.boss.crystal_golem_health)
    entity:setHealth(cfg.boss.crystal_golem_health)
    entity:setCustomName("§b§lCrystal Golem")
    entity:setCustomNameVisible(true)

    setupBossGoals(entity)

    entity:setAttribute("arcane_rpg.boss.phase", "1")
    entity:setAttribute("arcane_rpg.boss.enraged", "false")

    print("[ArcaneRPG] Crystal Golem spawned! HP: " .. cfg.boss.crystal_golem_health)
end)

Events:Listen("ServerTick", function()
    local Players = require("LuaTweaker.Players")
    local allPlayers = Players.GetPlayers()
    if #allPlayers == 0 then return end

    local Workspace = require("LuaTweaker.Workspace")
    for i = 1, #allPlayers do
        local player = allPlayers[i]
        local nearby = Workspace:GetEntitiesInRadius(player, 40.0)
        for j = 1, #nearby do
            local entity = nearby[j]
            if entity:getType() == "luatweaker:crystal_golem" and entity:isAlive() then
                local health = entity:getHealth()
                local maxHealth = entity:getMaxHealth()
                local phase = entity:getAttribute("arcane_rpg.boss.phase") or "1"

                if health < maxHealth * 0.5 and phase == "1" then
                    entity:setAttribute("arcane_rpg.boss.phase", "2")
                    entity:addEffect("speed", 999999, 1)
                    entity:playSound("minecraft:entity.ender_dragon.growl", 2.0, 0.5)
                    entity:spawnParticle("minecraft:end_rod", 40, 1.0)
                    for k = 1, #allPlayers do
                        allPlayers[k]:sendActionBar("§c§lThe Crystal Golem becomes ENRAGED!")
                        allPlayers[k]:playSound("minecraft:entity.ender_dragon.growl", 1.0, 0.5)
                    end
                end

                if phase == "2" and math.random() < 0.01 then
                    entity:shootProjectile("minecraft:small_fireball", 1.5, 2.0)
                    entity:playSound("minecraft:entity.blaze.shoot", 1.0, 0.8)
                end
            end
        end
    end
end)

Events:Listen("EntityDeath", function(event)
    if event == nil then return end
    local entity = event
    if entity:getType() ~= "luatweaker:crystal_golem" then return end

    local px = entity:getX()
    local py = entity:getY()
    local pz = entity:getZ()
    local Workspace = require("LuaTweaker.Workspace")
    for i = 1, 8 do
        local angle = (i / 8) * math.pi * 2
        local dx = math.cos(angle) * 3
        local dz = math.sin(angle) * 3
        Workspace:SetBlockState("minecraft:fire",
            math.floor(px + dx), math.floor(py), math.floor(pz + dz))
    end

    local Players = require("LuaTweaker.Players")
    local allPlayers = Players.GetPlayers()
    for i = 1, #allPlayers do
        allPlayers[i]:sendTitle(
            "§b§lVICTORY!",
            "§7The Crystal Golem has been defeated",
            10, 60, 20
        )
        allPlayers[i]:giveExperience(500)
    end

    print("[ArcaneRPG] Crystal Golem defeated!")
end)

print("[ArcaneRPG] Boss AI loaded: Crystal Golem (2 phases)")
