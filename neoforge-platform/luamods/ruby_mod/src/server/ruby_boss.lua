-- ===================================================================
-- Roblox Custom Boss AI & Skill System Test Script
-- Recolor Zombie Boss with Dash Skill, Fireball Skill, Phase Transitions
-- ===================================================================
local Entities = require("LuaTweaker.Entities")
local AIGoals  = require("LuaTweaker.AIGoals")
local Task     = require("LuaTweaker.Task")
local Vector3  = require("LuaTweaker.Math.Vector3")

print("Initializing RubyBossTest.lua...")

Entities.EntitySpawned:Connect(function(entity)
    if entity.Type == "luatweaker:ruby_boss" then
        print("[Boss System] Initializing Pure Custom AI for " .. entity.Type .. "...")

        -- 1. CLEAR ALL DEFAULT VANILLA AI GOALS (100% Pure Custom Lua AI)
        AIGoals:clearGoals(entity)

        -- Target nearest player & revenge target
        AIGoals:addNearestAttackableTargetGoal(entity, 1, "player")
        AIGoals:addHurtByTargetGoal(entity, 2)
        AIGoals:addMeleeAttackGoal(entity, 4, 1.25, false)

        -- 2. Custom Boss Attributes (Loaded from luaconfig/ruby_mod.json)
        local cfg = mod and mod:GetConfig()
        local bossHealth = (cfg and cfg.ruby_boss_health) or 300.0

        entity.CustomName = "§c[Ruby Overseer Boss]"
        entity.MaxHealth = bossHealth
        entity.Health = bossHealth
        entity:SetAttribute("IsBoss", "true")
        entity:SetAttribute("Phase", "1")

        -- 3. Add Custom Dash Attack Goal (Priority 2, Cooldown: 5s, Speed: 1.5)
        AIGoals:addDashGoal(entity, 2, 5.0, 1.5)

        -- 4. Add Custom Spell Cast Skill Goal (Priority 1, Cooldown: 8s, Range: 15 blocks)
        AIGoals:addSkillGoal(entity, 1, "RubyFireball", 8.0, 15.0, function(target)
            print("[Ruby Boss Skill] Playing 'spell_cast' animation & firing projectiles!")
            entity:SendMessage("§cThe Ruby Overseer plays 'spell_cast' animation and launches projectiles!")
            entity:PlayAnimation("spell_cast", 1.5, 0.2)
            entity:ShootProjectile("minecraft:dragon_fireball", 1.8, 0.0)
            if target then
                entity:ShootProjectileAt("minecraft:wither_skull", target, 2.0)
            end
        end)

        -- 5. Add Slam Attack Goal (Priority 3, Cooldown: 6s, Range: 4 blocks)
        AIGoals:addSkillGoal(entity, 3, "SlamAttack", 6.0, 4.0, function(target)
            print("[Ruby Boss Skill] Playing 'slam_attack' animation & ground pounding!")
            entity:PlayAnimation("slam_attack", 1.8, 0.1)
            if target then
                target.Health = target.Health - 20
                target.Velocity = Vector3.new(0, 0.8, 0)
            end
        end)

        -- 6. Reactive Phase 2 Transformation (Roar Animation + Enrage)
        Task:delay(2.0, function()
            if entity.IsAlive and entity.Health < 250 and entity:GetAttribute("Phase") == "1" then
                entity:SetAttribute("Phase", "2")
                entity.CustomName = "§4[ENRAGED] Ruby Overseer Boss"
                print("[Phase 2] Playing 'roar_skill' animation!")
                entity:PlayAnimation("roar_skill", 1.2, 0.2)
                entity.Velocity = Vector3.new(0, 1.2, 0)
                entity:SendMessage("§4[ENRAGED] The Ruby Overseer plays 'roar_skill' animation and enters Phase 2!")
            end
        end)
    end
end)

print("RubyBossTest.lua initialized successfully with Pure Custom AI & Animation Suite!")
