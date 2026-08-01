local Client = require("LuaTweaker.Client")
local Network = require("LuaTweaker.Network")
local ClientEffects = require("LuaTweaker.ClientEffects")
local GuiService = require("LuaTweaker.GuiService")

print("[Client] Initializing Magic Staff Client Visual Effects & HUD Listener...")

local currentMana = 100
local maxMana = 100
local activeSkill = "Ruby Orb Fireball"
local hudVisible = true

if Client and Client.OnKeyBindPressed then
    Client.OnKeyBindPressed:Connect(function(keyBindId, payload)
        print("[Client] KeyMapping Activated on Client! ID: " .. tostring(keyBindId) .. ", Payload: " .. tostring(payload))
        if keyBindId == "magic_staff_cast" or payload == "StaffCastSkill" then
            local castEvent = Network:GetOrCreateRemoteEvent("StaffCastSkill")
            if castEvent then
                print("[Client] Firing StaffCastSkill RemoteEvent from Client...")
                castEvent:FireServer()
            end
        elseif keyBindId == "magic_staff_switch" or keyBindId == "staff_swap_skill" or payload == "StaffSwapSkill" then
            local swapEvent = Network:GetOrCreateRemoteEvent("StaffSwapSkill")
            if swapEvent then
                print("[Client] Firing StaffSwapSkill RemoteEvent from Client...")
                swapEvent:FireServer()
            end
        end
    end)
end

if Network then
    local skillEffectEvent = Network:GetOrCreateRemoteEvent("StaffSkillEffectClient")
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

    local syncEvent = Network:GetOrCreateRemoteEvent("StaffManaSync")
    if syncEvent and syncEvent.OnClientEvent then
        syncEvent.OnClientEvent:Connect(function(mana, maxM, skill)
            if mana then currentMana = tonumber(mana) or currentMana end
            if maxM then maxMana = tonumber(maxM) or maxMana end
            if skill then activeSkill = tostring(skill) end
            hudVisible = true
        end)
    end
end

-- RENDER CLIENT MANA BAR HUD ON SCREEN EVERY FRAME
if GuiService and GuiService.OnRenderHUD then
    GuiService.OnRenderHUD:Connect(function(dt)
        if not hudVisible then return end

        local startX = 10
        local startY = 10
        local barWidth = 140
        local barHeight = 12

        -- Background Frame (Dark Glassmorphic container: 0xCC111827)
        GuiService:DrawRect(startX, startY, barWidth + 8, barHeight + 20, 0xCC111827)
        -- Outer Border (Cyan Accent: 0xFF38BDF8)
        GuiService:DrawRect(startX, startY, barWidth + 8, 1, 0xFF38BDF8)

        -- Skill Title Text
        GuiService:DrawText("Skill: " .. tostring(activeSkill), startX + 4, startY + 4, 0xFFFDE047, true)

        -- Mana Bar Background Track (0xFF1E293B)
        local barY = startY + 16
        GuiService:DrawRect(startX + 4, barY, barWidth, barHeight, 0xFF1E293B)

        -- Filled Mana Progress Bar (Cyan Gradient: 0xFF0284C7)
        local fillPercent = math.clamp(currentMana / math.max(1, maxMana), 0, 1)
        local fillWidth = math.floor(barWidth * fillPercent)
        if fillWidth > 0 then
            GuiService:DrawRect(startX + 4, barY, fillWidth, barHeight, 0xFF0284C7)
        end

        -- Mana Numbers Overlay Text
        local manaText = string.format("%d / %d MP", currentMana, maxMana)
        GuiService:DrawText(manaText, startX + 8, barY + 2, 0xFFFFFFFF, true)
    end)
end
