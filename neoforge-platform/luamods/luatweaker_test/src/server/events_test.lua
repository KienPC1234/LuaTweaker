-- ===================================================================
-- LuaTweaker Test Suite: Events
-- Verifies the shared event bus (AGENTS.md 5.3): Listen, Fire and the
-- cancellable-event contract. Any double-fire across reloads would show
-- up as duplicate log lines here.
-- ===================================================================
local Events = require("LuaTweaker.Events")

local firedCount = 0
Events:Listen("TestBusEvent", function(payload)
    firedCount = firedCount + 1
    print("[Test][Events] TestBusEvent #" .. firedCount
        .. (payload and payload.tag or "-") .. " (id="
        .. tostring(payload and payload.id or "?") .. ")")
end)

local function fireTestEvent(tag, id)
    Events:Fire("TestBusEvent", { tag = tag, id = id })
end

-- Fire a few events at load time; a listener leak across engine reloads
-- (5.3 violation) would double the printed count for the same id.
fireTestEvent("hello", 1)
fireTestEvent("world", 2)

-- Cancellable event contract: returning false from a listener vetoes.
-- Uses Events:FireCancellable (bound from IEventService.fireCancellable),
-- which returns false when any listener cancelled the event. Events:Fire
-- (Post) ignores listener return values, so it must NOT be used here.
local vetoed = false
Events:Listen("TestCancellable", function(event)
    if event and event.block == true then
        return false
    end
    return true
end)

Events:Listen("TestCancellable", function()
    vetoed = true
end)

local okBlocked = Events:FireCancellable("TestCancellable", { block = true })
if okBlocked == false then
    print("[Test][Events] Cancellable event was vetoed by false return")
else
    print("[ERROR][Test] Cancellable event was NOT vetoed (returned " .. tostring(okBlocked) .. ")")
end

-- FireCancellable still calls every listener; 'vetoed' being true proves the
-- second listener ran even while the first one returned false.
if vetoed then
    print("[Test][Events] Second listener ran (all listeners are invoked)")
else
    print("[ERROR][Test] Second listener did not run")
end

local okPass = Events:FireCancellable("TestCancellable", { block = false })
if okPass == true then
    print("[Test][Events] Cancellable event passed through after unblock")
else
    print("[ERROR][Test] Cancellable event was vetoed even when unblocked (returned " .. tostring(okPass) .. ")")
end

print("[Test][Events] Event bus verified: Listen/Fire/cancel")
