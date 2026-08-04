-- ================================================================
-- ARCANE RPG: Client HUD
-- Mana bar + Skill cooldown indicators
-- ================================================================
local Events = require("LuaTweaker.Events")
local GuiService = require("LuaTweaker.GuiService")
local Network = require("LuaTweaker.Network")

local function getConfig()
    return mod:GetConfig()
end

local skillSyncEvent = Network:GetOrCreateRemoteEvent("ArcaneSkillSync")
local lastCastSkill = nil
local lastCastTime = 0

skillSyncEvent.OnClientEvent:Connect(function(skillName, action)
    if action == "cast" then
        lastCastSkill = skillName
        lastCastTime = task.getTimeClock()
    end
end)

local skillOrder = { "crystal_bolt", "frost_nova", "arcane_shield", "meteor_strike" }

local skillDisplayNames = {
    crystal_bolt = "§b1",
    frost_nova = "§b2",
    arcane_shield = "§b3",
    meteor_strike = "§b4"
}

local skillColors = {
    crystal_bolt = 0x44AAFF,
    frost_nova = 0x88DDFF,
    arcane_shield = 0x6644CC,
    meteor_strike = 0xFF4400
}

Events:Listen("OnRenderHUD", function(guiGraphics)
    local cfg = getConfig()
    local hudCfg = cfg.hud

    local screenWidth, screenHeight = GuiService:GetScreenSize()

    -- ==== MANA BAR ====
    local barX = hudCfg.mana_bar_x
    local barY = hudCfg.mana_bar_y
    local barW = hudCfg.mana_bar_width
    local barH = hudCfg.mana_bar_height

    GuiService:DrawRect(barX - 1, barY - 1, barW + 2, barH + 2, 0x000000)
    GuiService:DrawRect(barX, barY, barW, barH, hudCfg.mana_bar_bg)

    local manaPercent = 1.0
    local Storage = require("LuaTweaker.Storage")
    local sessionStorage = Storage:GetSessionStorage()
    local cachedMana = sessionStorage:GetAsync("arcane_rpg.client.mana")
    local cachedMax = sessionStorage:GetAsync("arcane_rpg.client.max_mana")
    if cachedMana ~= nil and cachedMax ~= nil and cachedMax > 0 then
        manaPercent = cachedMana / cachedMax
    end

    local fillWidth = math.floor(barW * manaPercent)
    if fillWidth > 0 then
        GuiService:DrawRect(barX, barY, fillWidth, barH, hudCfg.mana_bar_color)
    end

    local manaText = ""
    if cachedMana ~= nil and cachedMax ~= nil then
        manaText = string.format("%.0f/%.0f", cachedMana, cachedMax)
    else
        manaText = "Mana"
    end
    GuiService:DrawText(manaText, barX + barW / 2 - 10, barY + 1, 0xFFFFFF, true)

    -- ==== SKILL COOLDOWN ICONS ====
    local iconSize = hudCfg.skill_icon_size
    local spacing = hudCfg.skill_icon_spacing
    local totalWidth = #skillOrder * iconSize + (#skillOrder - 1) * spacing
    local startX = (screenWidth - totalWidth) / 2
    local iconY = screenHeight - iconSize - 10

    for i = 1, #skillOrder do
        local skillName = skillOrder[i]
        local iconX = startX + (i - 1) * (iconSize + spacing)
        local color = skillColors[skillName] or 0x444444

        GuiService:DrawRect(iconX - 1, iconY - 1, iconSize + 2, iconSize + 2, 0x000000)
        GuiService:DrawRect(iconX, iconY, iconSize, iconSize, color)

        local displayName = skillDisplayNames[skillName] or "?"
        GuiService:DrawTextCentered(displayName, iconX + iconSize / 2, iconY + 6, 0xFFFFFF, true)
    end

    -- ==== CAST FEEDBACK ====
    if lastCastSkill ~= nil and task.getTimeClock() - lastCastTime < 2.0 then
        local fadeAlpha = 1.0 - (task.getTimeClock() - lastCastTime) / 2.0
        local name = lastCastSkill:gsub("_", " ")
        name = name:gsub("^%l", string.upper)
        GuiService:DrawTextCentered(
            "§b" .. name .. "!",
            screenWidth / 2,
            screenHeight / 2 - 40,
            0xFFFFFF,
            true
        )
    end

    -- ==== DEBUG INFO ====
    if hudCfg.show_debug then
        GuiService:DrawText("Arcane RPG v1.0 | Skills: 4", 4, 4, 0xAAAAAA, false)
        GuiService:DrawText(
            string.format("Mana: %.0f%%", manaPercent * 100),
            4, 14, 0x4488FF, false
        )
    end
end)

print("[ArcaneRPG] Client HUD loaded: Mana bar + 4 skill slots")
