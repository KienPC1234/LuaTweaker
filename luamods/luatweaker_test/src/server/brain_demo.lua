-- ================================================================
-- LuaTweaker Test: Brain behavior-tree demo (crystal wisp greeter)
--
-- A reactive behavior tree ticked from ServerTick. Priority runs LEFT:
--   survive (flee when hurt) -> greet (face + halo when a guest is near)
--   -> idle (occasional hop). Because the root is re-evaluated every tick,
--   a hurt wisp aborts greeting/patting mid-RUNNING and flees immediately.
--
-- Perception (one scan per sense interval, cached on the blackboard) is
-- kept OUT of the tree: nodes only read facts. Cooldown/Timeout run on
-- ent:getGameTime() (tick clock), never the wall clock.
-- ================================================================
local Brain = require("LuaTweaker.Brain")
local Events = require("LuaTweaker.Events")
local Players = require("LuaTweaker.Players")
local World = require("LuaTweaker.World")

local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

local function getDemoCfg()
    local cfg = CONFIG
    if cfg ~= nil and type(cfg) == "table" and type(cfg.brain_demo) == "table" then
        return cfg.brain_demo
    end
    return {}
end

local demoCfg = getDemoCfg()
local SCAN_RADIUS = tonumber(demoCfg.scan_radius) or 32.0
local SENSE_TICKS = math.max(1, math.floor(tonumber(demoCfg.sense_interval_ticks) or 10))
local GREET_RADIUS = tonumber(demoCfg.greet_radius) or 6.0
local GREET_COOLDOWN = math.max(1, math.floor(tonumber(demoCfg.greet_cooldown_ticks) or 200))
local FLEE_FRACTION = tonumber(demoCfg.flee_hp_fraction) or 0.3
local FLEE_RADIUS = tonumber(demoCfg.flee_radius) or 8.0
local FLEE_TIMEOUT = math.max(1, math.floor(tonumber(demoCfg.flee_timeout_ticks) or 100))
local HOP_COOLDOWN = math.max(1, math.floor(tonumber(demoCfg.hop_cooldown_ticks) or 60))
-- Behavior trees stay smooth at 10Hz; full 20Hz re-evaluation doubles the
-- InRange/distanceTo Java calls for zero gameplay gain (config brain_demo.tick_interval_ticks).
local TICK_INTERVAL = math.max(1, math.floor(tonumber(demoCfg.tick_interval_ticks) or 2))
local SENSE_RADIUS = math.max(GREET_RADIUS, FLEE_RADIUS)

local registry = Brain.Registry(64)

-- Subtree builders: plain Lua functions returning node tables, reused per type.
local function buildFleeAction()
    local flee = Brain.Action("flee", function(ctx)
        local ent = ctx.ent
        local threat = ctx.bb:Get("threat")
        if threat == nil or not Brain.InRange(ent, threat, FLEE_RADIUS) then
            return Brain.SUCCESS
        end
        if not ent:isNavigating() then
            local dx = ent:getX() - threat:getX()
            local dz = ent:getZ() - threat:getZ()
            local len = math.sqrt(dx * dx + dz * dz)
            if len < 0.001 then
                dx = 0
                dz = 1
                len = 1
            end
            ent:moveTo(
                ent:getX() + dx / len * FLEE_RADIUS,
                ent:getY(),
                ent:getZ() + dz / len * FLEE_RADIUS,
                1.2)
        end
        if ctx.now % 4 == 0 then
            ent:spawnParticle("minecraft:end_rod", 2, 0.2)
        end
        return Brain.RUNNING
    end)
    return Brain.Timeout("flee_cap", flee, FLEE_TIMEOUT)
end

local function buildTree()
    return Brain.Selector("wisp", {
        Brain.Sequence("survive", {
            Brain.Condition("is_hurt", function(ctx)
                return Brain.HealthFraction(ctx.ent) < FLEE_FRACTION
            end),
            buildFleeAction(),
        }),
        Brain.Sequence("greet", {
            Brain.Condition("guest_near", function(ctx)
                local guest = ctx.bb:Get("guest")
                return guest ~= nil and Brain.InRange(ctx.ent, guest, GREET_RADIUS)
            end),
            Brain.Cooldown("greet_cd", Brain.Sequence("say_hi", {
                Brain.Action("face", function(ctx)
                    local guest = ctx.bb:Get("guest")
                    if guest == nil then
                        return Brain.FAILURE
                    end
                    ctx.ent:lookAtEntity(guest)
                    return Brain.SUCCESS
                end),
                Brain.Action("halo", function(ctx)
                    local guest = ctx.bb:Get("guest")
                    if guest == nil then
                        return Brain.FAILURE
                    end
                    ctx.ent:spawnParticle("minecraft:end_rod", 4, 0.1)
                    guest:sendActionBar("The wisp hums softly...")
                    return Brain.SUCCESS
                end),
            }), GREET_COOLDOWN),
        }),
        Brain.Cooldown("idle_cd", Brain.Action("hop", function(ctx)
            local ent = ctx.ent
            if ent:isOnGround() then
                ent:jump()
            end
            ent:spawnParticle("minecraft:end_rod", 1, 0.05)
            return Brain.SUCCESS
        end), HOP_COOLDOWN),
    })
end

local tree = buildTree()

local brainTickCounter = 0

Events:Listen("ServerTick", function()
    brainTickCounter = brainTickCounter + 1
    if brainTickCounter < TICK_INTERVAL then
        return
    end
    brainTickCounter = 0
    local players = Players.GetPlayers()
    if #players == 0 then
        return
    end
    for i = 1, #players do
        local player = players[i]
        local nearby = World:GetEntitiesInRadius(player, SCAN_RADIUS)
        if nearby ~= nil then
            for j = 1, #nearby do
                local ent = nearby[j]
                if ent:getType() == "luatweaker:crystal_wisp" and ent:isAlive() then
                    local now = ent:getGameTime()
                    local ctx = registry:Ctx(ent, now)
                    -- Perception step: ONE scan per interval, facts cached on the
                    -- blackboard. The tree below only reads "guest"/"threat".
                    -- Single pass: canSee() is a line-of-sight raytrace, so one
                    -- NearestVisible over the wider radius feeds both facts.
                    if ctx.bb:CooldownReady("sense", SENSE_TICKS, now) then
                        local nearest = Brain.NearestVisible(ent, players, SENSE_RADIUS)
                        if nearest ~= nil and Brain.InRange(ent, nearest, GREET_RADIUS) then
                            ctx.bb:Set("guest", nearest)
                        else
                            ctx.bb:Set("guest", nil)
                        end
                        if nearest ~= nil and Brain.InRange(ent, nearest, FLEE_RADIUS) then
                            ctx.bb:Set("threat", nearest)
                        else
                            ctx.bb:Set("threat", nil)
                        end
                    end
                    Brain.Tick(tree, ctx)
                end
            end
        end
    end
end)

print("[LuaTweakerTest] Brain demo loaded (wisp greeter):\n" .. Brain.Describe(tree))
