-- ================================================================
-- ARCANE RPG: Client HUD
-- Mana bar + Skill cooldown indicators
-- ================================================================
local GuiService = require("LuaTweaker.GuiService")
local Network = require("LuaTweaker.Network")
local Storage = require("LuaTweaker.Storage")
local sessionStorage = Storage:GetSessionStorage()

local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

local function getConfig()
    return CONFIG
end

local skillSyncEvent = Network:GetOrCreateRemoteEvent("ArcaneSkillSync")
local manaSyncEvent = Network:GetOrCreateRemoteEvent("ArcaneManaSync")
local lastCastSkill = nil
local lastCastTime = 0

skillSyncEvent.OnClientEvent:Connect(function(skillName, action)
    if action == "cast" then
        lastCastSkill = skillName
        lastCastTime = task.getTimeClock()
    end
end)

-- Update cached mana whenever the server broadcasts it (server fires this every ~1s).
manaSyncEvent.OnClientEvent:Connect(function(mana, maxMana)
    if mana ~= nil then
        sessionStorage:SetAsync("arcane_rpg.client.mana", mana)
    end
    if maxMana ~= nil then
        sessionStorage:SetAsync("arcane_rpg.client.max_mana", maxMana)
    end
end)

local skillOrder = { "crystal_bolt", "frost_nova", "arcane_shield", "meteor_strike" }

local skillDisplayNames = {
    crystal_bolt = "1",
    frost_nova = "2",
    arcane_shield = "3",
    meteor_strike = "4"
}

local skillColors = {
    crystal_bolt = 0x44AAFF,
    frost_nova = 0x88DDFF,
    arcane_shield = 0x6644CC,
    meteor_strike = 0xFF4400
}

GuiService.OnRenderHUD:Connect(function(dt)
    local cfg = getConfig()
    local hudCfg = cfg.hud

    -- HUD can be fully disabled via luaconfig/arcane_rpg.json (hud.enabled).
    -- The mana bar + skill slot numbers ("1".."4") are off by default.
    if hudCfg == nil or hudCfg.enabled == false then
        return
    end

    local size = GuiService:GetScreenSize()
    local screenWidth, screenHeight = 0, 0
    if type(size) == "table" or type(size) == "userdata" then
        screenWidth = size.Width or size[1] or size[0] or 1920
        screenHeight = size.Height or size[2] or size[1] or 1080
    end
    if type(screenWidth) == "table" or type(screenWidth) == "userdata" then
        screenWidth = 1920 -- Fallback if somehow width is a table
    end
    if type(screenHeight) == "table" or type(screenHeight) == "userdata" then
        screenHeight = 1080
    end

    -- ==== MANA BAR ====
    local barX = hudCfg.mana_bar_x
    local barY = hudCfg.mana_bar_y
    local barW = hudCfg.mana_bar_width
    local barH = hudCfg.mana_bar_height

    GuiService:DrawRect(barX - 1, barY - 1, barW + 2, barH + 2, 0x000000)
    GuiService:DrawRect(barX, barY, barW, barH, hudCfg.mana_bar_bg)

    local manaPercent = 1.0
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
        local name = lastCastSkill:gsub("_", " ")
        name = name:gsub("^%l", string.upper)
        GuiService:DrawTextCentered(
            name .. "!",
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
