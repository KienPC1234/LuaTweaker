-- ===================================================================
-- Roblox Expanded Property Test Script (Item, Block, Entity Properties)
-- Demonstrates get & set operations on Damage, Hardness, Health, Velocity, OnFire, etc.
-- ===================================================================

print("Initializing PropertyExpansionTest.lua...")

-- 1. ENTITY PROPERTY MANIPULATION
local EntityService = Mod:GetService("EntityService")

EntityService.EntitySpawned:Connect(function(entity)
    print("[PropertyTest] Inspecting spawned entity: " .. entity.Name .. " (" .. entity.Type .. ")")

    -- Getters
    print("  Health: " .. tostring(entity.Health) .. " / " .. tostring(entity.MaxHealth))
    print("  IsAlive: " .. tostring(entity.IsAlive) .. ", IsOnFire: " .. tostring(entity.IsOnFire))
    print("  IsSneaking: " .. tostring(entity.IsSneaking) .. ", IsSprinting: " .. tostring(entity.IsSprinting))

    -- Setters
    entity.CustomName = "§6[Boss] " .. entity.Name
    entity.MaxHealth = 100
    entity.Health = 100
    entity.IsOnFire = false
    entity.Velocity = Vector3.new(0, 0.8, 0) -- Super jump boost!

    print("[PropertyTest] Updated entity " .. entity.CustomName .. " properties successfully!")
end)

-- 2. BLOCK PROPERTY MANIPULATION
local Workspace = Mod:GetService("Workspace")

local block = Workspace:GetBlock(Vector3.new(0, 64, 0))
if block then
    print("[PropertyTest] Block at (0,64,0): ID=" .. block.Id)
    print("  Hardness: " .. tostring(block.Hardness))
    print("  LightLevel: " .. tostring(block.LightLevel))
    print("  IsAir: " .. tostring(block.IsAir) .. ", IsSolid: " .. tostring(block.IsSolid) .. ", IsLiquid: " .. tostring(block.IsLiquid))

    -- Set block property (ID setter)
    block.Id = "minecraft:diamond_block"
    print("[PropertyTest] Changed block to minecraft:diamond_block")
end

print("PropertyExpansionTest.lua initialized successfully!")
