-- ===================================================================
-- Roblox-style World Interaction & properties example script
-- ===================================================================

local Workspace = Mod:GetService("Workspace")
local EntityService = Mod:GetService("EntityService")

print("Loading Roblox-style world interaction script...")

-- Example 1: Roblox-style Block Query and Manipulation
local block = Workspace:GetBlock(Vector3.new(100, 64, 200))
if block then
    print("Block at (100, 64, 200) class name or type ID: " .. block.Id)
    print("Block coordinates: Position=" .. tostring(block.Position))
    
    -- Setter property test: set block ID (places a block of this type)
    block.Id = "minecraft:gold_block"
    print("Successfully updated block ID to: " .. block.Id)
    
    -- Break block action (simulated)
    -- block:Destroy()
end

-- Example 2: Roblox-style Entity spawn reactive event with property manipulation
EntityService.EntitySpawned:Connect(function(entity)
    print("Roblox-style Entity spawned! Name: " .. entity.Name .. ", Type: " .. entity.Type)
    print("Spawn coordinates: Position=" .. tostring(entity.Position))
    print("Entity initial health: " .. entity.Health)
    
    -- Custom setter for entity health property
    if entity.Health > 10 then
        entity.Health = entity.Health + 10.0
        print("Surged Entity health to: " .. entity.Health)
    end
end)

print("Roblox-style interaction script loaded successfully!")
