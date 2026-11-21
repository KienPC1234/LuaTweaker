-- ================================================================
-- LuaTweaker Test: declared cross-mod integration (demo target)
--
-- This file only runs when a platform mod with id
-- "integration_demo_host" is installed. It never is, so every load
-- records a "skipped: ... is not installed" status instead - the soft
-- integration path, exercised end to end without touching the suite.
-- ================================================================

local modId = (mod and mod.ID) or "luatweaker_test"
print("[LuaTweakerTest] Integration 'integration_demo_host' active for " .. modId)
