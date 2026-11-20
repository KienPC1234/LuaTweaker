# Behavior Trees (`Brain`)

Pure-Lua behavior-tree runtime for NPC decision-making, loaded in every engine
as the `Brain` global (`require("LuaTweaker.Brain")`). No Java service, no
registration: build node tables once at script load, tick them per entity from
`onTick` or `ServerTick` with a per-entity blackboard from `Brain.Registry`.

Java stays a thin bridge (move/look/jump/sight primitives); ALL decision logic
lives in Lua, per the low-level API philosophy.

---

## 1. Tick model: statuses and evaluation order

Every tick re-runs the tree from the **root**, children left to right. Each node
returns one status:

| Status | Meaning |
| :--- | :--- |
| `Brain.SUCCESS` (`"success"`) | Node finished; composites continue/accept. |
| `Brain.FAILURE` (`"failure"`) | Node failed; composites stop/reject. |
| `Brain.RUNNING` (`"running"`) | Still working; resume next tick. |

Because the root is re-evaluated each tick, a **higher-priority branch on the
LEFT automatically preempts** a `RUNNING` branch on the right. There is no
separate abort wiring: `Selector` naturally jumps left the tick the left
condition turns true. Long `RUNNING` actions must therefore be **idempotent**:
re-issuing `ent:moveTo(...)` every tick re-paths constantly, so guard it with
`if not ent:isNavigating() then ent:moveTo(...) end` and return `RUNNING`
until arrival.

```lua
local Brain = require("LuaTweaker.Brain")

local tree = Brain.Selector("guard", {          -- (?) try left to right
    Brain.Sequence("survive", {                 -- (->) every step must hold
        Brain.Condition("dying", function(ctx)
            return Brain.HealthFraction(ctx.ent) < 0.2
        end),
        Brain.Action("flee", function(ctx)      -- leaf: real game effect
            -- ... moveTo away, return Brain.RUNNING until safe ...
        end),
    }),
    Brain.Action("patrol", function(ctx)        -- idle fallback, rightmost
        -- ... waypoint walk ...
    end),
})

local registry = Brain.Registry(64)
local Players = require("LuaTweaker.Players")
local World = require("LuaTweaker.World")
Events:Listen("ServerTick", function()
    for _, player in ipairs(Players.GetPlayers()) do
        for _, ent in ipairs(World:GetEntitiesInRadius(player, 32.0)) do
            if ent:getType() == "mymod:sentry" and ent:isAlive() then
                local ctx = registry:Ctx(ent, ent:getGameTime())
                Brain.Tick(tree, ctx)
            end
        end
    end
end)
```

## 2. Blackboard and context

`ctx = { ent, bb, now, trace }`. The blackboard (`Brain.Blackboard()`) is
**transient Lua-side scratch memory** — unlike `entity:setCustomData` (persistent
NBT, written to disk) it costs nothing per tick and dies with the Registry
entry. Perception writes facts; the tree only reads them:

```lua
if ctx.bb:CooldownReady("sense", 10, now) then   -- ONE scan per 10 ticks
    ctx.bb:Set("target", Brain.NearestVisible(ent, players, 24.0))
end
```

Blackboard API: `Get(key, default)`, `Set(key, value)` (nil removes),
`Inc(key, [n])`, `Stamp(key, now)`, `Last(key)`, `TimeSince(key, now)`,
`CooldownReady(key, intervalTicks, now)` (true at most once per interval).

`Brain.Registry(maxEntries)` hands each entity UUID its own blackboard with LRU
eviction (dead/unloaded mobs age out past the cap). The returned `ctx` table is
**reused per entity** (zero allocation on hot ticks); set `ctx.trace = {}` before
`Tick` to record the visited `"name=status"` path for debugging.

Event hooks bridge into the tree through the same blackboard:

```lua
:onHurt(function(ent, amount, attacker)
    registry:Ctx(ent, ent:getGameTime()).bb:Set("hurtAt", ent:getGameTime())
    return amount
end)
-- tree: Brain.Condition("recently_hit", function(ctx)
--     local t = ctx.bb:TimeSince("hurtAt", ctx.now)
--     return t ~= nil and t < 100
-- end)
```

## 3. Node catalog

**Composites** (children run left to right, reactive — no memory of past ticks):

| Constructor | Semantics |
| :--- | :--- |
| `Brain.Selector(name, children)` | First `SUCCESS`/`RUNNING` wins; all `FAILURE` → `FAILURE`. Priority lists. |
| `Brain.Sequence(name, children)` | First `FAILURE`/`RUNNING` stops; all `SUCCESS` → `SUCCESS`. Ordered steps. |
| `Brain.Parallel(name, children, [policy])` | Ticks **every** child each tick (no early exits). `"all"` (default): any `FAILURE` → `FAILURE`. `"any"`: any `SUCCESS` → `SUCCESS`. Simultaneous concerns (legs move + head watches); children must be independent. |
| `Brain.Utility(name, options)` | Scores every `{ score = fn, node }` option each tick (once), runs highest-first until one does not `FAIL`. Dynamic priority; scores must be numbers, lists short, score fns cheap. |

**Decorators** (wrap exactly one child; state lives in `ctx.bb` per node):

| Constructor | Semantics |
| :--- | :--- |
| `Brain.Inverter(name, child)` | `SUCCESS` ↔ `FAILURE`, `RUNNING` passes. |
| `Brain.Succeeder(name, child)` | Always `SUCCESS` unless `RUNNING`. |
| `Brain.Failer(name, child)` | Always `FAILURE` unless `RUNNING`. |
| `Brain.Guard(name, condFn, child)` | Run child only while `condFn(ctx)` holds, else `FAILURE`. The abort switch. |
| `Brain.Cooldown(name, child, ticks)` | Child may **start** at most once per interval; while cooling returns `FAILURE` so enclosing Sequences stall instead of skipping ahead. |
| `Brain.Timeout(name, child, ticks)` | `RUNNING` longer than the limit aborts to `FAILURE` (unstick pathing/waits). |
| `Brain.Repeat(name, child, [times])` | Succeed `times` times → `SUCCESS`; child `FAILURE` aborts. `nil` repeats forever. |
| `Brain.Retry(name, child, attempts)` | Hammer the child up to N times **within one tick** until non-`FAILURE`. |
| `Brain.Latch(name, child)` | Once the child `SUCCESS`es, keep returning `SUCCESS` without rerunning until `node:Reset(ctx)`. `FAILURE`/`RUNNING` pass through unlatched. Summon-once, roar-once, door-opened. |

**Leaves:** `Brain.Condition(name, fn)` (`fn(ctx)` truthy → `SUCCESS`);
`Brain.Action(name, fn)` (`fn(ctx)` must return a valid status — anything else is
a **loud error naming the node**, because silent status typos used to freeze whole
trees).

All constructors **fail fast** on empty names / empty children / non-function
leaves / non-positive intervals. `Cooldown`/`Timeout` require `ctx.now` from
`ent:getGameTime()` and error loudly without it — the wall-clock
`Task.GetTimeClock` is rejected by design (NTP jumps, pauses).

## 4. Typical combat NPC layout

Priority on the left, idleness on the right; complex phases become named subtree
builder functions (plain Lua functions returning node tables — see
`luamods/luatweaker_test/src/server/brain_demo.lua` for a runnable wisp greeter:
flee → greet → hop):

```text
[root Selector]
  [survive Sequence]      <- emergency left
    [dying Condition] [flee Action + Timeout]
  [engage Sequence]       <- combat middle
    [target_visible Condition] [Selector: strike-if-close else chase]
  [patrol Action]         <- idle rightmost
```

Keep trees shallow: a boss phase (windup → strike → recover) is a `Sequence`
built by `buildPhaseTwo()` and plugged in as one child. `Brain.Describe(tree)`
prints the indented structure for docs and `/lt` debugging sessions.

## 5. Perception helpers (pure Lua, one scan per sense tick)

`Brain.HealthFraction(ent)`, `Brain.InRange(ent, target, radius)`,
`Brain.NearestVisible(ent, candidates, [maxRadius])` (distance math filters
**before** any `canSee()` raytrace — always pass a radius in hot ticks),
`Brain.FacingDot(ent, target)` (`> 0` means ahead, `> 0.5` roughly a
120-degree cone; uses the `LookVector` property).

Movement/aim primitives used by actions (all server-side, all mobs):
`ent:moveTo(x, y, z, speed)`, `ent:stopMoving()`, `ent:isNavigating()`,
`ent:lookAt(x, y, z)`, `ent:lookAtEntity(target)`, `ent:jump()` (check
`ent:isOnGround()` first), sensing `ent:canSee(target)`,
`ent:getGameTime()`, combat `ent:shootProjectileAt(type, target, speed)`.
`AIGoals:listGoals(ent)` shows live goal rows; custom Lua goals report their
script-given `name` (`LuaGoal:ambush`) instead of the Java class name.

## 6. Pack coordination (`Brain.Squad`)

Per-entity `ctx.bb` remembers *"my* cooldowns"; a Squad remembers *"OUR* plan".
`Brain.Squad("huntpack")` returns a shared Blackboard (engine-scoped; wipe with
`Brain.SquadForget(key)` when the fight ends):

```lua
-- Alpha (whoever claims it first while live) publishes facts as NUMBERS/short
-- strings; members read them. Entity tables go stale on unload, so re-validate
-- (isAlive) every sense tick before trusting a stored reference.
squad:Set("alpha", ent:getUuid())
squad:Set("alphaPos", { x = ent:getX(), y = ent:getY(), z = ent:getZ() })
squad:Set("focus", Brain.NearestVisible(ent, players, 20.0))
```

Typical pack tree per member: `survive` → `Utility { summon-once (alpha only,
Latch) | bite-in-range | press (Parallel chase + watch) | regroup-to-alphaPos }`.
See `luamods/luatweaker_test/src/server/brain_hunt_demo.lua` for the runnable
version (alpha phases, frenzy multiplier, wisp summon).

## 7. Cost rules

1. One entity scan per sense interval (see `CooldownReady` pattern above), brains
   at `tickRate(5+)` unless per-tick control is truly needed.
2. `maxRadius` on every `NearestVisible` call; conditions never scan the world.
3. Long actions re-entry-safe (`isNavigating` guard) and `Timeout`-capped.
4. Every crossing into Java costs reflection + (for `canSee`) a raytrace: fewer,
   batched calls beat clever per-node micro-checks.
