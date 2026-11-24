-- ================================================================
-- LuaTweaker Test: Brain pack-hunt demo (crystal beasts)
--
-- One shared Squad blackboard ("huntpack") coordinates the whole pack:
-- the alpha writes the focus target, members read it. Per beast:
--   survive (flee when mauled) -> Utility { summon | bite | press | regroup }
-- Utility scores are recomputed every tick from bb facts; Parallel lets the
-- press branch move legs and watch head at the same time; Latch fires the
-- alpha's summon exactly once per fight.
-- ================================================================
local Brain = require("LuaTweaker.Brain")
local Events = require("LuaTweaker.Events")
local Players = require("LuaTweaker.Players")
local World = require("LuaTweaker.World")

local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

local function getHuntCfg()
    local cfg = CONFIG
    if cfg ~= nil and type(cfg) == "table" and type(cfg.brain_hunt) == "table" then
        return cfg.brain_hunt
    end
    return {}
end

local huntCfg = getHuntCfg()
local SCAN_RADIUS = tonumber(huntCfg.scan_radius) or 32.0
local SENSE_TICKS = math.max(1, math.floor(tonumber(huntCfg.sense_ticks) or 10))
local FOCUS_RADIUS = tonumber(huntCfg.focus_radius) or 20.0
local BITE_RANGE = tonumber(huntCfg.bite_range) or 2.8
local BITE_DAMAGE = tonumber(huntCfg.bite_damage) or 4.0
local BITE_COOLDOWN = math.max(1, math.floor(tonumber(huntCfg.bite_cooldown) or 30))
local FLEE_FRACTION = tonumber(huntCfg.flee_fraction) or 0.25
local FLEE_RADIUS = tonumber(huntCfg.flee_radius) or 8.0
local FLEE_TIMEOUT = math.max(1, math.floor(tonumber(huntCfg.flee_timeout) or 100))
local SUMMON_COUNT = math.max(1, math.floor(tonumber(huntCfg.summon_count) or 2))
local PHASE2_RATIO = tonumber(huntCfg.phase2_ratio) or 0.6
local PHASE3_RATIO = tonumber(huntCfg.phase3_ratio) or 0.3
local CHASE_SPEED = tonumber(huntCfg.chase_speed) or 1.1
local REGROUP_RADIUS = tonumber(huntCfg.regroup_radius) or 6.0
-- Pack trees re-evaluate at 10Hz (config brain_hunt.tick_interval_ticks):
-- same pack behavior, half the per-tick distanceTo/isNavigating Java calls.
local HUNT_TICK_INTERVAL = math.max(1, math.floor(tonumber(huntCfg.tick_interval_ticks) or 2))

local BEAST_TYPE = "luatweaker:crystal_beast"
local WISP_TYPE = "luatweaker:crystal_wisp"
local SQUAD_KEY = "huntpack"

local registry = Brain.Registry(64)
local squad = Brain.Squad(SQUAD_KEY)

local function isAlpha(ctx)
    return ctx.bb:Get("uuid") == squad:Get("alpha")
end

local function packPhase(ctx)
    local fraction = Brain.HealthFraction(ctx.ent)
    if fraction < PHASE3_RATIO then
        return 3
    end
    if fraction < PHASE2_RATIO then
        return 2
    end
    return 1
end

local function buildTree()
    local bite = Brain.Sequence("bite", {
        Brain.Condition("in_bite_range", function(ctx)
            local focus = squad:Get("focus")
            return focus ~= nil and Brain.InRange(ctx.ent, focus, BITE_RANGE)
        end),
        Brain.Cooldown("bite_cd", Brain.Action("strike", function(ctx)
            local focus = squad:Get("focus")
            if focus == nil or not focus:isAlive() then
                return Brain.FAILURE
            end
            local mult = 1.0
            if isAlpha(ctx) and packPhase(ctx) >= 3 then
                mult = 2.0
            end
            ctx.ent:swingArm()
            focus:damage(BITE_DAMAGE * mult)
            return Brain.SUCCESS
        end), BITE_COOLDOWN),
    })

    local press = Brain.Sequence("press", {
        Brain.Condition("focus_visible", function(ctx)
            local focus = squad:Get("focus")
            return focus ~= nil and focus:isAlive()
                and Brain.InRange(ctx.ent, focus, FOCUS_RADIUS)
        end),
        Brain.Parallel("chase_and_watch", {
            Brain.Action("chase", function(ctx)
                local focus = squad:Get("focus")
                if focus == nil or not focus:isAlive() then
                    return Brain.FAILURE
                end
                if Brain.InRange(ctx.ent, focus, BITE_RANGE) then
                    ctx.ent:stopMoving()
                    return Brain.SUCCESS
                end
                if not ctx.ent:isNavigating() then
                    ctx.ent:moveTo(focus:getX(), focus:getY(), focus:getZ(), CHASE_SPEED)
                end
                return Brain.RUNNING
            end),
            Brain.Action("watch", function(ctx)
                local focus = squad:Get("focus")
                if focus == nil then
                    return Brain.FAILURE
                end
                ctx.ent:lookAtEntity(focus)
                return Brain.SUCCESS
            end),
        }, "all"),
    })

    local summon = Brain.Sequence("summon", {
        Brain.Condition("alpha_phase2", function(ctx)
            return isAlpha(ctx) and packPhase(ctx) >= 2
        end),
        Brain.Latch("call_pack", Brain.Action("howl", function(ctx)
            ctx.ent:playSound("minecraft:entity.ender_dragon.growl", 2.0, 0.6)
            ctx.ent:spawnParticle("minecraft:end_rod", 20, 0.5)
            for _ = 1, SUMMON_COUNT do
                ctx.ent:spawnEntity(WISP_TYPE, 2, 0, 2)
            end
            return Brain.SUCCESS
        end)),
    })

    local regroup = Brain.Action("regroup", function(ctx)
        local pos = squad:Get("alphaPos")
        if pos == nil then
            return Brain.SUCCESS
        end
        local ent = ctx.ent
        local dx = pos.x - ent:getX()
        local dz = pos.z - ent:getZ()
        if dx * dx + dz * dz < REGROUP_RADIUS * REGROUP_RADIUS then
            ent:stopMoving()
            return Brain.SUCCESS
        end
        if not ent:isNavigating() then
            ent:moveTo(pos.x, ent:getY(), pos.z, CHASE_SPEED)
        end
        return Brain.RUNNING
    end)

    return Brain.Selector("pack", {
        Brain.Sequence("survive", {
            Brain.Condition("mauled", function(ctx)
                return Brain.HealthFraction(ctx.ent) < FLEE_FRACTION
            end),
            Brain.Timeout("flee_cap", Brain.Action("flee", function(ctx)
                local focus = squad:Get("focus")
                if focus == nil or not Brain.InRange(ctx.ent, focus, FLEE_RADIUS) then
                    return Brain.SUCCESS
                end
                local ent = ctx.ent
                if not ent:isNavigating() then
                    ent:moveTo(ent:getX() + (ent:getX() - focus:getX()),
                        ent:getY(), ent:getZ() + (ent:getZ() - focus:getZ()), 1.2)
                end
                return Brain.RUNNING
            end), FLEE_TIMEOUT),
        }),
        Brain.Utility("act", {
            { score = function(ctx)
                if isAlpha(ctx) and packPhase(ctx) >= 2
                    and squad:Get("focus") ~= nil then
                    return 90
                end
                return -1
            end, node = summon },
            { score = function(ctx)
                local focus = squad:Get("focus")
                if focus ~= nil and Brain.InRange(ctx.ent, focus, BITE_RANGE) then
                    return 80
                end
                return -1
            end, node = bite },
            { score = function(ctx)
                local focus = squad:Get("focus")
                if focus ~= nil and focus:isAlive()
                    and Brain.InRange(ctx.ent, focus, FOCUS_RADIUS) then
                    return 50
                end
                return -1
            end, node = press },
            { score = function(_) return 10 end, node = regroup },
        }),
    })
end

local tree = buildTree()

local huntTickCounter = 0

-- Forward declaration: defined below the Listen block; the closure only runs
-- after the whole file loads, but the explicit decl avoids use-before-define.
local tickBeast

Events:Listen("ServerTick", function()
    huntTickCounter = huntTickCounter + 1
    if huntTickCounter < HUNT_TICK_INTERVAL then
        return
    end
    huntTickCounter = 0
    local players = Players.GetPlayers()
    if #players == 0 then
        return
    end
    for i = 1, #players do
        local nearby = World:GetEntitiesInRadius(players[i], SCAN_RADIUS)
        if nearby ~= nil then
            for j = 1, #nearby do
                local ent = nearby[j]
                if ent:getType() == BEAST_TYPE and ent:isAlive() then
                    tickBeast(ent, players)
                end
            end
        end
    end
end)

-- Per-beast sense + tree tick, extracted so the scan loop stays flat.
tickBeast = function(ent, players)
    local now = ent:getGameTime()
    local ctx = registry:Ctx(ent, now)
    ctx.bb:Set("uuid", ent:getUuid())
    if ctx.bb:CooldownReady("sense", SENSE_TICKS, now) then
        -- Alpha claim: first live beast to tick owns the pack.
        local alphaEnt = squad:Get("alphaEnt")
        if alphaEnt == nil or not alphaEnt:isAlive() then
            squad:Set("alphaEnt", ent)
            squad:Set("alpha", ent:getUuid())
        end
        -- Shared focus: whoever sees prey reports it; the dead are dropped.
        local focus = squad:Get("focus")
        if focus ~= nil and not focus:isAlive() then
            squad:Set("focus", nil)
            focus = nil
        end
        local seen = Brain.NearestVisible(ent, players, FOCUS_RADIUS)
        if seen ~= nil then
            squad:Set("focus", seen)
        end
        if ctx.bb:Get("uuid") == squad:Get("alpha") then
            squad:Set("alphaPos",
                { x = ent:getX(), y = ent:getY(), z = ent:getZ() })
        end
    end
    Brain.Tick(tree, ctx)
end

print("[LuaTweakerTest] Brain hunt demo loaded (beast pack):\n" .. Brain.Describe(tree))
