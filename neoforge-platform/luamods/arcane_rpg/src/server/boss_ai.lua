-- ================================================================
-- ARCANE RPG: Crystal Golem Boss AI
-- Custom behavior via AIGoals + event-driven phase transitions
--
-- NOTE: EntitySpawn/EntityDeath events deliver RAW Java entity proxies that
-- do NOT expose the IEntity wrapper API (setAttribute, getType-as-string, ...).
-- To stay correct we drive everything from the ServerTick scan, whose entities
-- come from World:GetEntitiesInRadius() and ARE proper IEntity Lua tables.
-- ================================================================
local Events = require("LuaTweaker.Events")
local AIGoals = require("LuaTweaker.AIGoals")
local Players = require("LuaTweaker.Players")
local World = require("LuaTweaker.World")

local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

local function getConfig()
    return CONFIG
end

-- Forward declaration: handleBossDeath is defined below but referenced from the
-- ServerTick closure above; without this it would become an implicit global.
local handleBossDeath

local function setupBossGoals(entity)
    AIGoals:clearGoals(entity)
    AIGoals:addMeleeAttackGoal(entity, 1, 1.2, false)
    AIGoals:addNearestAttackableTargetGoal(entity, 2, "player")
    AIGoals:addHurtByTargetGoal(entity, 1)
end

local function setupBoss(entity)
    local cfg = getConfig()
    entity:setMaxHealth(cfg.boss.crystal_golem_health)
    entity:setHealth(cfg.boss.crystal_golem_health)
    entity:setCustomName("Crystal Golem")
    entity:setCustomNameVisible(true)
    setupBossGoals(entity)
    entity:setAttribute("arcane_rpg.boss.phase", "1")
    entity:setAttribute("arcane_rpg.boss.death_handled", "false")
    print("[ArcaneRPG] Crystal Golem spawned! HP: " .. cfg.boss.crystal_golem_health)
end

Events:Listen("ServerTick", function()
    local allPlayers = Players.GetPlayers()
    if #allPlayers == 0 then return end

    for i = 1, #allPlayers do
        local player = allPlayers[i]
        local nearby = World:GetEntitiesInRadius(player, 64.0)
        for j = 1, #nearby do
            local entity = nearby[j]
            if entity:getType() ~= "luatweaker:crystal_golem" then
                -- Not a boss; skip this entity.
            else
                local phase = entity:getAttribute("arcane_rpg.boss.phase")
                if phase == nil or phase == "" then
                    setupBoss(entity)
                    phase = "1"
                end

                if not entity:isAlive() then
                    local deathHandledAttr = entity:getAttribute("arcane_rpg.boss.death_handled")
                    if deathHandledAttr ~= "true" then
                        entity:setAttribute("arcane_rpg.boss.death_handled", "true")
                        handleBossDeath(entity, allPlayers)
                    end
                else
                    local health = entity:getHealth()
                    local maxHealth = entity:getMaxHealth()

                    -- Phase 2 at 50% HP
                    if phase == "1" and health < maxHealth * 0.5 then
                        entity:setAttribute("arcane_rpg.boss.phase", "2")
                        entity:addEffect("speed", 999999, 1)
                        entity:playSound("minecraft:entity.ender_dragon.growl", 2.0, 0.5)
                        entity:spawnParticle("minecraft:end_rod", 40, 1.0)
                        for k = 1, #allPlayers do
                            allPlayers[k]:sendActionBar("The Crystal Golem becomes ENRAGED!")
                            allPlayers[k]:playSound("minecraft:entity.ender_dragon.growl", 1.0, 0.5)
                        end
                    end

                    -- Phase 2: occasional fireball volleys
                    if phase == "2" and math.random() < 0.01 then
                        entity:shootProjectile("minecraft:small_fireball", 1.5, 2.0)
                        entity:playSound("minecraft:entity.blaze.shoot", 1.0, 0.8)
                    end

                    -- Phase 3 at 25% HP: enrage + periodic AoE pulse
                    if phase == "2" and health < maxHealth * 0.25 then
                        entity:setAttribute("arcane_rpg.boss.phase", "3")
                        entity:addEffect("regeneration", 200, 2)
                        entity:spawnParticle("minecraft:dragon_breath", 50, 2.0)
                        entity:playSound("minecraft:entity.ender_dragon.growl", 3.0, 0.3)
                        for k = 1, #allPlayers do
                            allPlayers[k]:sendTitle(
                                "PHASE 3!",
                                "The Crystal Golem unleashes its final form!",
                                10, 40, 20
                            )
                        end
                    end

                    if phase == "3" and math.random() < 0.005 then
                        local targets = World:GetEntitiesInRadius(entity, 8.0)
                        for t = 1, #targets do
                            local target = targets[t]
                            if target:getUuid() ~= entity:getUuid() and target:isLiving() then
                                target:damage(8.0)
                                target:addEffect("weakness", 60, 1)
                                target:spawnParticle("minecraft:dragon_breath", 10, 0.5)
                            end
                        end
                        entity:playSound("minecraft:entity.warden.sonic_boom", 2.0, 1.5)
                    end
                end
            end
        end
    end
end)

handleBossDeath = function(entity, allPlayers)
    local px = entity:getX()
    local py = entity:getY()
    local pz = entity:getZ()
    for i = 1, 8 do
        local angle = (i / 8) * math.pi * 2
        local dx = math.cos(angle) * 3
        local dz = math.sin(angle) * 3
        World:SetBlockState(
            math.floor(px + dx), math.floor(py), math.floor(pz + dz), "minecraft:fire")
    end

    for i = 1, #allPlayers do
        allPlayers[i]:sendTitle(
            "VICTORY!",
            "The Crystal Golem has been defeated",
            10, 60, 20
        )
        allPlayers[i]:giveExperience(500)
    end

    print("[ArcaneRPG] Crystal Golem defeated!")
end

print("[ArcaneRPG] Boss AI loaded: Crystal Golem (3 phases)")
