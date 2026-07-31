-- ===================================================================
-- World Interaction & properties test script
-- ===================================================================
local World    = require("LuaTweaker.World")
local Entities = require("LuaTweaker.Entities")
local Vector3  = require("LuaTweaker.Math.Vector3")

print("Loading world interaction script...")

-- 1. Block Query and Manipulation
local block = World:GetBlock(Vector3.new(100, 64, 200))
if block then
    print("Block at (100, 64, 200) type ID: " .. block.Id)
    print("Block coordinates: Position=" .. tostring(block.Position))

    block.Id = "minecraft:gold_block"
    print("Successfully updated block ID to: " .. block.Id)
end

-- 2. Entity spawn reactive event
Entities.EntitySpawned:Connect(function(entity)
    print("Entity spawned! Name: " .. entity.Name .. ", Type: " .. entity.Type)
    print("Spawn coordinates: Position=" .. tostring(entity.Position))
    print("Entity initial health: " .. entity.Health)

    if entity.Health > 10 then
        entity.Health = entity.Health + 10.0
        print("Surged Entity health to: " .. entity.Health)
    end
end)

print("Interaction script loaded successfully!")
