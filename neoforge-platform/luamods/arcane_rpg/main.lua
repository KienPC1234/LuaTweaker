-- ================================================================
-- ARCANE RPG | Autonomous LuaMod
-- A full-featured RPG mod demonstrating LuaTweaker capabilities
-- ================================================================

-- ==== STARTUP PHASE: Content Registration ====
require(".src.startup.content")

-- ==== SERVER PHASE: Game Logic ====
require(".src.server.mana_system")
require(".src.server.skills")
require(".src.server.boss_ai")
require(".src.server.dynamic_bridge")
require(".src.server.recipes")
require(".src.server.commands")

-- ==== CLIENT PHASE: HUD & Visuals ====
require(".src.client.hud")

-- ==== MOD LIFECYCLE ====
function mod.OnEnable()
    local cfg = mod:GetConfig()
    print("[ArcaneRPG] Loaded! Max Mana: " .. cfg.mana.max_mana .. ", Skills: 4, Boss: Crystal Golem")
end

function mod.OnDisable()
    print("[ArcaneRPG] Unloading... cleaning up resources")
end

Events:Listen("OnScriptUnload", function()
    print("[ArcaneRPG] OnScriptUnload fired - cleaning up signals, timers, HUD elements")
end)
