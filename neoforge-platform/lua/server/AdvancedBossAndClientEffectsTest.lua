-- ===================================================================
-- Advanced Boss & Client Visual Effects Test Script
-- Demonstrates custom projectile registration, Camera Shake, Screen Flash, Particle Emitters
-- ===================================================================
local function safeRequire(modName)
    local success, result = pcall(require, modName)
    if success then return result else return nil end
end

local Content       = safeRequire("LuaTweaker.Content")
local Camera        = safeRequire("LuaTweaker.Camera")
local ClientEffects = safeRequire("LuaTweaker.ClientEffects")
local Entities      = safeRequire("LuaTweaker.Entities")

print("Initializing AdvancedBossAndClientEffectsTest.lua...")

-- 1. REGISTER CUSTOM PROJECTILE
if Content and Content.registerProjectile then
    Content:registerProjectile("luatweaker:ruby_orb", {
        damage = 30,
        explosionPower = 2,
        trailParticle = "minecraft:flame",
        onHitEffect = "minecraft:wither",
        gravity = false,
        homing = true
    })
    print("[Content] Registered Custom Projectile: luatweaker:ruby_orb")
end

-- 2. CLIENT VISUAL & AUDIO EFFECTS
Entities.EntitySpawned:Connect(function(entity)
    if entity.Type == "minecraft:zombie" then
        print("[ClientTest] Zombie spawned, triggering screen shake & boss entrance flash!")

        if Camera and Camera.Shake then
            Camera:Shake(2.5, 1.2)
        end

        if ClientEffects and ClientEffects.FlashScreen then
            ClientEffects:FlashScreen("#FF0000", 0.5)
            ClientEffects:PlaySound("minecraft:entity.wither.spawn", 1.0, 0.8)
            ClientEffects:SpawnParticle("minecraft:flame", entity.X, entity.Y + 1.0, entity.Z, 0, 0.2, 0)
        end
    end
end)

print("AdvancedBossAndClientEffectsTest.lua initialized successfully!")
