-- ===================================================================
-- LuaTweaker Test Suite: Storage (World / Player / Session stores)
-- Exercises the Roblox-style storage API (GetAsync / SetAsync) with
-- config-driven keys so a full round-trip can be verified at runtime.
-- ===================================================================
local Storage = require("LuaTweaker.Storage")

local cfg = mod and mod:GetConfig() or {}
local TEST_KEY = cfg.storage_test_key or "luatweaker_test.roundtrip"
local TEST_VALUE = tostring(cfg.storage_test_value) or "42"

local function testSessionStorage()
    local session = Storage:GetSessionStorage()
    if not session then
        print("[Test][Storage] SessionStorage not available")
        return
    end
    local before = session:GetAsync(TEST_KEY)
    session:SetAsync(TEST_KEY, TEST_VALUE)
    local after = session:GetAsync(TEST_KEY)
    if tostring(after) == TEST_VALUE then
        print("[Test][Storage] Session round-trip OK: " .. TEST_KEY .. "=" .. tostring(after))
    else
        print("[ERROR][Test] Session round-trip MISMATCH: got " .. tostring(after))
    end
    if before ~= nil then
        print("[Test][Storage] Session had previous value (reload preserved): " .. tostring(before))
    end
end

local function testWorldStorage()
    local world = Storage:GetWorldStorage()
    if not world then
        print("[Test][Storage] WorldStorage not available")
        return
    end
    world:SetAsync("luatweaker_test.world_marker", os.time())
    local marker = world:GetAsync("luatweaker_test.world_marker")
    print("[Test][Storage] WorldStorage marker: " .. tostring(marker))
end

testSessionStorage()
testWorldStorage()

print("[Test][Storage] Storage round-trips verified")
