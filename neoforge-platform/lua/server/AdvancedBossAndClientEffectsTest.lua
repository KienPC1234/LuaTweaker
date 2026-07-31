-- ===================================================================
-- Roblox Advanced Boss & Client Visual Effects Test Script
-- Demonstrates custom projectile registration, Camera Shake, Screen Flash, Particle Emitters, and Sound Playback
-- ===================================================================

print("Initializing AdvancedBossAndClientEffectsTest.lua...")

-- 1. REGISTER CUSTOM PROJECTILE (Ruby Orb with Explosion Power & Flame Particles)
local startup = Mod:GetService("Content") or Mod:GetService("startup")
if startup and startup.registerProjectile then
    startup:registerProjectile("luatweaker:ruby_orb", {
        damage = 30,
        explosionPower = 2,
        trailParticle = "minecraft:flame",
        onHitEffect = "minecraft:wither",
        gravity = false,
        homing = true
    })
    print("[Content] Registered Custom Projectile: luatweaker:ruby_orb")
end

-- 2. ADVANCED CLIENT VISUAL & AUDIO EFFECTS
local Camera = Mod:GetService("Camera") or _G.Camera
local ClientEffects = Mod:GetService("ClientEffects")
local EntityService = Mod:GetService("EntityService")

EntityService.EntitySpawned:Connect(function(entity)
    if entity.Type == "minecraft:zombie" then
        print("[ClientTest] Zombie spawned, triggering screen shake & boss entrance flash!")

        -- Camera Shake (Intensity: 2.5, Duration: 1.2 seconds)
        if Camera and Camera.Shake then
            Camera:Shake(2.5, 1.2)
        end

        -- Screen Flash Red Overlay (Duration: 0.5s)
        if ClientEffects and ClientEffects.FlashScreen then
            ClientEffects:FlashScreen("#FF0000", 0.5)
            ClientEffects:PlaySound("minecraft:entity.wither.spawn", 1.0, 0.8)
            ClientEffects:SpawnParticle("minecraft:flame", entity.X, entity.Y + 1.0, entity.Z, 0, 0.2, 0)
        end
    end
end)

print("AdvancedBossAndClientEffectsTest.lua initialized successfully!")
