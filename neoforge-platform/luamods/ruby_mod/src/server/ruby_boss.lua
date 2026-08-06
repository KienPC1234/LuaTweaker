-- ===================================================================
-- Roblox Custom Boss AI & Skill System Test Script
-- Recolor Zombie Boss with Dash Skill, Fireball Skill, Phase Transitions
-- ===================================================================
local Entities = require("LuaTweaker.Entities")
local AIGoals  = require("LuaTweaker.AIGoals")
local Task     = require("LuaTweaker.Task")
local Vector3  = require("LuaTweaker.Math.Vector3")

print("Initializing RubyBossTest.lua...")

-- CONFIG-DRIVEN BOSS TUNING (luaconfig/ruby_mod.json — default_config.json on first launch)
local cfg = mod and mod:GetConfig() or {}
local BOSS_HEALTH = tonumber(cfg.ruby_boss_health) or 300.0
local DASH_COOLDOWN = tonumber(cfg.ruby_boss_dash_cooldown) or 5.0
local DASH_SPEED = tonumber(cfg.ruby_boss_dash_speed) or 1.5
local FIREBALL_COOLDOWN = tonumber(cfg.ruby_boss_fireball_cooldown) or 8.0
local FIREBALL_RANGE = tonumber(cfg.ruby_boss_fireball_range) or 15.0
local FIREBALL_SPEED = tonumber(cfg.ruby_boss_fireball_speed) or 1.8
local SKULL_SPEED = tonumber(cfg.ruby_boss_skull_speed) or 2.0
local SLAM_COOLDOWN = tonumber(cfg.ruby_boss_slam_cooldown) or 6.0
local SLAM_RANGE = tonumber(cfg.ruby_boss_slam_range) or 4.0
local SLAM_DAMAGE = tonumber(cfg.ruby_boss_slam_damage) or 20
local SLAM_KNOCKBACK = tonumber(cfg.ruby_boss_slam_knockback) or 0.8
local MELEE_SPEED = tonumber(cfg.ruby_boss_melee_speed) or 1.25
local PHASE2_HP_THRESHOLD = tonumber(cfg.ruby_boss_phase2_hp_threshold) or 250
local PHASE2_DELAY = tonumber(cfg.ruby_boss_phase2_delay) or 2.0
local ROAR_KNOCKBACK = tonumber(cfg.ruby_boss_roar_knockback) or 1.2

Entities.EntitySpawned:Connect(function(entity)
    -- Entity: the unified entity Lua table (method-style API + property aliases)
    if entity.Type == "luatweaker:ruby_boss" then
        -- Summoned minions are follow-mode pets: the owner's scripts decide when
        -- they may attack (marking a target), so skip the boss AI here.
        if entity:hasTag("ruby_minion") then
            print("[Boss System] Summoned minion spawned — follow mode active.")
            return
        end
        print("[Boss System] Initializing Pure Custom AI for " .. entity.Type .. "...")

        -- 1. CLEAR ALL DEFAULT VANILLA AI GOALS (100% Pure Custom Lua AI)
        AIGoals:clearGoals(entity)

        -- Target nearest player & revenge target
        AIGoals:addNearestAttackableTargetGoal(entity, 1, "player")
        AIGoals:addHurtByTargetGoal(entity, 2)
        AIGoals:addMeleeAttackGoal(entity, 4, MELEE_SPEED, false)

        -- 2. Custom Boss Attributes (Loaded from luaconfig/ruby_mod.json)
        local bossHealth = BOSS_HEALTH

        entity.CustomName = "§c[Ruby Overseer Boss]"
        entity.MaxHealth = bossHealth
        entity.Health = bossHealth
        entity:SetAttribute("IsBoss", "true")
        entity:SetAttribute("Phase", "1")

        -- 3. Add Custom Dash Attack Goal (Priority 2, Cooldown + Speed from config)
        AIGoals:addDashGoal(entity, 2, DASH_COOLDOWN, DASH_SPEED)

        -- 4. Add Custom Spell Cast Skill Goal (Priority 1, Cooldown + Range from config)
        AIGoals:addSkillGoal(entity, 1, "RubyFireball", FIREBALL_COOLDOWN, FIREBALL_RANGE, function(target)
            print("[Ruby Boss Skill] Playing 'spell_cast' animation & firing projectiles!")
            entity:SendMessage("§cThe Ruby Overseer plays 'spell_cast' animation and launches projectiles!")
            entity:PlayAnimation("spell_cast", 1.5, 0.2)
            entity:ShootProjectile("minecraft:dragon_fireball", FIREBALL_SPEED, 0.0)
            if target then
                entity:ShootProjectileAt("minecraft:wither_skull", target, SKULL_SPEED)
            end
        end)

        -- 5. Add Slam Attack Goal (Priority 3, Cooldown + Range from config)
        AIGoals:addSkillGoal(entity, 3, "SlamAttack", SLAM_COOLDOWN, SLAM_RANGE, function(target)
            print("[Ruby Boss Skill] Playing 'slam_attack' animation & ground pounding!")
            entity:PlayAnimation("slam_attack", 1.8, 0.1)
            if target then
                target.Health = target.Health - SLAM_DAMAGE
                target.Velocity = Vector3.new(0, SLAM_KNOCKBACK, 0)
            end
        end)

        -- 6. Reactive Phase 2 Transformation (Roar Animation + Enrage)
        Task:delay(PHASE2_DELAY, function()
            if entity.IsAlive and entity.Health < PHASE2_HP_THRESHOLD and entity:GetAttribute("Phase") == "1" then
                entity:SetAttribute("Phase", "2")
                entity.CustomName = "§4[ENRAGED] Ruby Overseer Boss"
                print("[Phase 2] Playing 'roar_skill' animation!")
                entity:PlayAnimation("roar_skill", 1.2, 0.2)
                entity.Velocity = Vector3.new(0, ROAR_KNOCKBACK, 0)
                entity:SendMessage("§4[ENRAGED] The Ruby Overseer plays 'roar_skill' animation and enters Phase 2!")
            end
        end)
    end
end)

print("RubyBossTest.lua initialized successfully with Pure Custom AI & Animation Suite!")
