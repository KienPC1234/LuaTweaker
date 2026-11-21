-- ================================================================
-- LuaTweaker Test: ImGui demo (advanced client-side control panel)
--
-- A live control panel rendered by Dear ImGui: sliders, checkboxes, text
-- fields, a dropdown, a color picker and clickable item-icon slots.
-- Everything runs inside ImGui.OnFrame (one signal per render tick);
-- calling any widget outside that signal is a Lua error by design.
-- Item slots are preview/click surfaces only: clicks print to chat-side
-- log here, real inventory moves stay in server scripts via Network.
-- ================================================================
local ImGui = require("LuaTweaker.ImGui")

-- A universal mod's main.lua also runs on a dedicated server, where there is
-- no client. Bail out instead of failing the whole mod load.
if ImGui:GetVersion() == "unavailable" then
    print("[LuaTweakerTest] ImGui demo skipped: no client on this side")
    return true
end

local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

local function getDemoCfg()
    local cfg = CONFIG
    if cfg ~= nil and type(cfg) == "table" and type(cfg.imgui_demo) == "table" then
        return cfg.imgui_demo
    end
    return {}
end

local demoCfg = getDemoCfg()
if demoCfg.enabled == false then
    return true
end

local showPanel = demoCfg.open_by_default
if showPanel == nil then
    showPanel = true
end
local flow = tonumber(demoCfg.default_flow) or 50.0
local label = "arcane"
local mode = 1
local MODES = { "Gentle", "Focused", "Overdrive" }
local presses = 0
local pickedItem = "none"
local flowHistory = {}

local PANEL_FLAGS = "NoCollapse,MenuBar"
if demoCfg.locked == true then
    PANEL_FLAGS = "NoCollapse,MenuBar,NoResize,NoMove"
end

ImGui.OnFrame:Connect(function()
    showPanel = ImGui:Begin("Arcane Panel", showPanel, PANEL_FLAGS)
    if not showPanel then
        ImGui:End()
        return
    end
    if ImGui:BeginTabBar("PanelTabs") then
        if ImGui:BeginTabItem("Controls") then
            ImGui:Text("Advanced controls served fresh every frame.")
            ImGui:Separator()
            flow = ImGui:SliderFloat("Mana flow", flow, 0.0, 100.0)
            ImGui:ProgressBar(flow / 100.0, MODES[mode] or "")
            if ImGui:CollapsingHeader("Tuning") then
                label = ImGui:InputText("Label", label)
                mode = ImGui:ListBox("Mode", mode, MODES)
            end
            flowHistory[#flowHistory + 1] = flow
            if #flowHistory > 60 then
                table.remove(flowHistory, 1)
            end
            ImGui:PlotLines("Flow history", flowHistory)
            ImGui:BeginDisabled(flow <= 0)
            if ImGui:Button("Pulse!") then
                presses = presses + 1
            end
            ImGui:EndDisabled()
            if ImGui:IsItemHovered() then
                ImGui:SetTooltip("Fires a pulse at the current flow")
            end
            ImGui:SameLine()
            ImGui:Text("pulses: " .. presses)
            ImGui:EndTabItem()
        end
        if ImGui:BeginTabItem("Kit") then
            if ImGui:BeginTable("KitTable", 2, "RowBg,Borders") then
                ImGui:TableSetupColumn("Icon")
                ImGui:TableSetupColumn("Item")
                ImGui:TableHeadersRow()
                local kit = {
                    { icon = "luatweaker_test:crystal_sword", name = "Crystal Sword x1" },
                    { icon = "luatweaker_test:mana_potion", name = "Mana Potion x3" },
                    { icon = "luatweaker_test:crystal_detector", name = "Crystal Detector x1" },
                }
                for i = 1, #kit do
                    ImGui:TableNextRow()
                    if ImGui:TableNextColumn() then
                        local count = kit[i].name:match("x(%d+)") or "1"
                        if ImGui:ItemIcon(kit[i].icon, 32, tonumber(count) or 1) then
                            pickedItem = kit[i].name
                        end
                        if ImGui:IsItemHovered() then
                            ImGui:SetTooltip(kit[i].name .. " - right-click for actions")
                        end
                        ImGui:OpenPopupOnItemClick("KitMenu")
                    end
                    if ImGui:TableNextColumn() then
                        ImGui:Text(kit[i].name)
                    end
                end
                ImGui:EndTable()
            end
            if ImGui:BeginPopupContextItem("KitMenu") then
                if ImGui:MenuItem("Inspect", "", false, true) then
                    pickedItem = "inspecting..."
                end
                if ImGui:MenuItem("Clear", "", false, pickedItem ~= "none") then
                    pickedItem = "none"
                end
                ImGui:EndPopup()
            end
            ImGui:Text("Picked: " .. pickedItem)
            ImGui:EndTabItem()
        end
        if ImGui:BeginTabItem("FX") then
            ImGui:Text("Live atlas sprite (animates itself):")
            local uv = ImGui:GetSpriteUv("minecraft:textures/atlas/blocks.png", "minecraft:block/fire_0")
            if uv ~= nil then
                ImGui:ImageSub("minecraft:textures/atlas/blocks.png", 32, 32, uv[1], uv[2], uv[3], uv[4])
            else
                ImGui:Text("(fire sprite unavailable here)")
            end
            ImGui:SameLine()
            ImGui:Image("minecraft:textures/gui/icons.png", 32, 32)
            ImGui:Text("Left: blocks atlas, right: static GUI texture.")
            ImGui:EndTabItem()
        end
        ImGui:EndTabBar()
    end
    if ImGui:BeginMenuBar() then
        if ImGui:BeginMenu("Panel") then
            ImGui:MenuItem("Version: " .. ImGui:GetVersion(), "", false, false)
            ImGui:EndMenu()
        end
        ImGui:EndMenuBar()
    end
    ImGui:End()
end)

print("[LuaTweakerTest] ImGui demo loaded: Arcane Panel renders every frame")
