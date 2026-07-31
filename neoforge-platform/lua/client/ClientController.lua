-- ===================================================================
-- Roblox Client Main Controller Script (LuaTweaker Client Architecture)
-- Handles LocalPlayer, Keybinds, RemoteEvents, and Render Loop
-- ===================================================================

print("Initializing ClientController.lua...")

-- SERVICES
local NetworkService = Mod:GetService("NetworkService")

-- CONSTANTS
local MAX_PING_ATTEMPTS = 5
local KEY_TRIGGER_ACTION = "G"

-- STATE VARIABLES
local isClientActive = true
local currentPingCount = 0

-- PRIVATE FUNCTIONS

--- Handles incoming score notifications sent by the server
---@param newScore number
---@param reason string
local function onScoreUpdated(newScore, reason)
    print("[Client] Score updated by server: newScore=" .. tostring(newScore) .. " (" .. tostring(reason) .. ")")
end

--- Handles user keyboard input events
---@param inputObject any
---@param isProcessed boolean
local function onInputBegan(inputObject, isProcessed)
    if isProcessed then
        return
    end

    print("[Client] InputBegan event detected: KeyCode=" .. tostring(inputObject))

    -- Trigger RemoteEvent to request server action
    local actionEvent = NetworkService:GetOrCreateRemoteEvent("PlayerActionEvent")
    actionEvent:FireServer("KeyPressed", KEY_TRIGGER_ACTION)
end

-- REMOTE EVENT SIGNALS SETUP
local scoreEvent = NetworkService:GetOrCreateRemoteEvent("ScoreUpdate")
scoreEvent.OnClientEvent:Connect(onScoreUpdated)

-- REMOTE FUNCTION INVOKE SETUP
local requestHealthFunc = NetworkService:GetOrCreateRemoteFunction("RequestPlayerHealth")
requestHealthFunc.OnClientInvoke = function()
    print("[Client] Server invoked OnClientInvoke on LocalPlayer")
    return 20.0
end

-- USER INPUT SERVICE BINDING
if UserInputService then
    local swapSkillEvent = NetworkService:GetOrCreateRemoteEvent("StaffSwapSkill")

    UserInputService.InputBegan:Connect(function(inputObject, isProcessed)
        if isProcessed then return end
        
        local keyVal = tostring(inputObject)
        -- GLFW_KEY_Z = 90, GLFW_KEY_V = 86, GLFW_KEY_F = 70
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

-- PLAYER LOCAL PLAYER CHECK
if Players and Players.LocalPlayer then
    print("[Client] LocalPlayer connected: " .. tostring(Players.LocalPlayer))
else
    print("[Client] Running in standalone client test environment")
end

print("ClientController.lua initialized successfully!")
