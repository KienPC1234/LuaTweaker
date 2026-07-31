-- ===================================================================
-- Client Main Controller Script (LuaTweaker Client Architecture)
-- Handles LocalPlayer, Keybinds, RemoteEvents, and Render Loop
-- ===================================================================
local Network = require("LuaTweaker.Network")

print("Initializing ClientController.lua...")

-- CONSTANTS
local MAX_PING_ATTEMPTS = 5
local KEY_TRIGGER_ACTION = "G"

-- STATE VARIABLES
local currentPingCount = 0

-- PRIVATE FUNCTIONS
local function onScoreUpdated(newScore, reason)
    print("[Client] Score updated by server: newScore=" .. tostring(newScore) .. " (" .. tostring(reason) .. ")")
end

-- REMOTE EVENT SIGNALS SETUP
local scoreEvent = Network.GetOrCreateRemoteEvent("ScoreUpdate")
scoreEvent.OnClientEvent:Connect(onScoreUpdated)

-- REMOTE FUNCTION INVOKE SETUP
local requestHealthFunc = Network.GetOrCreateRemoteFunction("RequestPlayerHealth")
requestHealthFunc.OnClientInvoke = function()
    print("[Client] Server invoked OnClientInvoke on LocalPlayer")
    return 20.0
end

-- USER INPUT SERVICE BINDING
if UserInputService then
    local swapSkillEvent = Network.GetOrCreateRemoteEvent("StaffSwapSkill")

    UserInputService.InputBegan:Connect(function(inputObject, isProcessed)
        if isProcessed then return end
        
        local keyVal = tostring(inputObject)
        if inputObject == 90 or inputObject == 86 or keyVal == "90" or keyVal == "86" or keyVal:upper() == "Z" or keyVal:upper() == "V" then
            print("[Client] Skill Swap Key (Z/V) pressed! Requesting Magic Staff Skill Swap...")
            swapSkillEvent:FireServer()
        end
    end)
end

-- RUN SERVICE RENDER STEPPED BINDING
if RunService then
    print("[Client] Subscribing to RunService.RenderStepped signal...")
    RunService.RenderStepped:Connect(function(deltaTime)
        if currentPingCount < MAX_PING_ATTEMPTS then
            currentPingCount = currentPingCount + 1
        end
    end)
end

print("ClientController.lua initialized successfully!")
