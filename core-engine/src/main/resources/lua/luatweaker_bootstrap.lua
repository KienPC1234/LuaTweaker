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

function task.spawn(...)
    local fn, args = parseTaskArgs(...)
    if type(fn) ~= 'function' then
        error('[task.spawn] Expected function, got ' .. type(fn))
    end
    local thread = coroutine.create(fn)
    local ok, err
    if #args > 0 then
        ok, err = coroutine.resume(thread, table.unpack(args))
    else
        ok, err = coroutine.resume(thread)
    end
    if not ok and err and not tostring(err):find("UnwindThrowable") then
        print('[ERROR][task.spawn] Coroutine error: ' .. tostring(err))
    end
    return thread
end

function task.defer(...)
    local fn, args = parseTaskArgs(...)
    if type(fn) ~= 'function' then
        error('[task.defer] Expected function, got ' .. type(fn))
    end
    table.insert(deferred, { fn = fn, args = args })
end

local function getTimeClock()
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
    table.insert(delays, { time = getTimeClock() + sec, fn = fn, args = passArgs })
end

function task.wait(...)
    local rawArgs = {...}
    local sec = rawArgs[1]
    if type(sec) == 'table' then
        sec = rawArgs[2]
    end
    sec = tonumber(sec) or 0
    local thread = coroutine.running()
    task.delay(sec, function()
        if thread then coroutine.resume(thread) end
    end)
    return coroutine.yield()
end

function task._tick()
    local def = deferred
    deferred = {}
    for _, item in ipairs(def) do
        if type(item.fn) == 'function' then
            local ok, err = pcall(item.fn, table.unpack(item.args or {}))
            if not ok and err and not tostring(err):find("UnwindThrowable") then
                print('[ERROR][task._tick] Deferred task error: ' .. tostring(err))
            end
        end
    end
    local now = getTimeClock()
    local remaining = {}
    for _, item in ipairs(delays) do
        if now >= item.time then
            if type(item.fn) == 'function' then
                local ok, err = pcall(item.fn, table.unpack(item.args or {}))
                if not ok and err and not tostring(err):find("UnwindThrowable") then
                    print('[ERROR][task._tick] Delay task error: ' .. tostring(err))
                end
            end
        else
            table.insert(remaining, item)
        end
    end
    delays = remaining
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
    connection = self:Connect(function(...)
        connection:Disconnect()
        fn(...)
    end)
    return connection
end

function Signal:Fire(...)
    self._listeners = self._listeners or {}
    local args = {...}
    print("[Signal] Firing signal with listener count: " .. tostring(#self._listeners))
    for _, l in ipairs(self._listeners) do
        if l.connected and type(l.fn) == 'function' then
            print("[Signal] Invoking listener callback...")
            task.spawn(l.fn, table.unpack(args))
        end
    end
end
Signal.fire = Signal.Fire

function Signal:Wait()
    local runningThread = coroutine.running()
    local conn
    conn = self:Connect(function(...)
        conn:Disconnect()
        coroutine.resume(runningThread, ...)
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

function RemoteFunction:new(name, javaNetworkService)
    local self = setmetatable({}, RemoteFunction)
    self.Name = name
    self.OnServerInvoke = nil
    self.OnClientInvoke = nil
    self._javaService = javaNetworkService
    return self
end

function RemoteFunction:InvokeServer(...)
    local args = {...}
    local net = _G.NetworkService
    if net and type(net.InvokeServer) == "function" then
        return net:InvokeServer(self.Name, args)
    end
end

function RemoteFunction:InvokeClient(player, ...)
    local args = {...}
    local uuid = ""
    if type(player) == "string" then
        uuid = player
    elseif type(player) == "table" and type(player.getUuid) == "function" then
        uuid = player:getUuid()
    end
    local net = _G.NetworkService
    if net and type(net.InvokeClient) == "function" then
        return net:InvokeClient(self.Name, uuid, args)
    end
end

_G.RemoteFunction = RemoteFunction

-- ===================================================================
-- USER INPUT & RUN SERVICE APIS
-- ===================================================================

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
    self.EasingStyle = easingStyle or "Linear"
    self.EasingDirection = easingDirection or "Out"
    self.RepeatCount = repeatCount or 0
    self.Reverses = reverses or false
    self.DelayTime = delayTime or 0
    return self
end

_G.TweenInfo = TweenInfo

local TweenService = {}

function TweenService:Create(instance, tweenInfo, targetProperties)
    local tween = {
        _completedSignal = Signal.new(),
        Completed = Signal.new()
    }
    function tween:Play()
        local duration = tweenInfo and tweenInfo.Time or 1.0
        task.delay(duration, function()
            tween.Completed:Fire()
        end)
    end
    function tween:Cancel()
    end
    return tween
end

_G.TweenService = TweenService

print("[LuaTweaker] System Bootstrap Library loaded successfully!")
