-- ===================================================================
-- LuaTweaker System Bootstrap Script
-- Autonomous Lua Runtime Libraries (Task, Signal, Roblox APIs)
-- ===================================================================

local task = {}
local deferred = {}
local delays = {}

local function parseTaskArgs(...)
    local raw = {...}
    if #raw == 0 then
        return nil, {}
    end
    local first = raw[1]
    local fn
    local startIdx = 2
    if type(first) == 'table' then
        fn = raw[2]
        startIdx = 3
    else
        fn = first
        startIdx = 2
    end
    local args = {}
    for i = startIdx, #raw do
        table.insert(args, raw[i])
    end
    return fn, args
end

-- Creates a cancellable task entry. The returned handle is what task.cancel() takes;
-- Cancel() is false once the task has started or was already cancelled.
local function makeTaskEntry(fn, args, time)
    local entry = { fn = fn, args = args, time = time, cancelled = false, started = false }
    entry.Cancel = function()
        if entry.started or entry.cancelled then
            return false
        end
        entry.cancelled = true
        return true
    end
    return entry
end

function task.spawn(...)
    return task.defer(...)
end

--- Schedules fn for the next tick. Returns a handle for task.cancel().
function task.defer(...)
    local fn, args = parseTaskArgs(...)
    if type(fn) ~= 'function' then
        error('[task.defer] Expected function, got ' .. type(fn))
    end
    local entry = makeTaskEntry(fn, args, 0)
    table.insert(deferred, entry)
    return entry
end

--- Cancels a task that has not started yet (from task.defer/spawn/delay).
--- Returns true when it was cancelled, false when it already ran or was cancelled.
function task.cancel(handle)
    if type(handle) == 'table' and type(handle.Cancel) == 'function' then
        return handle.Cancel()
    end
    return false
end

local function getTimeClock()
    if task and type(task.getTimeClock) == 'function' then
        return task.getTimeClock()
    end
    if os and type(os) == 'table' and os.clock then
        return os.clock()
    end
    return 0
end

function task.delay(...)
    local rawArgs = {...}
    local sec = rawArgs[1]
    local fn = rawArgs[2]
    local startIdx = 3
    if type(sec) == 'table' then
        sec = rawArgs[2]
        fn = rawArgs[3]
        startIdx = 4
    end
    sec = tonumber(sec) or 0
    if type(fn) ~= 'function' then
        error('[task.delay] Expected function, got ' .. type(fn))
    end
    local passArgs = {}
    for i = startIdx, #rawArgs do
        table.insert(passArgs, rawArgs[i])
    end
    local entry = makeTaskEntry(fn, passArgs, getTimeClock() + sec)
    table.insert(delays, entry)
    return entry
end

function task.wait(...)
    local rawArgs = {...}
    local sec = rawArgs[1]
    if type(sec) == 'table' then
        sec = rawArgs[2]
    end
    sec = tonumber(sec) or 0
    -- Lua 5.4 returns (thread, isMain). Outside a coroutine the first value is the
    -- main thread, which cannot be yielded, so check isMain instead of nil.
    local thread, isMain = coroutine.running()
    if thread == nil or isMain then
        -- Without a resumable coroutine there is nothing to resume later, so
        -- yield() would raise "attempt to yield from outside a coroutine".
        print('[WARN][task] task.wait() called outside a coroutine - wrap it in Task.spawn()')
        return
    end
    task.delay(sec, function()
        if thread then
            local ok, err = coroutine.resume(thread)
            if not ok then
                print('[ERROR][task] task.wait resume failed: ' .. tostring(err))
            end
        end
    end)
    return coroutine.yield()
end

-- Run a deferred function inside a Lua coroutine (task._run_deferred is a Java
-- binding backed by the engine coroutine scheduler). Catches Lua errors per task
-- so one crashing task cannot abort the whole tick.
local function runDeferred(fn, args)
    if type(fn) ~= 'function' then
        print('[WARN][task] Skipping deferred task: expected function, got ' .. tostring(fn))
        return
    end
    local ok, err = pcall(task._run_deferred, fn, args)
    if not ok and err then
        print('[ERROR][task] Deferred task error: ' .. tostring(err))
    end
end

function task._tick()
    -- Snapshot both queues up front. A wait() scheduled by a deferred task during
    -- this tick belongs to the next tick, not to the delay pass of this one.
    local def = deferred
    deferred = {}
    local due = delays
    delays = {}

    for _, item in ipairs(def) do
        if not item.cancelled then
            item.started = true
            runDeferred(item.fn, item.args)
        end
    end

    local now = getTimeClock()
    for _, item in ipairs(due) do
        if item.cancelled then
            -- dropped: task.cancel() ran before the deadline
        elseif now >= item.time then
            item.started = true
            runDeferred(item.fn, item.args)
        else
            table.insert(delays, item)
        end
    end
end

-- Queue snapshot used by /lt doctor. The deferral/delay queues live in this closure,
-- so Java cannot read them any other way.
function task._stats()
    return { queued = #deferred + #delays, deferred = #deferred, delayed = #delays }
end

_G.task = task
_G.Task = task

-- ===================================================================
-- ROBLOX SIGNAL & EVENT SYSTEM
-- ===================================================================

local Signal = {}
Signal.__index = Signal

function Signal.new()
    local self = setmetatable({}, Signal)
    self._listeners = {}
    return self
end

function Signal:Connect(fn)
    self._listeners = self._listeners or {}
    local listener = { fn = fn, connected = true }
    table.insert(self._listeners, listener)
    local conn = {
        Disconnect = function()
            listener.connected = false
            for i, l in ipairs(self._listeners) do
                if l == listener then
                    table.remove(self._listeners, i)
                    break
                end
            end
        end
    }
    conn.disconnect = conn.Disconnect
    return conn
end
Signal.connect = Signal.Connect

function Signal:Once(fn)
    local connection
    local fired = false
    connection = self:Connect(function(...)
        -- Fire defers listeners, so two Fire calls in one tick can enqueue this
        -- wrapper twice before the first Disconnect runs. The latch makes it once.
        if fired then return end
        fired = true
        connection:Disconnect()
        fn(...)
    end)
    return connection
end

function Signal:Fire(...)
    self._listeners = self._listeners or {}
    local args = {...}
    -- Iterate a snapshot: a listener may Disconnect itself (Once) or another
    -- listener while the automatically-dispatched tasks run.
    local snapshot = {}
    for i = 1, #self._listeners do snapshot[i] = self._listeners[i] end
    for _, l in ipairs(snapshot) do
        if l.connected and type(l.fn) == 'function' then
            task.spawn(l.fn, table.unpack(args))
        end
    end
end
Signal.fire = Signal.Fire

-- Synchronous variant: invokes listeners immediately on the calling thread.
-- Required for render-time callbacks (e.g. GuiService.OnRenderHUD) that must
-- draw during the render event while the GuiGraphics context is available.
function Signal:FireSync(...)
    self._listeners = self._listeners or {}
    local args = {...}
    -- Snapshot first: Disconnect removes from the live array, which would skip
    -- the listener that shifts into the current index during iteration.
    local snapshot = {}
    for i = 1, #self._listeners do snapshot[i] = self._listeners[i] end
    for _, l in ipairs(snapshot) do
        if l.connected and type(l.fn) == 'function' then
            local ok, err = pcall(l.fn, table.unpack(args))
            if not ok and err then
                local tb = debug and debug.traceback and debug.traceback(err, 2)
                print('[ERROR][Signal] Listener error: ' .. tostring(err) .. (tb and ('\n' .. tb) or ''))
            end
        end
    end
end
Signal.fireSync = Signal.FireSync

function Signal:Wait()
    local runningThread, isMain = coroutine.running()
    if runningThread == nil or isMain then
        print('[WARN][Signal] Signal:Wait() called outside a coroutine - returning nil')
        return nil
    end
    local conn
    local resumed = false
    conn = self:Connect(function(...)
        if resumed then return end
        resumed = true
        conn:Disconnect()
        local ok, err = coroutine.resume(runningThread, ...)
        if not ok then
            print('[ERROR][Signal] Signal:Wait resume failed: ' .. tostring(err))
        end
    end)
    return coroutine.yield()
end

_G.Signal = Signal

local Client = _G.Client or {}
Client.OnKeyBindPressed = Signal.new()
_G.Client = Client

-- ===================================================================
-- NETWORK REMOTE EVENT & REMOTE FUNCTION
-- ===================================================================

local RemoteEvent = {}
RemoteEvent.__index = RemoteEvent

function RemoteEvent:new(name, javaNetworkService)
    local self = setmetatable({}, RemoteEvent)
    self.Name = name
    self.OnServerEvent = Signal.new()
    self.OnClientEvent = Signal.new()
    self._javaService = javaNetworkService
    return self
end

function RemoteEvent:FireServer(...)
    local args = {...}
    local net = _G.NetworkService
    if net and type(net.FireServer) == "function" then
        net:FireServer(self.Name, args)
    elseif self._javaService then
        pcall(function() self._javaService:FireServer(self.Name, args) end)
    end
end

function RemoteEvent:FireClient(player, ...)
    local args = {...}
    local uuid = ""
    if type(player) == "string" then
        uuid = player
    elseif type(player) == "table" and type(player.getUuid) == "function" then
        uuid = player:getUuid()
    end
    local net = _G.NetworkService
    if net and type(net.FireClient) == "function" then
        net:FireClient(self.Name, uuid, args)
    elseif self._javaService then
        pcall(function() self._javaService:FireClient(self.Name, uuid, args) end)
    end
end

function RemoteEvent:FireAllClients(...)
    local args = {...}
    local net = _G.NetworkService
    if net and type(net.FireAllClients) == "function" then
        net:FireAllClients(self.Name, args)
    elseif self._javaService then
        pcall(function() self._javaService:FireAllClients(self.Name, args) end)
    end
end

_G.RemoteEvent = RemoteEvent

local RemoteFunction = {}
RemoteFunction.__index = RemoteFunction

-- RemoteFunction is a real request/response round trip: the request goes over the
-- network, the answering side runs OnServerInvoke/OnClientInvoke, and the caller
-- yields until the answer arrives. These are hard caps for that wait.
local RPC_POLL_INTERVAL_SECONDS = 0.05
local RPC_TIMEOUT_SECONDS = 10

local function canYield()
    local thread, isMain = coroutine.running()
    return thread ~= nil and not isMain
end

-- Waits for one request id, yielding the calling coroutine between polls.
-- Returns the remote return value; raises on remote errors, timeouts and misuse.
local function awaitRpc(net, requestId, label)
    if type(requestId) ~= 'string' or requestId == '' then
        error('[' .. label .. '] transport did not return a request id', 0)
    end
    local startedAt = getTimeClock()
    while true do
        local state = net:_RpcPoll(requestId)
        local status = type(state) == 'table' and state.Status or nil
        if status == 'done' then
            return state.Value
        end
        if status == 'error' then
            error('[' .. label .. '] ' .. tostring(state.Error), 0)
        end
        if status == nil then
            net:_RpcCancel(requestId)
            error('[' .. label .. '] transport returned no response state', 0)
        end
        if getTimeClock() - startedAt >= RPC_TIMEOUT_SECONDS then
            net:_RpcCancel(requestId)
            error('[' .. label .. '] timed out after ' .. RPC_TIMEOUT_SECONDS .. 's', 0)
        end
        if not canYield() then
            net:_RpcCancel(requestId)
            error('[' .. label .. '] waits for the server answer, so it must run inside a '
                .. 'coroutine: use task.spawn(function() ... end) or task.delay', 0)
        end
        task.wait(RPC_POLL_INTERVAL_SECONDS)
    end
end

function RemoteFunction:new(name, javaNetworkService)
    local self = setmetatable({}, RemoteFunction)
    self.Name = name
    self.OnServerInvoke = nil
    self.OnClientInvoke = nil
    self._javaService = javaNetworkService
    return self
end

--- Invokes the server handler (server side receives the calling player first).
function RemoteFunction:InvokeServer(...)
    local args = {...}
    local net = _G.NetworkService
    if not net or type(net._RpcInvokeServer) ~= 'function' then
        return nil
    end
    local requestId = net:_RpcInvokeServer(self.Name, args)
    return awaitRpc(net, requestId, 'RemoteFunction:InvokeServer(' .. tostring(self.Name) .. ')')
end

--- Invokes one client's handler; the client handler receives only these arguments.
function RemoteFunction:InvokeClient(player, ...)
    local args = {...}
    local uuid = ""
    if type(player) == 'string' then
        uuid = player
    elseif type(player) == 'table' and type(player.getUuid) == 'function' then
        uuid = player:getUuid()
    end
    local net = _G.NetworkService
    if not net or type(net._RpcInvokeClient) ~= 'function' then
        return nil
    end
    local requestId = net:_RpcInvokeClient(self.Name, uuid, args)
    return awaitRpc(net, requestId, 'RemoteFunction:InvokeClient(' .. tostring(self.Name) .. ')')
end

_G.RemoteFunction = RemoteFunction

-- ===================================================================
-- USER INPUT & RUN SERVICE APIS
-- ===================================================================

-- InputBegan/InputEnded fire on raw client input with:
--   (inputType, code, action, modifiers)
-- inputType = "key" | "mouse", code = GLFW key/button id,
-- action = 1 (press) or 0 (release), modifiers = GLFW modifier bits.
local UserInputService = {
    InputBegan = Signal.new(),
    InputEnded = Signal.new()
}
function UserInputService:IsKeyDown(keyCode)
    return false
end
_G.UserInputService = UserInputService

local RunService = {
    Heartbeat = Signal.new(),
    RenderStepped = Signal.new(),
    Stepped = Signal.new()
}
function RunService:IsServer()
    return true
end
function RunService:IsClient()
    return false
end
_G.RunService = RunService

-- ===================================================================
-- MATH & CFRAME UTILITIES
-- ===================================================================

local CFrame = {}
CFrame.__index = CFrame

function CFrame.new(x, y, z)
    local self = setmetatable({}, CFrame)
    if type(x) == "table" and x.X then
        self.Position = x
    else
        self.Position = Vector3.new(x or 0, y or 0, z or 0)
    end
    self.LookVector = Vector3.new(0, 0, -1)
    self.UpVector = Vector3.new(0, 1, 0)
    return self
end

function CFrame.lookAt(eye, target)
    local cf = CFrame.new(eye)
    if eye and target then
        local dir = (target - eye).Unit
        cf.LookVector = dir
    end
    return cf
end

_G.CFrame = CFrame

local TweenInfo = {}
TweenInfo.__index = TweenInfo

function TweenInfo.new(time, easingStyle, easingDirection, repeatCount, reverses, delayTime)
    local self = setmetatable({}, TweenInfo)
    self.Time = time or 1.0
    self.Duration = self.Time
    self.EasingStyle = easingStyle or "Linear"
    self.EasingDirection = easingDirection or "Out"
    self.RepeatCount = repeatCount or 0
    self.Reverses = reverses or false
    self.DelayTime = delayTime or 0
    return self
end

_G.TweenInfo = TweenInfo

-- TweenService is bound by ClientLuaBinding (Java impl with real Play/Pause/Cancel).

print("[LuaTweaker] System Bootstrap Library loaded successfully!")
