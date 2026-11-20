-- ===================================================================
-- LuaTweaker Brain: Behavior Tree runtime (pure Lua, no Java needed)
--
-- Tick model: every AI tick re-runs the tree from the ROOT, left to right.
-- Each node returns SUCCESS ("success"), FAILURE ("failure") or RUNNING
-- ("running"). Because the root is re-evaluated each tick, HIGH-PRIORITY
-- branches on the LEFT automatically preempt (abort) lower branches on the
-- right: no explicit abort wiring needed, but long RUNNING actions must be
-- idempotent (re-issuing moveTo while isNavigating() is the standard guard).
--
-- Separation of concerns: a perception step writes facts into the ctx.bb
-- Blackboard (usually inside Brain.Registry per-entity memory); tree nodes
-- only READ the blackboard and issue primitive calls. Never scan the world
-- inside a Condition when a cached fact will do.
--
-- Time base: pass ctx.now = ent:getGameTime() (server tick clock). Cooldown
-- and Timeout decorators require it; the wall-clock Task.GetTimeClock is
-- WRONG here (NTP jumps, pause menus) and is rejected by loud errors.
--
-- Performance rules (every entity call crosses the Java bridge via reflection,
-- and canSee() raytraces blocks, so a hot tick must stay lean):
--   1. ONE perception scan per tick, cached into ctx.bb; tree nodes only read
--      facts. Never call GetEntitiesInRadius inside a Condition.
--   2. Throttle perception with bb:CooldownReady(key, everyNTicks, now) and
--      run brains at tickRate(5+) unless the mob truly needs per-tick control.
--   3. Pass maxRadius to NearestVisible so far candidates die on cheap math
--      before any raytrace.
--   4. Event hooks (onHurt/onDeath/onInteract) bridge into the tree by writing
--      bb flags: registry:Ctx(ent, ent:getGameTime()).bb:Set("hurtAt", now).
--      Hooks and ticks share the per-entity blackboard.
--
-- Decorator memory (Cooldown stamps, Timeout starts, Repeat counters) lives in
-- ctx.bb namespaced per node. If a higher-priority branch aborts a subtree
-- mid-RUNNING, that state stays until the node runs terminally again. This is
-- standard BT memory, not a leak (one key per node, LRU-pruned per entity).
-- ===================================================================

local Brain = {}

Brain.SUCCESS = "success"
Brain.FAILURE = "failure"
Brain.RUNNING = "running"

local nextNodeId = 0
local function allocId()
    nextNodeId = nextNodeId + 1
    return nextNodeId
end

local function isStatus(value)
    return value == Brain.SUCCESS or value == Brain.FAILURE or value == Brain.RUNNING
end

local function checkNode(child, parentName)
    if type(child) ~= "table" or type(child.run) ~= "function" then
        error("[Brain] '" .. tostring(parentName) .. "' needs child nodes "
            .. "(use Brain.Action / Brain.Condition / composites), got "
            .. type(child), 3)
    end
end

local function checkChildren(children, name)
    if type(children) ~= "table" or #children == 0 then
        error("[Brain] '" .. tostring(name) .. "' needs a non-empty children array", 3)
    end
    for i = 1, #children do
        checkNode(children[i], name)
    end
end

local function checkName(name, kind)
    if type(name) ~= "string" or name == "" then
        error("[Brain] " .. tostring(kind) .. " needs a non-empty string name "
            .. "(names appear in traces and listGoals debugging)", 3)
    end
end

--- Runs one node, appending "name=status" to ctx.trace when tracing is on.
local function exec(node, ctx)
    local status = node.run(ctx)
    if ctx.trace ~= nil then
        ctx.trace[#ctx.trace + 1] = node.name .. "=" .. tostring(status)
    end
    return status
end

--- Ticks the whole tree from the root. Returns SUCCESS / FAILURE / RUNNING.
---@param tree table root node built by the constructors below
---@param ctx table { ent: entity table, bb: Blackboard, now: game ticks, trace?: array }
---@return string status
function Brain.Tick(tree, ctx)
    checkNode(tree, "Brain.Tick root")
    if type(ctx) ~= "table" or ctx.bb == nil then
        error("[Brain] Tick needs ctx = { ent = ..., bb = Brain.Blackboard(), now = ... }", 2)
    end
    return exec(tree, ctx)
end

-- ===================================================================
-- Blackboard: per-entity scratch memory (transient, Lua-side).
-- Unlike entity:setCustomData (persistent NBT, written to disk), the
-- blackboard costs nothing per tick and dies with the Registry entry.
-- ===================================================================

---@return table blackboard with Get/Set/Inc/Stamp/Last/TimeSince/CooldownReady
function Brain.Blackboard()
    local data = {}
    local bb = {}

    ---@param key string
    ---@param default any value returned when unset
    function bb:Get(key, default)
        local value = data[key]
        if value == nil then
            return default
        end
        return value
    end

    ---@param key string
    ---@param value any nil removes the key
    function bb:Set(key, value)
        data[key] = value
    end

    ---@param key string
    ---@param amount number increment step, default 1
    ---@return number new value
    function bb:Inc(key, amount)
        local next = (tonumber(data[key]) or 0) + (tonumber(amount) or 1)
        data[key] = next
        return next
    end

    ---@param key string
    ---@param now number game ticks
    function bb:Stamp(key, now)
        data[key] = now
    end

    ---@param key string
    ---@return number|nil last stamped tick
    function bb:Last(key)
        return data[key]
    end

    ---@param key string
    ---@param now number game ticks
    ---@return number|nil ticks elapsed, nil when never stamped
    function bb:TimeSince(key, now)
        local last = data[key]
        if last == nil then
            return nil
        end
        return now - last
    end

    --- One-shot gate: true at most once per intervalTicks. Stamps on success.
    ---@param key string
    ---@param intervalTicks number
    ---@param now number game ticks
    ---@return boolean ready
    function bb:CooldownReady(key, intervalTicks, now)
        local last = data[key]
        if last ~= nil and (now - last) < intervalTicks then
            return false
        end
        data[key] = now
        return true
    end

    return bb
end

-- ===================================================================
-- Composites
-- ===================================================================

--- Selector (?): first child returning SUCCESS or RUNNING wins; all FAILURE -> FAILURE.
---@param name string
---@param children table array of nodes, priority left to right
function Brain.Selector(name, children)
    checkName(name, "Selector")
    checkChildren(children, name)
    return {
        name = name,
        children = children,
        _btid = allocId(),
        run = function(ctx)
            for i = 1, #children do
                local status = exec(children[i], ctx)
                if status ~= Brain.FAILURE then
                    return status
                end
            end
            return Brain.FAILURE
        end,
    }
end

--- Sequence (->): every child must succeed; first FAILURE or RUNNING stops the chain.
---@param name string
---@param children table array of steps in order
function Brain.Sequence(name, children)
    checkName(name, "Sequence")
    checkChildren(children, name)
    return {
        name = name,
        children = children,
        _btid = allocId(),
        run = function(ctx)
            for i = 1, #children do
                local status = exec(children[i], ctx)
                if status ~= Brain.SUCCESS then
                    return status
                end
            end
            return Brain.SUCCESS
        end,
    }
end

--- Parallel: ticks EVERY child each tick (simultaneous concerns: legs move
--- while the head watches). Policy "all" (default): any FAILURE -> FAILURE,
--- all SUCCESS -> SUCCESS, else RUNNING. Policy "any": any SUCCESS -> SUCCESS,
--- all FAILURE -> FAILURE, else RUNNING. Children must be independent: two
--- writers to the same channel (two moveTo targets) will fight every tick.
---@param name string
---@param children table array of nodes
---@param policy string|nil "all" or "any"
function Brain.Parallel(name, children, policy)
    checkName(name, "Parallel")
    checkChildren(children, name)
    policy = policy or "all"
    if policy ~= "all" and policy ~= "any" then
        error("[Brain] Parallel '" .. name .. "' policy must be 'all' or 'any'", 2)
    end
    return {
        name = name,
        children = children,
        _btid = allocId(),
        run = function(ctx)
            -- No early exits: simultaneity means every child ticks, then the
            -- policy combines. (An early return on FAILURE would make the tail
            -- children starve whenever the head fails.)
            local sawSuccess = false
            local sawRunning = false
            local sawFailure = false
            for i = 1, #children do
                local status = exec(children[i], ctx)
                if status == Brain.SUCCESS then
                    sawSuccess = true
                elseif status == Brain.RUNNING then
                    sawRunning = true
                else
                    sawFailure = true
                end
            end
            if policy == "all" then
                if sawFailure then
                    return Brain.FAILURE
                end
                if sawRunning then
                    return Brain.RUNNING
                end
                return Brain.SUCCESS
            else
                if sawSuccess then
                    return Brain.SUCCESS
                end
                if sawRunning then
                    return Brain.RUNNING
                end
                return Brain.FAILURE
            end
        end,
    }
end

--- Utility: scores every option each tick and runs them highest-first until one
--- does not FAIL (dynamic priority: hunger 0.8 beats curiosity 0.3, but the
--- ranking is recomputed from bb facts every tick, so a scare still preempts).
--- Each option is { score = fn(ctx) -> number, node = node }. Scores are computed
--- ONCE per tick, ties break toward the earlier option. Non-number scores are a
--- loud error. Keep the list short (2-4) and score fns cheap: read bb facts,
--- never scan the world (a scan inside a comparator runs O(n log n) times).
---@param name string
---@param options table array of { score, node }
function Brain.Utility(name, options)
    checkName(name, "Utility")
    if type(options) ~= "table" or #options == 0 then
        error("[Brain] Utility '" .. name .. "' needs a non-empty options array", 2)
    end
    for i = 1, #options do
        local opt = options[i]
        if type(opt) ~= "table" or type(opt.score) ~= "function" then
            error("[Brain] Utility '" .. name .. "' option " .. i
                .. " needs { score = fn, node = node }", 2)
        end
        checkNode(opt.node, name .. " option " .. i)
    end
    return {
        name = name,
        options = options,
        _btid = allocId(),
        run = function(ctx)
            local ranked = {}
            for i = 1, #options do
                local value = options[i].score(ctx)
                if type(value) ~= "number" then
                    error("[Brain] Utility '" .. name .. "' option " .. i
                        .. " score must return a number, got: " .. tostring(value))
                end
                ranked[i] = { idx = i, value = value }
            end
            table.sort(ranked, function(a, b)
                if a.value == b.value then
                    return a.idx < b.idx
                end
                return a.value > b.value
            end)
            for _, entry in ipairs(ranked) do
                local status = exec(options[entry.idx].node, ctx)
                if status ~= Brain.FAILURE then
                    return status
                end
            end
            return Brain.FAILURE
        end,
    }
end

-- ===================================================================
-- Decorators (wrap exactly one child)
-- ===================================================================

--- Inverter: SUCCESS <-> FAILURE, RUNNING passes through.
function Brain.Inverter(name, child)
    checkName(name, "Inverter")
    checkNode(child, name)
    return {
        name = name,
        child = child,
        _btid = allocId(),
        run = function(ctx)
            local status = exec(child, ctx)
            if status == Brain.SUCCESS then
                return Brain.FAILURE
            end
            if status == Brain.FAILURE then
                return Brain.SUCCESS
            end
            return Brain.RUNNING
        end,
    }
end

--- Succeeder: always SUCCESS unless the child is still RUNNING.
function Brain.Succeeder(name, child)
    checkName(name, "Succeeder")
    checkNode(child, name)
    return {
        name = name,
        child = child,
        _btid = allocId(),
        run = function(ctx)
            if exec(child, ctx) == Brain.RUNNING then
                return Brain.RUNNING
            end
            return Brain.SUCCESS
        end,
    }
end

--- Failer: always FAILURE unless the child is still RUNNING.
function Brain.Failer(name, child)
    checkName(name, "Failer")
    checkNode(child, name)
    return {
        name = name,
        child = child,
        _btid = allocId(),
        run = function(ctx)
            if exec(child, ctx) == Brain.RUNNING then
                return Brain.RUNNING
            end
            return Brain.FAILURE
        end,
    }
end

--- Guard: runs the child only while conditionFn(ctx) is truthy, else FAILURE.
--- Re-evaluated every tick, so this is the abort switch for stale branches.
function Brain.Guard(name, conditionFn, child)
    checkName(name, "Guard")
    if type(conditionFn) ~= "function" then
        error("[Brain] Guard '" .. name .. "' needs a condition function", 2)
    end
    checkNode(child, name)
    return {
        name = name,
        child = child,
        _btid = allocId(),
        run = function(ctx)
            if not conditionFn(ctx) then
                return Brain.FAILURE
            end
            return exec(child, ctx)
        end,
    }
end

local function needNow(ctx, decoratorName)
    if ctx.now == nil then
        error("[Brain] '" .. decoratorName .. "' needs ctx.now = ent:getGameTime() "
            .. "(game ticks, never the wall clock)", 3)
    end
    return ctx.now
end

--- Cooldown: the child may START at most once per intervalTicks; while cooling
--- returns FAILURE so enclosing Sequences stall instead of skipping ahead.
--- NOTE: the last-run stamp lives in ctx.bb. If a higher-priority branch aborts
--- this subtree mid-RUNNING, the stamp stays until the next successful start;
--- that is standard decorator memory, not a leak (one key per node).
function Brain.Cooldown(name, child, intervalTicks)
    checkName(name, "Cooldown")
    checkNode(child, name)
    if (tonumber(intervalTicks) or 0) <= 0 then
        error("[Brain] Cooldown '" .. name .. "' needs intervalTicks > 0", 2)
    end
    local node = { name = name, child = child, _btid = allocId() }
    node._key = "cooldown_" .. node._btid
    node.run = function(ctx)
        local now = needNow(ctx, name)
        local last = ctx.bb:Last(node._key)
        if last ~= nil and (now - last) < intervalTicks then
            return Brain.FAILURE
        end
        local status = exec(child, ctx)
        if status ~= Brain.RUNNING then
            ctx.bb:Stamp(node._key, now)
        end
        return status
    end
    return node
end

--- Timeout: a RUNNING child running longer than limitTicks aborts to FAILURE.
--- Terminal results pass through and reset the timer.
function Brain.Timeout(name, child, limitTicks)
    checkName(name, "Timeout")
    checkNode(child, name)
    if (tonumber(limitTicks) or 0) <= 0 then
        error("[Brain] Timeout '" .. name .. "' needs limitTicks > 0", 2)
    end
    local node = { name = name, child = child, _btid = allocId() }
    node._key = "timeout_" .. node._btid
    node.run = function(ctx)
        local now = needNow(ctx, name)
        local status = exec(child, ctx)
        if status ~= Brain.RUNNING then
            ctx.bb:Set(node._key, nil)
            return status
        end
        local started = ctx.bb:Last(node._key)
        if started == nil then
            ctx.bb:Stamp(node._key, now)
            return Brain.RUNNING
        end
        if (now - started) >= limitTicks then
            ctx.bb:Set(node._key, nil)
            return Brain.FAILURE
        end
        return Brain.RUNNING
    end
    return node
end

--- Repeat: runs the child until it succeeds `times` times (then SUCCESS).
--- A child FAILURE aborts to FAILURE. times == nil repeats forever.
function Brain.Repeat(name, child, times)
    checkName(name, "Repeat")
    checkNode(child, name)
    if times ~= nil and (tonumber(times) or 0) < 1 then
        error("[Brain] Repeat '" .. name .. "' needs times >= 1 or nil", 2)
    end
    local node = { name = name, child = child, _btid = allocId() }
    node._key = "repeat_" .. node._btid
    node.run = function(ctx)
        local status = exec(child, ctx)
        if status == Brain.FAILURE then
            ctx.bb:Set(node._key, nil)
            return Brain.FAILURE
        end
        if status == Brain.RUNNING then
            return Brain.RUNNING
        end
        local done = (ctx.bb:Get(node._key, 0) or 0) + 1
        if times ~= nil and done >= times then
            ctx.bb:Set(node._key, nil)
            return Brain.SUCCESS
        end
        ctx.bb:Set(node._key, done)
        return Brain.RUNNING
    end
    return node
end

--- Retry: ticks the child up to `attempts` times WITHIN one tick until SUCCESS.
--- Returns FAILURE when every attempt fails, RUNNING when the child is busy.
function Brain.Retry(name, child, attempts)
    checkName(name, "Retry")
    checkNode(child, name)
    if (tonumber(attempts) or 0) < 1 then
        error("[Brain] Retry '" .. name .. "' needs attempts >= 1", 2)
    end
    return {
        name = name,
        child = child,
        _btid = allocId(),
        run = function(ctx)
            for _ = 1, attempts do
                local status = exec(child, ctx)
                if status ~= Brain.FAILURE then
                    return status
                end
            end
            return Brain.FAILURE
        end,
    }
end

--- Latch: once the child SUCCEEDS, keep returning SUCCESS without running it
--- again until Reset(ctx) (phase changes, fight resets). FAILURE and RUNNING
--- pass through unlatched, so a failed summon retries next tick while a spent
--- one never refires. Standard use: summon-once, roar-once, door-opened.
function Brain.Latch(name, child)
    checkName(name, "Latch")
    checkNode(child, name)
    local node = { name = name, child = child, _btid = allocId() }
    node._key = "latch_" .. node._btid
    node.run = function(ctx)
        if ctx.bb:Get(node._key) then
            return Brain.SUCCESS
        end
        local status = exec(child, ctx)
        if status == Brain.SUCCESS then
            ctx.bb:Set(node._key, true)
        end
        return status
    end
    ---@param ctx table clears the latched SUCCESS so the child may run again
    function node:Reset(ctx)
        ctx.bb:Set(self._key, nil)
    end
    return node
end

-- ===================================================================
-- Leaves
-- ===================================================================

--- Condition leaf: truthy fn(ctx) -> SUCCESS, falsy -> FAILURE.
function Brain.Condition(name, fn)
    checkName(name, "Condition")
    if type(fn) ~= "function" then
        error("[Brain] Condition '" .. name .. "' needs a function", 2)
    end
    return {
        name = name,
        _btid = allocId(),
        run = function(ctx)
            if fn(ctx) then
                return Brain.SUCCESS
            end
            return Brain.FAILURE
        end,
    }
end

--- Action leaf: fn(ctx) must return "success" / "failure" / "running".
--- Anything else is a LOUD error naming the node (silent typos here used to
--- freeze whole trees, so strictness is the feature).
function Brain.Action(name, fn)
    checkName(name, "Action")
    if type(fn) ~= "function" then
        error("[Brain] Action '" .. name .. "' needs a function", 2)
    end
    return {
        name = name,
        _btid = allocId(),
        run = function(ctx)
            local status = fn(ctx)
            if not isStatus(status) then
                error("[Brain] Action '" .. name .. "' must return Brain.SUCCESS / "
                    .. "Brain.FAILURE / Brain.RUNNING, got: " .. tostring(status), 2)
            end
            return status
        end,
    }
end

-- ===================================================================
-- Registry: per-entity tick memory with LRU pruning.
-- One tree table is shared by every instance of a type; the Registry hands
-- each entity its own { bb, lastSeen } so state never leaks across mobs.
-- Stale entries (dead/unloaded entities) are evicted past maxEntries.
-- ===================================================================

---@param maxEntries number cap, default 128
function Brain.Registry(maxEntries)
    local cap = tonumber(maxEntries) or 128
    if cap < 1 then
        error("[Brain] Registry needs maxEntries >= 1", 2)
    end
    local entries = {}
    local count = 0
    local reg = {}

    local function entityKey(ent)
        if type(ent) == "table" and type(ent.getUuid) == "function" then
            return ent:getUuid()
        end
        return "obj:" .. tostring(ent)
    end

    ---@param ent table entity Lua table (needs getUuid)
    ---@param now number game ticks from ent:getGameTime()
    ---@return table ctx { ent, bb, now, trace = nil }
    --- NOTE: the SAME ctx table is returned for an entity until it is evicted,
    --- so per-tick overhead is field writes, not allocation. Callers own
    --- ctx.trace: set it to {} when debugging, it is cleared on every Ctx().
    function reg:Ctx(ent, now)
        if ent == nil then
            error("[Brain] Registry:Ctx needs an entity table", 2)
        end
        local key = entityKey(ent)
        local entry = entries[key]
        if entry == nil then
            entry = { bb = Brain.Blackboard(), lastSeen = now, ctx = nil }
            entry.ctx = { ent = ent, bb = entry.bb, now = now, trace = nil }
            entries[key] = entry
            count = count + 1
            while count > cap do
                local oldestKey = nil
                local oldestSeen = nil
                for candidateKey, candidate in pairs(entries) do
                    if oldestSeen == nil or candidate.lastSeen < oldestSeen then
                        oldestKey = candidateKey
                        oldestSeen = candidate.lastSeen
                    end
                end
                if oldestKey == nil then
                    break
                end
                entries[oldestKey] = nil
                count = count - 1
            end
        end
        entry.lastSeen = now
        entry.ctx.ent = ent
        entry.ctx.now = now
        entry.ctx.trace = nil
        return entry.ctx
    end

    function reg:Forget(ent)
        local key = entityKey(ent)
        if entries[key] ~= nil then
            entries[key] = nil
            count = count - 1
        end
    end

    function reg:Size()
        return count
    end

    return reg
end

-- ===================================================================
-- Squad: shared blackboard across entities (pack coordination).
-- Per-entity ctx.bb remembers "my cooldowns"; a Squad remembers "OUR plan":
-- the alpha writes focusTargetId/focusPos, members read it. Module-level and
-- engine-scoped (a /lt reload wipes it, like all Lua state). Store NUMBERS
-- and short strings here; entity tables go stale when targets unload, so
-- re-validate (isAlive) every sense tick before trusting a stored reference.
-- ===================================================================

local squads = {}

---@param key string non-empty pack id, e.g. "huntpack_north"
---@return table shared Blackboard (created on first use)
function Brain.Squad(key)
    if type(key) ~= "string" or key == "" then
        error("[Brain] Squad needs a non-empty string key", 2)
    end
    local bb = squads[key]
    if bb == nil then
        bb = Brain.Blackboard()
        squads[key] = bb
    end
    return bb
end

--- Drops a pack's shared memory (pack wiped, fight over).
---@param key string
function Brain.SquadForget(key)
    squads[key] = nil
end

-- ===================================================================
-- Perception helpers (pure Lua over existing primitives).
-- Call these in a perception step, stash facts on ctx.bb, and let the tree
-- read facts. Keeps per-tick entity scans in ONE place.
-- ===================================================================

---@return number 0..1 (1.0 when the entity has no max health to divide by)
function Brain.HealthFraction(ent)
    local max = ent:getMaxHealth()
    if max == nil or max <= 0 then
        return 1.0
    end
    return ent:getHealth() / max
end

---@return boolean
function Brain.InRange(ent, target, radius)
    if ent == nil or target == nil then
        return false
    end
    return ent:distanceTo(target) <= radius
end

--- Nearest candidate the entity has line of sight to (nil when none).
--- DISTANCE IS CHEAP, SIGHT IS NOT: candidates beyond maxRadius are skipped
--- by pure math before any hasLineOfSight raytrace runs, so always pass a
--- radius in hot ticks instead of scanning the whole entity list.
---@param ent table
---@param candidates table array of entity tables
---@param maxRadius number|nil skip candidates farther than this (default: no limit)
---@return table|nil
function Brain.NearestVisible(ent, candidates, maxRadius)
    local best = nil
    local bestDist = nil
    for i = 1, #candidates do
        local candidate = candidates[i]
        if candidate ~= nil then
            local dist = ent:distanceTo(candidate)
            if (maxRadius == nil or dist <= maxRadius)
                and (bestDist == nil or dist < bestDist)
                and ent:canSee(candidate) then
                best = candidate
                bestDist = dist
            end
        end
    end
    return best
end

--- Facing dot product (-1 behind .. +1 dead ahead) via the LookVector
--- property; 0 when vectors are unavailable. FOV check: dot > 0 means the
--- target is somewhere in front (dot > 0.5 is roughly a 120-degree cone).
---@return number
function Brain.FacingDot(ent, target)
    if ent == nil or target == nil then
        return 0
    end
    local look = ent.LookVector
    if look == nil or look.X == nil or look.Y == nil or look.Z == nil then
        return 0
    end
    local dx = target:getX() - ent:getX()
    local dy = (target:getY() + 1.0) - (ent:getY() + 1.0)
    local dz = target:getZ() - ent:getZ()
    local len = math.sqrt(dx * dx + dy * dy + dz * dz)
    if len == 0 then
        return 1
    end
    return (look.X * dx + look.Y * dy + look.Z * dz) / len
end

--- Renders the tree as indented text for docs and /lt debugging sessions.
---@return string
function Brain.Describe(tree)
    checkNode(tree, "Brain.Describe")
    local lines = {}
    local function walk(node, depth)
        lines[#lines + 1] = string.rep("  ", depth) .. (node.name or "?")
        local kids = node.children
        if kids ~= nil then
            for i = 1, #kids do
                walk(kids[i], depth + 1)
            end
        elseif node.child ~= nil then
            walk(node.child, depth + 1)
        end
    end
    walk(tree, 0)
    return table.concat(lines, "\n")
end

_G.Brain = Brain
return Brain
