-- ================================================================
-- LuaTweaker Test: Crystal Golem Boss AI
-- Custom behavior via AIGoals + event-driven phase transitions
--
-- NOTE: EntitySpawn/EntityDeath events deliver RAW Java entity proxies that
-- do NOT expose the IEntity wrapper API (setAttribute, getType-as-string, ...).
-- To stay correct we drive everything from the ServerTick scan, whose entities
-- come from World:GetEntitiesInRadius() and ARE proper IEntity Lua tables.
--
-- TICK BUDGET: the scan runs every SCAN_INTERVAL ticks (config
-- boss.scan_interval_ticks, default 10 = 2x/s). Chance-based volleys are
-- scaled by the interval so the per-second rate is unchanged.
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

-- Scan tuning resolved ONCE at load (AGENTS.md 5.8). /lt reload re-runs this
-- file, so config edits + reload refresh these without touching the tick loop.
local bossCfgStatic = getBossCfg()
local SCAN_RADIUS = tonumber(bossCfgStatic.scan_radius) or 64.0
local SCAN_INTERVAL = math.max(1, math.floor(tonumber(bossCfgStatic.scan_interval_ticks) or 10))

-- Forward declaration: handleBossDeath is defined below but referenced from the
-- ServerTick closure above; without this it would become an implicit global.
local handleBossDeath

local scanCounter = 0

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
    entity:setAttribute("luatweaker_test.boss.phase", "1")
    entity:setAttribute("luatweaker_test.boss.death_handled", "false")
    print("[LuaTweakerTest] Crystal Golem spawned! HP: " .. bossHealth)
end

-- Single-boss tick, extracted so the scan loop stays flat (max ~3 depth).
-- Phase ratios and per-round chances are passed in (already scaled); bossCfg
-- is only read for rare one-shot actions (effects, sounds, titles).
local function tickBoss(entity, bossCfg, phase2Ratio, phase3Ratio, fireballChance, pulseChance, allPlayers)
    local phase = entity:getAttribute("luatweaker_test.boss.phase")
    if phase == nil or phase == "" then
        setupBoss(entity)
        phase = "1"
    end

    if not entity:isAlive() then
        local deathHandledAttr = entity:getAttribute("luatweaker_test.boss.death_handled")
        if deathHandledAttr ~= "true" then
            entity:setAttribute("luatweaker_test.boss.death_handled", "true")
            handleBossDeath(entity, allPlayers)
        end
        return
    end

    local health = entity:getHealth()
    local maxHealth = entity:getMaxHealth()

    -- Phase 2 at (phase2_hp_ratio * 100)% HP (config)
    if phase == "1" and health < maxHealth * phase2Ratio then
        entity:setAttribute("luatweaker_test.boss.phase", "2")
        phase = "2"
        entity:addEffect("speed", bossCfg.enrage_speed_ticks, 1)
        entity:playSound("minecraft:entity.ender_dragon.growl", 2.0, 0.5)
        entity:spawnParticle("minecraft:end_rod", 40, 1.0)
        for k = 1, #allPlayers do
            allPlayers[k]:sendActionBar("The Crystal Golem becomes ENRAGED!")
            allPlayers[k]:playSound("minecraft:entity.ender_dragon.growl", 1.0, 0.5)
        end
    end

    -- Phase 2: occasional fireball volleys
    if phase == "2" and math.random() < fireballChance then
        entity:shootProjectile("minecraft:small_fireball", bossCfg.phase2_fireball_speed, bossCfg.phase2_fireball_inaccuracy)
        entity:playSound("minecraft:entity.blaze.shoot", 1.0, 0.8)
    end

    -- Phase 3 at (phase3_hp_ratio * 100)% HP: enrage + periodic AoE pulse
    if phase == "2" and health < maxHealth * phase3Ratio then
        entity:setAttribute("luatweaker_test.boss.phase", "3")
        phase = "3"
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

    if phase == "3" and math.random() < pulseChance then
        local targets = World:GetEntitiesInRadius(entity, bossCfg.phase3_pulse_radius)
        if targets ~= nil then
            local selfUuid = entity:getUuid()
            for t = 1, #targets do
                local target = targets[t]
                if target:getUuid() ~= selfUuid and target:isLiving() then
                    target:damage(bossCfg.phase3_pulse_damage)
                    target:addEffect("weakness", bossCfg.phase3_pulse_weakness_ticks, 1)
                    target:spawnParticle("minecraft:dragon_breath", 10, 0.5)
                end
            end
        end
        entity:playSound("minecraft:entity.warden.sonic_boom", 2.0, 1.5)
    end
end

Events:Listen("ServerTick", function()
    scanCounter = scanCounter + 1
    if scanCounter < SCAN_INTERVAL then
        return
    end
    scanCounter = 0
    local allPlayers = Players.GetPlayers()
    if #allPlayers == 0 then return end

    local bossCfg = getBossCfg()
    local phase2Ratio = tonumber(bossCfg.phase2_hp_ratio) or 0.5
    local phase3Ratio = tonumber(bossCfg.phase3_hp_ratio) or 0.25
    local fireballChance = (tonumber(bossCfg.phase2_fireball_chance) or 0.01) * SCAN_INTERVAL
    local pulseChance = (tonumber(bossCfg.phase3_pulse_chance) or 0.005) * SCAN_INTERVAL
    -- Overlapping player radii scan the same boss twice; handle each once.
    local seen = {}

    for i = 1, #allPlayers do
        local nearby = World:GetEntitiesInRadius(allPlayers[i], SCAN_RADIUS)
        if nearby ~= nil then
            for j = 1, #nearby do
                local entity = nearby[j]
                if entity:getType() == "luatweaker:crystal_golem" then
                    local uuid = entity:getUuid()
                    if not seen[uuid] then
                        seen[uuid] = true
                        tickBoss(entity, bossCfg, phase2Ratio, phase3Ratio,
                            fireballChance, pulseChance, allPlayers)
                    end
                end
            end
        end
    end
end)

handleBossDeath = function(entity, allPlayers)
    local bossCfg = getBossCfg()
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

    print("[LuaTweakerTest] Crystal Golem defeated!")
end

print("[LuaTweakerTest] Boss AI loaded: Crystal Golem (3 phases)")
