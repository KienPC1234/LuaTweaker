-- ===================================================================
-- LuaTweaker Test Suite: Math (Vector3 / Color3)
-- Verifies the Roblox-style math API: construction, arithmetic, dot,
-- cross, magnitude and color helpers.
-- ===================================================================
local Vector3 = require("LuaTweaker.Math.Vector3")
local Color3 = require("LuaTweaker.Math.Color3")

local a = Vector3.new(1, 2, 3)
local b = Vector3.new(4, 5, 6)

local sum = a + b
if sum.X == 5 and sum.Y == 7 and sum.Z == 9 then
    print("[Test][Math] Vector3 add OK")
else
    print("[ERROR][Test] Vector3 add FAILED: " .. tostring(sum.X) .. "," .. tostring(sum.Y) .. "," .. tostring(sum.Z))
end

local dot = a:Dot(b)
if dot == 32 then
    print("[Test][Math] Vector3 dot OK: " .. tostring(dot))
else
    print("[ERROR][Test] Vector3 dot FAILED: got " .. tostring(dot))
end

local mag = a.Magnitude
if math.abs(mag - math.sqrt(14)) < 0.001 then
    print("[Test][Math] Vector3 magnitude OK: " .. string.format("%.3f", mag))
else
    print("[ERROR][Test] Vector3 magnitude FAILED: got " .. tostring(mag))
end

local cross = a:Cross(b)
if cross.X == -3 and cross.Y == 6 and cross.Z == -3 then
    print("[Test][Math] Vector3 cross OK")
else
    print("[ERROR][Test] Vector3 cross FAILED: "
        .. tostring(cross.X) .. "," .. tostring(cross.Y) .. "," .. tostring(cross.Z))
end

local c = Color3.fromRGB(255, 0, 128)
if c.R == 1 and c.G == 0 and math.abs(c.B - (128 / 255)) < 0.01 then
    print("[Test][Math] Color3.fromRGB OK: " .. tostring(c.R) .. "," .. tostring(c.G) .. "," .. string.format("%.3f", c.B))
else
    print("[ERROR][Test] Color3 FAILED: R=" .. tostring(c.R) .. " G=" .. tostring(c.G) .. " B=" .. tostring(c.B))
end

print("[Test][Math] Math library verified")
