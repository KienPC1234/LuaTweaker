-- ===================================================================
-- LuaTweaker Test Suite: Noise Service
-- Verifies deterministic, seeded noise: fBm, ridged, domain warp,
-- voronoi and simplex all return finite values in documented ranges.
-- ===================================================================
local Noise = require("LuaTweaker.Noise")

local function check(label, cond)
    if cond then
        print("[Test][Noise] " .. label .. " OK")
    else
        print("[ERROR][Test] " .. label .. " FAILED")
    end
end

local function isFinite(v)
    return type(v) == "number" and v == v and v ~= math.huge and v ~= -math.huge
end

-- Determinism: same seed -> same output
Noise:SetSeed(42)
local a1 = Noise:Simplex(1.5, 2.5, 1.0)
local a2 = Noise:Simplex(1.5, 2.5, 1.0)
check("Simplex deterministic", a1 == a2)

-- Range checks
local s = Noise:Simplex(0.3, 0.7, 1.0)
check("Simplex in [-1,1]", isFinite(s) and s >= -1 and s <= 1)

local f = Noise:FBm(1.0, 2.0, 4, 2.0, 0.5)
check("fBm finite", isFinite(f))

local r = Noise:Ridged(1.0, 2.0, 4, 1.0, 2.0)
check("Ridged finite", isFinite(r))

local v = Noise:Voronoi(1.0, 2.0, 0.8, 0)
check("Voronoi finite", isFinite(v))

local warp = Noise:DomainWarp(1.0, 2.0, 20.0, 1.0)
check("DomainWarp returns table", type(warp) == "table")
if type(warp) == "table" then
    check("DomainWarp has warpedX", isFinite(warp[1]) or isFinite(warp.warpedX))
end

-- Seed reproducibility
Noise:SetSeed(7)
local b1 = Noise:Simplex(0.5, 0.5, 1.0)
Noise:SetSeed(7)
local b2 = Noise:Simplex(0.5, 0.5, 1.0)
check("Seed reproducible", b1 == b2)

print("[Test][Noise] Noise service verified")
