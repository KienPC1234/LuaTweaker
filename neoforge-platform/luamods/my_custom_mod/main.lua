-- ========================================================
-- MOD ID: my_custom_mod | ENTRYPOINT: main.lua
-- Tests the cross-mod container API + server command primitive:
--   World:ExecuteCommand          - run a server console command from Lua
--   World:GetBlockState           - read block id + properties of ANY mod's block
--   World:GetBlockEntityData      - read NBT of ANY mod's container block entity
--   World:SetBlockEntityData      - write NBT (merge-safe) on ANY container
--   World:SetBlockState           - toggle blockstate properties
--   World:EjectContainerItem      - eject items out of ANY container
-- Target: 'luatweaker:wood_crate' registered by the SEPARATE ruby_mod.
-- ========================================================

-- 1. NAP MODULE TINH
local Content = require("LuaTweaker.Content")
local Events  = require("LuaTweaker.Events")
local Task    = require("LuaTweaker.Task")
local World   = require("LuaTweaker.World")
local Players = require("LuaTweaker.Players")

-- 2. NAP MODULE NOI BO
local BossAI  = require(".src.server.boss_ai")
if BossAI and BossAI.Initialize then
    BossAI.Initialize()
end

-- 3. GIAI DOAN STARTUP: DANG KY VAT PHAM
local CustomSword = Content.NewItem("my_custom_mod:shadow_blade")
    :DisplayName("Lua Kiem Bong Dem")
    :AttackDamage(12.0)
    :Register()

-- 4. SERVER COMMAND PRIMITIVE (test qua mod nay, khong phai ruby_mod)
-- Delayed: commands need an active world (during startup overworld is null).
Task.spawn(function()
    Task.wait(5)
    local cmdOk = World:ExecuteCommand("say [MyCustomMod] ExecuteCommand works from Lua")
    print("[MyCustomMod] World:ExecuteCommand -> " .. tostring(cmdOk))
end)

-- 5. GIAI DOAN RUNTIME: LANG NGHE SU KIEN
if Events and Events.OnEntityDamaged then
    Events.OnEntityDamaged:Connect(function(event)
        local attacker = event.Attacker
        local target   = event.Target
        if attacker and attacker:GetHeldItem() and attacker:GetHeldItem().Id == "my_custom_mod:shadow_blade" then
            Task:delay(0.5, function()
                if target and target:IsAlive() then
                    target:TakeDamage(5.0, "magic")
                end
            end)
        end
    end)
end

-- 6. CROSS-MOD CONTAINER TEST: thao tac tren wood_crate CUA ruby_mod
local CRATE_ID = "luatweaker:wood_crate"
local TEST_ITEM = "minecraft:stick"
local CHECK_INTERVAL = 5

local function testCrate(dim, x, y, z)
    -- a) Doc blockstate tu mod khac
    local state = World:GetBlockState(dim, x, y, z)
    if not state or state.Id ~= CRATE_ID then return end
    print("[MyCustomMod] GetBlockState -> " .. tostring(state.Id) ..
        " | open=" .. tostring(state.Properties and state.Properties.open or "?"))

    -- b) Doc NBT tu mod khac (so luong item ben trong)
    local data = World:GetBlockEntityData(dim, x, y, z)
    if not data then
        print("[MyCustomMod] GetBlockEntityData -> nil (chua co block entity?)")
        return
    end
    local items = data.Items or {}
    print("[MyCustomMod] GetBlockEntityData -> items=" .. #items)

    -- c) Ghi NBT merge-safe tu mod khac: them 1 stick vao slot 0 neu slot rong
    local slot0 = items[1]
    if not slot0 then
        local okWrite = World:SetBlockEntityData(dim, x, y, z, {
            Items = { { id = TEST_ITEM, count = 1, slot = 0 } }
        })
        print("[MyCustomMod] SetBlockEntityData(stick -> slot 0) -> " .. tostring(okWrite))
        if okWrite then
            local after = World:GetBlockEntityData(dim, x, y, z)
            local afterItems = after and after.Items or {}
            print("[MyCustomMod] Verify read-back: items=" .. #afterItems ..
                " | first=" .. tostring(afterItems[1] and afterItems[1].id or "none"))
        end
    else
        -- d) Eject ra ngoai de giai phong slot 0 (mod khac cung rut duoc do)
        local okEject = World:EjectContainerItem(dim, x, y, z, 0, 1)
        print("[MyCustomMod] EjectContainerItem(slot 0) -> " .. tostring(okEject))
    end

    -- e) SetBlockState: toggle OPENED property cua crate
    local currentOpen = state.Properties and state.Properties.open == "true"
    local okToggle = World:SetBlockState(dim, x, y, z, CRATE_ID, { open = tostring(not currentOpen) })
    print("[MyCustomMod] SetBlockState(open=" .. tostring(not currentOpen) .. ") -> " .. tostring(okToggle))
end

Task.spawn(function()
    while true do
        Task.wait(CHECK_INTERVAL)
        local ok, err = pcall(function()
            local online = Players and Players.GetPlayers and Players:GetPlayers()
            if not online then return end
            for _, player in ipairs(online) do
                local px, py, pz = player:getX(), player:getY(), player:getZ()
                local dim = player:getDimension()
                for dx = -1, 1 do
                    for dy = -1, 1 do
                        for dz = -1, 1 do
                            testCrate(dim, math.floor(px) + dx, math.floor(py) + dy, math.floor(pz) + dz)
                        end
                    end
                end
            end
        end)
        if not ok and err then
            print("[ERROR] [MyCustomMod] Container test loop error: " .. tostring(err))
        end
    end
end)

-- 7. VONG DOI MOD
function mod.OnEnable()
    local cfg = mod:GetConfig()
    print("[MyCustomMod] Successfully loaded! Debug Mode: " .. tostring(cfg and cfg.debug_mode))
end
