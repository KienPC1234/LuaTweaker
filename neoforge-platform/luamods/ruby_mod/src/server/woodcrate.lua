-- ===================================================================
-- Wood Crate: Lua demo of the BlockState + BlockEntity NBT API, container
-- events and the server command primitive.
--  * 'ContainerOpened' / 'ContainerItemRejected' events come from Java
--    (shared bus; canonical names in Java EventNames).
--  * World:GetBlockState / World:GetBlockEntityData read live block/NBT data.
--  * World:ExecuteCommand runs a server console command from Lua.
-- ===================================================================
local World = require("LuaTweaker.World")
local Events = require("LuaTweaker.Events")
local Task = require("LuaTweaker.Task")
local Players = require("LuaTweaker.Players")

local cfg = mod and mod:GetConfig() or {}
local CHECK_INTERVAL = tonumber(cfg.woodcrate_check_interval) or 5
local COMMAND_DELAY = tonumber(cfg.woodcrate_cmd_delay) or 5
local SCAN_RADIUS = tonumber(cfg.woodcrate_scan_radius) or 1

local CRATE_ID = "luatweaker:wood_crate"

-- Event listeners (fired by Java when the container is used).
Events:Listen("ContainerOpened", function(payload)
    if payload and payload.Id == CRATE_ID then
        print("[WoodCrate] Opened by " .. tostring(payload.Player) ..
            " @ " .. tostring(payload.X) .. ", " .. tostring(payload.Y) .. ", " .. tostring(payload.Z))
    end
end)

Events:Listen("ContainerItemRejected", function(payload)
    if payload and payload.Id == CRATE_ID then
        print("[WoodCrate] REJECTED " .. tostring(payload.ItemId) .. " x" .. tostring(payload.Count) ..
            " (slot " .. tostring(payload.Slot) .. ") @ " .. tostring(payload.X) .. ", " .. tostring(payload.Y) .. ", " .. tostring(payload.Z))
    end
end)

-- Periodic inspection: stamp a Lua-owned NBT field + read the crate state.
local function inspectCrate(dim, x, y, z)
    local state = World:GetBlockState(dim, x, y, z)
    if not state or state.Id ~= CRATE_ID then return end
    local opened = state.Properties and state.Properties.open or "false"
    local data = World:GetBlockEntityData(dim, x, y, z)
    if not data then return end

    local owner = data.LuaData and data.LuaData.Owner
    if not owner then
        World:SetBlockEntityData(dim, x, y, z, {
            LuaData = { Owner = "ruby_mod" }
        })
        print("[WoodCrate] Stamped Owner on crate @ " .. x .. ", " .. y .. ", " .. z)
        return
    end
    print("[WoodCrate] Crate @ " .. x .. ", " .. y .. ", " .. z ..
        " | open=" .. tostring(opened) .. " | owner=" .. tostring(owner) ..
        " | items=" .. tostring(data.Items and #data.Items or 0))
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
                for dx = -SCAN_RADIUS, SCAN_RADIUS do
                    for dy = -SCAN_RADIUS, SCAN_RADIUS do
                        for dz = -SCAN_RADIUS, SCAN_RADIUS do
                            inspectCrate(dim, math.floor(px) + dx, math.floor(py) + dy, math.floor(pz) + dz)
                        end
                    end
                end
            end
        end)
        if not ok and err then
            print("[ERROR] [WoodCrate] Loop error: " .. tostring(err))
        end
    end
end)

-- Demo of the server command primitive: announce once the world is ready.
-- Delayed a few seconds because ExecuteCommand requires an active world
-- (during server startup the overworld does not exist yet).
Task.spawn(function()
    Task.wait(COMMAND_DELAY)
    local okCmd = World:ExecuteCommand("say [LuaTweaker] Wood Crate demo loaded from Lua")
    print("[WoodCrate] ExecuteCommand returned: " .. tostring(okCmd))
end)

print("[WoodCrate] BlockState/NBT/Events/Command demo active")
