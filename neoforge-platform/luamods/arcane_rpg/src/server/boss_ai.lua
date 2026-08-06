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

-- Defensive boss config: sane fallbacks so a missing/unreadable config file
-- can never crash the boss tick (AGENTS.md 0.14 / 5.8).
local function getBossCfg()
    local cfg = getConfig()
    return (cfg and type(cfg) == "table" and cfg.boss) or {}
end

-- Forward declaration: handleBossDeath is defined below but referenced from the
-- ServerTick closure above; without this it would become an implicit global.
local handleBossDeath

local function setupBossGoals(entity)
    AIGoals:clearGoals(entity)
    AIGoals:addMeleeAttackGoal(entity, 1, getBossCfg().melee_speed or 1.2, false)
    AIGoals:addNearestAttackableTargetGoal(entity, 2, "player")
    AIGoals:addHurtByTargetGoal(entity, 1)
end

local function setupBoss(entity)
    local bossCfg = getBossCfg()
    local bossHealth = bossCfg.crystal_golem_health or 500.0
    entity:setMaxHealth(bossHealth)
    entity:setHealth(bossHealth)
    entity:setCustomName("Crystal Golem")
    entity:setCustomNameVisible(true)
    setupBossGoals(entity)
    entity:setAttribute("arcane_rpg.boss.phase", "1")
    entity:setAttribute("arcane_rpg.boss.death_handled", "false")
    print("[ArcaneRPG] Crystal Golem spawned! HP: " .. bossHealth)
end

Events:Listen("ServerTick", function()
    local allPlayers = Players.GetPlayers()
    if #allPlayers == 0 then return end

    for i = 1, #allPlayers do
        local player = allPlayers[i]
        local bossCfg = getBossCfg()
        local nearby = World:GetEntitiesInRadius(player, bossCfg.scan_radius)
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

                    -- Phase 2 at (phase2_hp_ratio * 100)% HP (config)
                    if phase == "1" and health < maxHealth * bossCfg.phase2_hp_ratio then
                        entity:setAttribute("arcane_rpg.boss.phase", "2")
                        entity:addEffect("speed", bossCfg.enrage_speed_ticks, 1)
                        entity:playSound("minecraft:entity.ender_dragon.growl", 2.0, 0.5)
                        entity:spawnParticle("minecraft:end_rod", 40, 1.0)
                        for k = 1, #allPlayers do
                            allPlayers[k]:sendActionBar("The Crystal Golem becomes ENRAGED!")
                            allPlayers[k]:playSound("minecraft:entity.ender_dragon.growl", 1.0, 0.5)
                        end
                    end

                    -- Phase 2: occasional fireball volleys
                    if phase == "2" and math.random() < bossCfg.phase2_fireball_chance then
                        entity:shootProjectile("minecraft:small_fireball", bossCfg.phase2_fireball_speed, bossCfg.phase2_fireball_inaccuracy)
                        entity:playSound("minecraft:entity.blaze.shoot", 1.0, 0.8)
                    end

                    -- Phase 3 at (phase3_hp_ratio * 100)% HP: enrage + periodic AoE pulse
                    if phase == "2" and health < maxHealth * bossCfg.phase3_hp_ratio then
                        entity:setAttribute("arcane_rpg.boss.phase", "3")
                        entity:addEffect("regeneration", bossCfg.phase3_regen_ticks, 2)
                        entity:spawnParticle("minecraft:dragon_breath", 50, 2.0)
                        entity:playSound("minecraft:entity.ender_dragon.growl", 3.0, 0.3)
                        for k = 1, #allPlayers do
                            allPlayers[k]:sendTitle(
                                "PHASE 3!",
                                "The Crystal Golem unleashes its final form!",
                                bossCfg.phase3_title_fade_in, bossCfg.phase3_title_stay, bossCfg.phase3_title_fade_out
                            )
                        end
                    end

                    if phase == "3" and math.random() < bossCfg.phase3_pulse_chance then
                        local targets = World:GetEntitiesInRadius(entity, bossCfg.phase3_pulse_radius)
                        for t = 1, #targets do
                            local target = targets[t]
                            if target:getUuid() ~= entity:getUuid() and target:isLiving() then
                                target:damage(bossCfg.phase3_pulse_damage)
                                target:addEffect("weakness", bossCfg.phase3_pulse_weakness_ticks, 1)
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
    local bossCfg = getConfig().boss
    local px = entity:getX()
    local py = entity:getY()
    local pz = entity:getZ()
    for i = 1, bossCfg.death_fire_count do
        local angle = (i / bossCfg.death_fire_count) * math.pi * 2
        local dx = math.cos(angle) * bossCfg.death_fire_radius
        local dz = math.sin(angle) * bossCfg.death_fire_radius
        World:SetBlockState(
            math.floor(px + dx), math.floor(py), math.floor(pz + dz), "minecraft:fire")
    end

    for i = 1, #allPlayers do
        allPlayers[i]:sendTitle(
            "VICTORY!",
            "The Crystal Golem has been defeated",
            bossCfg.death_title_fade_in, bossCfg.death_title_stay, bossCfg.death_title_fade_out
        )
        allPlayers[i]:giveExperience(bossCfg.death_xp)
    end

    print("[ArcaneRPG] Crystal Golem defeated!")
end

print("[ArcaneRPG] Boss AI loaded: Crystal Golem (3 phases)")
