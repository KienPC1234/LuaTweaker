-- ===================================================================
-- Roblox Custom NBT & DataComponent Attribute Test Script
-- Demonstrates read & write operations on custom NBT tags & attributes on Item, Block, and Entity
-- ===================================================================

print("Initializing NbtAttributeTest.lua...")

-- 1. ENTITY CUSTOM NBT ATTRIBUTES & SYNCHRONIZATION
local EntityService = Mod:GetService("EntityService")

EntityService.EntitySpawned:Connect(function(entity)
    print("[NbtTest] Spawned entity: " .. entity.Name .. " (" .. entity.Type .. ")")

    -- Set custom attributes (stored in persistent NBT)
    entity:SetAttribute("RPGClass", "Archmage")
    entity:SetAttribute("ManaLevel", "500")

    -- Get custom attribute
    local classType = entity:GetAttribute("RPGClass")
    print("  Entity RPGClass attribute read back: " .. tostring(classType))

    -- Full NBT property read/write
    entity.Nbt = "{Mana: 500, CustomPowers: ['Fireball', 'Teleport']}"
    print("  Entity NBT read back: " .. tostring(entity.Nbt))
end)

-- 2. ITEM STACK NBT CUSTOM_DATA ATTRIBUTES
local function testItemNbt(player)
    if player then
        local item = player:GetHeldItem()
        if item then
            print("[NbtTest] Held item ID: " .. item.Id)

            -- Set custom NBT attribute via DataComponents.CUSTOM_DATA
            item:SetAttribute("MagicPower", "9000")
            print("  Item MagicPower attribute: " .. tostring(item:GetAttribute("MagicPower")))

            -- Direct NBT property write
            item.Nbt = "{CustomRarity: 'Legendary', Soulbound: true}"
            print("  Item NBT read back: " .. tostring(item.Nbt))
        end
    end
end

-- 3. BLOCK ENTITY NBT ATTRIBUTES & CLIENT RENDERER SYNC
local Workspace = Mod:GetService("Workspace")

local block = Workspace:GetBlock(Vector3.new(100, 64, 200))
if block then
    print("[NbtTest] Inspecting block at (100,64,200): ID=" .. block.Id)

    -- Set custom attribute on BlockEntity
    block:SetAttribute("Owner", "Player1")
    print("  Block Owner attribute: " .. tostring(block:GetAttribute("Owner")))

    -- Set full NBT on BlockEntity (triggers level.sendBlockUpdated)
    block.Nbt = "{CustomLock: true, Passcode: 1234}"
    print("  Block NBT read back: " .. tostring(block.Nbt))
end

print("NbtAttributeTest.lua initialized successfully!")
