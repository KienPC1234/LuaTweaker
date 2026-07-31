local Client = require("LuaTweaker.Client")
local Network = require("LuaTweaker.Network")
local ClientEffects = require("LuaTweaker.ClientEffects")

print("[Client] Initializing Magic Staff Client Visual Effects Listener...")

if Client and Client.OnKeyBindPressed then
    Client.OnKeyBindPressed:Connect(function(keyBindId, payload)
        print("[Client] KeyMapping Activated on Client! ID: " .. tostring(keyBindId) .. ", Payload: " .. tostring(payload))
        if keyBindId == "magic_staff_cast" or payload == "StaffCastSkill" then
            local castEvent = Network.GetOrCreateRemoteEvent("StaffCastSkill")
            if castEvent then
                print("[Client] Firing StaffCastSkill RemoteEvent from Client...")
                castEvent:FireServer()
            end
        elseif keyBindId == "magic_staff_switch" or keyBindId == "staff_swap_skill" or payload == "StaffSwapSkill" then
            local swapEvent = Network.GetOrCreateRemoteEvent("StaffSwapSkill")
            if swapEvent then
                print("[Client] Firing StaffSwapSkill RemoteEvent from Client...")
                swapEvent:FireServer()
            end
        end
    end)
end

if Network then
    local skillEffectEvent = Network.GetOrCreateRemoteEvent("StaffSkillEffectClient")
    if skillEffectEvent and skillEffectEvent.OnClientEvent then
        skillEffectEvent.OnClientEvent:Connect(function(effectType)
            print("[Client] Magic Staff skill effect received from server: " .. tostring(effectType))
            if effectType == "fireball" then
                if ClientEffects then ClientEffects:FlashScreen("0xFFFF5500", 0.2) end
            elseif effectType == "summon" then
                if ClientEffects then ClientEffects:FlashScreen("0xFF55FF55", 0.2) end
            elseif effectType == "aegis" then
                if ClientEffects then ClientEffects:FlashScreen("0xFF55FFFF", 0.2) end
            end
        end)
    end
end
