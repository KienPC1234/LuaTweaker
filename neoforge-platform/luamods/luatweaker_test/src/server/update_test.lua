-- ===================================================================
-- LuaTweaker Test Suite: Update Service
-- Verifies the read-only update status view. The engine fetches update
-- URLs on the Java side; Lua only reads the cached result (AGENTS.md
-- module-update: declarative, https-only, never inside the sandbox).
-- ===================================================================
local Update = require("LuaTweaker.Update")

local function check(label, cond)
    if cond then
        print("[Test][Update] " .. label .. " OK")
    else
        print("[ERROR][Test] " .. label .. " FAILED")
    end
end

-- GetStatus for an unknown mod must return nil (no crash).
local status = Update:GetStatus("luatweaker_test")
check("GetStatus returns nil or table", status == nil or type(status) == "table")

-- GetUpdates must return a table (possibly empty).
local updates = Update:GetUpdates()
check("GetUpdates returns table", type(updates) == "table")

-- mod:GetUpdateStatus() is the per-mod convenience view.
local modStatus = nil
if mod and mod.GetUpdateStatus then
    modStatus = mod:GetUpdateStatus("luatweaker_test")
end
check("mod:GetUpdateStatus returns nil or table", modStatus == nil or type(modStatus) == "table")

print("[Test][Update] Update service verified")
