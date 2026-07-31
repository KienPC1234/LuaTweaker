-- ===================================================================
-- Attribute System & Math/String Extensions Test
-- Demonstrates math.clamp, math.lerp, string.split, SetAttribute, GetAttribute
-- ===================================================================
local Entities = require("LuaTweaker.Entities")

print("Initializing RobloxAttributeAndMathTest.lua...")

-- 1. MATH & STRING EXTENSION TESTS
local clamped = math.clamp(150, 0, 100)
print("[MathTest] math.clamp(150, 0, 100) = " .. tostring(clamped) .. " (Expected: 100)")

local lerped = math.lerp(10, 20, 0.5)
print("[MathTest] math.lerp(10, 20, 0.5) = " .. tostring(lerped) .. " (Expected: 15)")

local rounded = math.round(4.6)
print("[MathTest] math.round(4.6) = " .. tostring(rounded) .. " (Expected: 5)")

local splitParts = string.split("apple,banana,cherry", ",")
print("[StringTest] string.split count = " .. tostring(#splitParts) .. " (Expected: 3)")

local trimmed = string.trim("   Hello LuaTweaker!   ")
print("[StringTest] string.trim = '" .. trimmed .. "' (Expected: 'Hello LuaTweaker!')")

-- 2. REACTIVE ATTRIBUTE SYSTEM & AttributeChanged SIGNAL
Entities.EntitySpawned:Connect(function(entity)
    print("[AttributeTest] Connecting AttributeChanged listener to entity: " .. entity.Name)

    entity.AttributeChanged:Connect(function(attrName, attrValue)
        print("[AttributeChanged Event Fired] Entity " .. entity.Name .. " -> " .. attrName .. " = " .. tostring(attrValue))
    end)

    entity:SetAttribute("SuperPower", "Fly")
    entity:SetAttribute("ShieldLevel", "500")

    print("[AttributeTest] GetAttribute('SuperPower') = " .. tostring(entity:GetAttribute("SuperPower")))
end)

print("RobloxAttributeAndMathTest.lua initialized successfully!")
