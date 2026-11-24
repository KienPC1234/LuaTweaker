-- ================================================================
-- LuaTweaker Test: Ore radar (complex custom GLSL driven by live data)
--
-- The crystal_detector item (right-click) scans ores server-side and fires
-- the HighlightOres remote event with block positions. This script turns that
-- data into a fullscreen radar sweep: Lua computes direction/distance per ore
-- from the LOCAL player, the shader draws rings + sweep beam + blips.
-- Lua owns data and choreography; GLSL owns pixels.
-- ================================================================
local Shaders = require("LuaTweaker.Shaders")
local Players = require("LuaTweaker.Players")
local Network = require("LuaTweaker.Network")

-- A universal mod's main.lua also runs on a dedicated server, where there is
-- no client. Bail out instead of failing the whole mod load.
if Shaders:getBackend() == "unknown" then
    print("[LuaTweakerTest] Radar demo skipped: no client on this side")
    return true
end

local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

local function getRadarCfg()
    local cfg = CONFIG
    if cfg ~= nil and type(cfg) == "table" and type(cfg.radar_demo) == "table" then
        return cfg.radar_demo
    end
    return {}
end

local radarCfg = getRadarCfg()
if radarCfg.enabled == false then
    return true
end

local DURATION = tonumber(radarCfg.duration_seconds) or 8.0
local STRENGTH = tonumber(radarCfg.strength) or 0.85
local MAX_DIST = tonumber(radarCfg.max_dist) or 24.0

-- Complex custom pass: aspect-correct radar disc, animated sweep beam with
-- trail, range rings, cross hairs, and up to 3 directional blips.
-- Engine provides: DiffuseSampler, Time, ScreenSize, texCoord, fragColor.
local RADAR_BODY = [[
uniform vec3 BlipA;
uniform vec3 BlipB;
uniform vec3 BlipC;
uniform float Count;
uniform float Strength;
float ringGlow(vec2 p, float r, float w) {
    return 1.0 - smoothstep(0.0, w, abs(length(p) - r));
}
float blipDot(vec2 p, vec2 c, float r) {
    return 1.0 - smoothstep(r * 0.3, r, length(p - c));
}
void main() {
    vec4 base = texture(DiffuseSampler, texCoord);
    vec2 aspect = vec2(ScreenSize.x / ScreenSize.y, 1.0);
    vec2 p = (texCoord - 0.5) * 2.0 * aspect;
    float outer = min(aspect.x, 1.0) * 0.9;
    float r = length(p) / outer;
    if (r > 1.0) {
        fragColor = base;
        return;
    }
    vec3 col = base.rgb * 0.45 + vec3(0.0, 0.06, 0.02);
    col += vec3(0.0, 0.35, 0.12) * (ringGlow(p, outer * 0.33, 0.004) + ringGlow(p, outer * 0.66, 0.004) + ringGlow(p, outer * 0.99, 0.006));
    col += vec3(0.0, 0.18, 0.06) * ((1.0 - smoothstep(0.0, 0.004, abs(p.x))) + (1.0 - smoothstep(0.0, 0.004, abs(p.y))));
    float ang = atan(p.y, p.x) / 6.2831853 + 0.5;
    float sweep = fract(Time * 0.2);
    float dd = abs(ang - sweep);
    dd = min(dd, 1.0 - dd);
    float beam = 1.0 - smoothstep(0.0, 0.08, dd);
    float trail = mod(sweep - ang + 1.0, 1.0);
    trail = (1.0 - trail) * (1.0 - trail) * (1.0 - smoothstep(0.45, 0.5, trail));
    col += vec3(0.1, 0.9, 0.3) * (beam * 0.8 + trail * 0.4) * (1.0 - smoothstep(0.9, 1.0, r));
    if (Count > 0.5) {
        col += vec3(1.0, 0.3, 0.1) * blipDot(p, BlipA.xy * BlipA.z * outer * 0.85, 0.035);
    }
    if (Count > 1.5) {
        col += vec3(1.0, 0.3, 0.1) * blipDot(p, BlipB.xy * BlipB.z * outer * 0.85, 0.035);
    }
    if (Count > 2.5) {
        col += vec3(1.0, 0.3, 0.1) * blipDot(p, BlipC.xy * BlipC.z * outer * 0.85, 0.035);
    }
    float edge = 1.0 - smoothstep(0.96, 1.0, r);
    fragColor = vec4(mix(base.rgb, col, edge * Strength), base.a);
}
]]

-- Registration runs the register-time lint: unbalanced delimiters,
-- redeclared engine names and GLSL1-isms fail HERE with line numbers,
-- while the author is still looking at this file.
Shaders:registerGlslPass("radar_sweep", RADAR_BODY)

local function getPlayerTransform()
    local player = Players.LocalPlayer
    if player == nil and type(Players.GetLocalPlayer) == "function" then
        player = Players:GetLocalPlayer()
    end
    if player == nil then
        return nil
    end
    local pos = player.Position
    if pos == nil or pos.X == nil then
        return nil
    end
    local yaw = tonumber(player.Yaw)
    if yaw == nil and type(player.getYaw) == "function" then
        yaw = tonumber(player:getYaw())
    end
    return pos.X, pos.Y, pos.Z, yaw or 0.0
end

local highlightEvent = Network:GetOrCreateRemoteEvent("HighlightOres")
highlightEvent.OnClientEvent:Connect(function(results)
    if type(results) ~= "table" or #results == 0 then
        return
    end
    local px, _, pz, yawDeg = getPlayerTransform()
    if px == nil then
        return
    end
    -- Nearest-first, cheapest math before any vec uniform writes.
    local ranked = {}
    for i = 1, #results do
        local b = results[i]
        if type(b) == "table" then
            local bx = b.x or b.X
            local bz = b.z or b.Z
            if bx ~= nil and bz ~= nil then
                local dx = (tonumber(bx) or 0) + 0.5 - px
                local dz = (tonumber(bz) or 0) + 0.5 - pz
                ranked[#ranked + 1] = { dx = dx, dz = dz, dist2 = dx * dx + dz * dz }
            end
        end
    end
    if #ranked == 0 then
        return
    end
    table.sort(ranked, function(a, b) return a.dist2 < b.dist2 end)

    -- Rotate world XZ offset into player-facing radar coords (+Y forward, +X right).
    local yawRad = math.rad(yawDeg)
    local fwdX, fwdZ = -math.sin(yawRad), math.cos(yawRad)
    local rightX, rightZ = -math.cos(yawRad), -math.sin(yawRad)

    -- Compute everything first: every uniform write requires an ACTIVE pass, so
    -- the pass must be activated before the first setPassUniformVec call.
    local names = { "BlipA", "BlipB", "BlipC" }
    local blips = {}
    for i = 1, math.min(3, #ranked) do
        local hit = ranked[i]
        local dist = math.sqrt(hit.dist2)
        if dist < 0.25 then
            blips[#blips + 1] = { 0.0, 0.0, 0.0 }
        else
            local rx = (hit.dx * rightX + hit.dz * rightZ) / dist
            local ry = (hit.dx * fwdX + hit.dz * fwdZ) / dist
            local normDist = math.min(dist / MAX_DIST, 1.0)
            blips[#blips + 1] = { rx, ry, normDist }
        end
    end
    if #blips == 0 then
        return
    end
    Shaders:activatePass("radar_sweep")
    for i = 1, #blips do
        Shaders:setPassUniformVec(names[i], blips[i])
    end
    Shaders:setPassUniform("Count", #blips)
    Shaders:setPassUniform("Strength", STRENGTH)
    if type(task) == "table" and type(task.delay) == "function" then
        task.delay(DURATION, function()
            if Shaders:getActivePass() == "radar_sweep" then
                Shaders:deactivatePass()
            end
        end)
    end
end)

print("[LuaTweakerTest] Radar demo loaded: right-click the Crystal Detector, watch the sweep")
