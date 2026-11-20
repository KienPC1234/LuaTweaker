-- ================================================================
-- LuaTweaker Test: Shaders demo (client-side screen effects)
--
-- Runs on world entry: a small camera kick plus a letterbox intro that
-- clears itself after a few seconds. Everything goes through the Shaders
-- service (named effects + custom GLSL passes); game logic that triggers
-- effects (hurt flashes, boss intros) belongs in server scripts and reaches
-- here via Network remote events.
-- ================================================================
local Shaders = require("LuaTweaker.Shaders")

-- A universal mod's main.lua also runs on a dedicated server, where there is
-- no client. Bail out instead of failing the whole mod load.
if Shaders:getBackend() == "unknown" then
    print("[LuaTweakerTest] Shaders demo skipped: no client on this side")
    return true
end

local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

local function getDemoCfg()
    local cfg = CONFIG
    if cfg ~= nil and type(cfg) == "table" and type(cfg.shaders_demo) == "table" then
        return cfg.shaders_demo
    end
    return {}
end

local demoCfg = getDemoCfg()
if demoCfg.enabled == false then
    return true
end

local introSeconds = tonumber(demoCfg.intro_letterbox_seconds) or 4.0
local introHeight = tonumber(demoCfg.intro_height) or 0.12
local kickStrength = tonumber(demoCfg.kick_strength) or 2.0

Shaders:setEffect("kick", { strength = kickStrength, seconds = 0.4 })
Shaders:setEffect("letterbox", { height = introHeight })

-- Task:delay exists on every engine; the type check keeps this file honest
-- if the scheduler binding ever changes shape.
if type(task) == "table" and type(task.delay) == "function" then
    task.delay(introSeconds, function()
        Shaders:clearEffect("letterbox")
    end)
else
    Shaders:clearEffect("letterbox")
end

print("[LuaTweakerTest] Shaders demo loaded (backend: " .. Shaders:getBackend() .. ")")
print("  try: Shaders:setEffect('grain', { strength = 0.4 })")
print("  try: Shaders:setEffect('vignette', { strength = 0.6 })")
print("  try: Shaders:clearAll()")
