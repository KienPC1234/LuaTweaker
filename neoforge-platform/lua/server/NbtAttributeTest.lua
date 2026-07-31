-- ===================================================================
-- NBT & Attribute Test Script
-- Demonstrates read & write operations on custom NBT tags & attributes on Item, Block, and Entity
-- ===================================================================
local Entities = require("LuaTweaker.Entities")
local World    = require("LuaTweaker.World")
local Vector3  = require("LuaTweaker.Math.Vector3")

print("Initializing NbtAttributeTest.lua...")

-- 1. ENTITY CUSTOM NBT ATTRIBUTES
Entities.EntitySpawned:Connect(function(entity)
    print("[NbtTest] Spawned entity: " .. entity.Name .. " (" .. entity.Type .. ")")

    entity:SetAttribute("RPGClass", "Archmage")
    entity:SetAttribute("ManaLevel", "500")

    local classType = entity:GetAttribute("RPGClass")
    print("  Entity RPGClass attribute read back: " .. tostring(classType))

    entity.Nbt = "{Mana: 500, CustomPowers: ['Fireball', 'Teleport']}"
    print("  Entity NBT read back: " .. tostring(entity.Nbt))
end)

-- 2. BLOCK ENTITY NBT ATTRIBUTES
local block = World:GetBlock(Vector3.new(100, 64, 200))
if block then
    print("[NbtTest] Inspecting block at (100,64,200): ID=" .. block.Id)

    block:SetAttribute("Owner", "Player1")
    print("  Block Owner attribute: " .. tostring(block:GetAttribute("Owner")))

    block.Nbt = "{CustomLock: true, Passcode: 1234}"
    print("  Block NBT read back: " .. tostring(block.Nbt))
end

print("NbtAttributeTest.lua initialized successfully!")
