-- ================================================================
-- LuaTweaker Test Suite | Autonomous LuaMod
-- A single standardized mod exercising every engine capability so
-- regressions surface as clear, single-failure test logs:
--   * dimension (Crystal Realm + Skylands) terrain/biome/picker/spawn
--   * network RemoteEvents (StaffCastSkill / StaffSwapSkill)
--   * mana system + skills + boss AI (config-driven)
--   * DynamicJavaProxy low-level bridge patterns
--   * recipes (shaped/smelting/shapeless)
--   * server commands
--   * client HUD
-- ================================================================

-- ==== STARTUP PHASE: Content Registration ====
require(".src.startup.content")
require(".src.startup.dimension")

-- ==== SERVER PHASE: Game Logic ====
require(".src.server.mana_system")
require(".src.server.skills")
require(".src.server.boss_ai")
require(".src.server.network_test")
require(".src.server.storage_test")
require(".src.server.events_test")
require(".src.server.math_test")
require(".src.server.world_test")
require(".src.server.loot_test")
require(".src.server.worldgen_test")
require(".src.server.structures_test")
require(".src.server.noise_test")
require(".src.server.interception_test")
require(".src.server.update_test")
require(".src.server.dynamic_bridge")
require(".src.server.recipes")
require(".src.server.commands")
require(".src.server.multiblock_machine")

-- ==== CLIENT PHASE: HUD & Visuals ====
require(".src.client.hud")

-- ==== MOD LIFECYCLE ====
function mod.OnEnable()
    local cfg = mod:GetConfig()
    local skillCount = 0
    if cfg and cfg.skills then
        for _ in pairs(cfg.skills) do skillCount = skillCount + 1 end
    end
    print("[LuaTweakerTest] Loaded! Max Mana: "
        .. (cfg and cfg.mana and cfg.mana.max_mana or "?")
        .. ", Skills: " .. skillCount .. ", Boss: Crystal Golem")
end

function mod.OnDisable()
    print("[LuaTweakerTest] Unloading... cleaning up resources")
end

Events:Listen("OnScriptUnload", function()
    print("[LuaTweakerTest] OnScriptUnload fired - cleaning up signals, timers, HUD elements")
end)
