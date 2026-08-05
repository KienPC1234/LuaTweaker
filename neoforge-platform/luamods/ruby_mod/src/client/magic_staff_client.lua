-- ===================================================================
-- Magic Staff: Client-Side HUD, Target Outline, Keybind Feedback & Effects
-- ===================================================================
local Client = require("LuaTweaker.Client")
local Network = require("LuaTweaker.Network")
local ClientEffects = require("LuaTweaker.ClientEffects")
local GuiService = require("LuaTweaker.GuiService")
local RenderService = require("LuaTweaker.RenderService")

print("[Client] Initializing Magic Staff Client Visual Effects & HUD Listener...")

-- PLAYER STATE MIRROR (synced from the server via StaffManaSync)
local currentMana = 100
local maxMana = 100
local activeSkill = "Ruby Orb Fireball"
local hudVisible = false
local flashTicks = 0

-- TARGET MARKER (client-only border box around the entity marked with [X])
local markedTargetUuid = nil

-- CONFIG-DRIVEN CLIENT TUNING
local cfg = mod and mod:GetConfig() or {}
local TARGET_OUTLINE_COLOR = tonumber(cfg.target_outline_color) or 4294902015

-- HUD GEOMETRY CONSTANTS
local PANEL_WIDTH = 170
local PANEL_HEIGHT = 64
local BAR_WIDTH = 146
local BAR_HEIGHT = 10
local PADDING = 12
local PANEL_BG = 0xCC111827
local PANEL_BORDER = 0xFF38BDF8
local BAR_BG = 0xFF1E293B
local BAR_FILL = 0xFF0284C7
local TEXT_COLOR = 0xFFFFFFFF
local TITLE_COLOR = 0xFFFDE047
local FLASH_COLOR = 0x88FFFFFF
local FLASH_DURATION = 10

-- PER-SKILL HUD ACCENTS
local SKILL_ACCENTS = {
    ["Ruby Orb Fireball"] = 0xFF38BDF8,
    ["Summon Ruby Guardian"] = 0xFF4ADE80,
    ["Aegis Shield Barrier"] = 0xFFF472B6,
    ["Homing Ruby Dart"] = 0xFFC084FC
}

-- PRIVATE FUNCTIONS
local function getSkillAccent()
    return SKILL_ACCENTS[activeSkill] or PANEL_BORDER
end

local function getManaPercent()
    return math.clamp(currentMana / math.max(1, maxMana), 0, 1)
end

-- KEYBIND FEEDBACK (the server receives keybind casts via the payload packet
-- sent by the Java KeyMapping handler, so this listener only plays client-side
-- feedback — it must never re-fire the server, or every key press would cast
-- the skill twice).
if Client and Client.OnKeyBindPressed then
    Client.OnKeyBindPressed:Connect(function(keyBindId, payload)
        print("[Client] KeyMapping feedback: " .. tostring(keyBindId) .. " (" .. tostring(payload) .. ")")
        flashTicks = FLASH_DURATION
    end)
end

-- SERVER -> CLIENT SKILL EFFECT FLASHES
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
            elseif effectType == "homing" then
                if ClientEffects then ClientEffects:FlashScreen("0xFFC084FC", 0.2) end
            end
        end)
    end

    -- MANA & ACTIVE SKILL SYNC (isHolding = false hides the HUD)
    local syncEvent = Network:GetOrCreateRemoteEvent("StaffManaSync")
    if syncEvent and syncEvent.OnClientEvent then
        syncEvent.OnClientEvent:Connect(function(mana, maxM, skill, isHolding)
            if mana then currentMana = tonumber(mana) or currentMana end
            if maxM then maxMana = tonumber(maxM) or maxMana end
            if skill then activeSkill = tostring(skill) end
            hudVisible = isHolding == true
        end)
    end

    -- TARGET MARKED: draw a client-only border box around the marked entity
    local markSyncEvent = Network:GetOrCreateRemoteEvent("TargetMarked")
    if markSyncEvent and markSyncEvent.OnClientEvent then
        markSyncEvent.OnClientEvent:Connect(function(uuid)
            print("[Client] Target marked: " .. tostring(uuid))
            if uuid and tostring(uuid) ~= "" then
                markedTargetUuid = tostring(uuid)
            else
                markedTargetUuid = nil
            end
        end)
    end
end

-- RENDER CLIENT MANA BAR HUD ON SCREEN EVERY FRAME
if GuiService and GuiService.OnRenderHUD then
    GuiService.OnRenderHUD:Connect(function(dt)
        if not hudVisible then return end
        if flashTicks > 0 then flashTicks = flashTicks - 1 end

        local size = GuiService:GetScreenSize()
        local screenWidth = size.Width or 0
        local startX = math.floor((screenWidth - PANEL_WIDTH) / 2)
        local startY = 14

        -- Panel Background (Dark Glassmorphic Container)
        GuiService:DrawRect(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, PANEL_BG)
        -- Accent Border (per-skill color, glowing after keybind feedback)
        local borderColor = flashTicks > 0 and FLASH_COLOR or getSkillAccent()
        GuiService:DrawOutline(startX, startY, PANEL_WIDTH, PANEL_HEIGHT, borderColor)

        -- Skill Title (centered)
        GuiService:DrawTextCentered("Skill: " .. tostring(activeSkill), startX + PANEL_WIDTH / 2, startY + 6, TITLE_COLOR, true)

        -- Mana Progress Bar
        local barX = startX + PADDING
        local barY = startY + 22
        GuiService:DrawProgressBar(barX, barY, BAR_WIDTH, BAR_HEIGHT, getManaPercent(), BAR_BG, BAR_FILL)

        -- Mana Numbers Overlay
        local manaText = string.format("%d / %d MP", currentMana, maxMana)
        GuiService:DrawTextCentered(manaText, startX + PANEL_WIDTH / 2, barY + 1, TEXT_COLOR, true)

        -- Keybind Hint Footer
        GuiService:DrawTextCentered("[G] Cast | [Z] Swap | [X] Mark Target", startX + PANEL_WIDTH / 2, startY + PANEL_HEIGHT - 10, 0xFF94A3B8, true)
    end)
end

-- WORLD-SPACE TARGET OUTLINE — computed in pure Lua from client-side entity data.
-- Java only provides primitives (DrawLine/DrawBox/GetEntity/WorldToScreen); all
-- shape logic, margins, colors and labels live here.

local outlineDebugState = nil

---@param uuid string
---@param color number
local function drawTargetOutline(uuid, color)
    local ent = RenderService:GetEntity(uuid)
    if not ent then
        if outlineDebugState ~= "missing" then
            print("[Client] Outline entity NOT FOUND for uuid: " .. tostring(uuid))
            outlineDebugState = "missing"
        end
        return
    end
    if outlineDebugState ~= "ok" then
        print("[Client] Outline entity found: " .. tostring(ent.Name) .. " (" .. tostring(ent.Type) .. ") @ " .. string.format("%.1f, %.1f, %.1f", ent.X, ent.Y, ent.Z))
        outlineDebugState = "ok"
    end
    local margin = 0.12
    local halfW = ent.Width / 2 + margin
    RenderService:DrawBox(
        ent.X - halfW, ent.Y - margin, ent.Z - halfW,
        ent.X + halfW, ent.Y + ent.Height + margin, ent.Z + halfW,
        color
    )
    -- Name label above the box (projected to screen space)
    local labelPos = RenderService:WorldToScreen(ent.X, ent.Y + ent.Height + 0.6, ent.Z)
    if labelPos and labelPos.Visible then
        GuiService:DrawTextCentered(ent.Name, math.floor(labelPos.X), math.floor(labelPos.Y), color, true)
    end
end

if Client and Client.OnRenderWorld and RenderService then
    Client.OnRenderWorld:Connect(function(dt)
        if markedTargetUuid then
            drawTargetOutline(markedTargetUuid, TARGET_OUTLINE_COLOR)
        else
            outlineDebugState = nil
        end
    end)
end
