-- ==== SECTION: WorldEdit-style commands (bulk block editing) ====
-- Selection-based editing via World:FillBlocks / World:ReplaceBlocks.
-- Every tunable (volume cap, world height range, dimension, suggestions)
-- comes from mod:GetConfig() (AGENTS.md 5.8).

local config = mod:GetConfig()

local WE_MAX_VOLUME = config.worldedit_max_volume or 4096
local WE_MIN_Y = config.worldedit_min_y or -64
local WE_MAX_Y = config.worldedit_max_y or 319
local WE_DIMENSION = config.worldedit_dimension or "minecraft:overworld"
local WE_SUGGEST_BLOCKS = config.worldedit_suggest_blocks
    or { "minecraft:stone", "minecraft:glass", "minecraft:gold_block", "minecraft:water" }

local Commands = require("LuaTweaker.Commands")
local World = require("LuaTweaker.World")

local WorldEditMod = {}

-- Per-sender selections: name -> { x1, y1, z1, x2, y2, z2 } (world coords).
local selections = {}

---@param value number
---@param minValue number
---@param maxValue number
---@return number
local function clamp(value, minValue, maxValue)
    if value < minValue then return minValue end
    if value > maxValue then return maxValue end
    return value
end

---@param sender CommandSender
---@return table|nil
local function getPlayerPos(sender)
    local player = sender.Player
    if player == nil or player.Position == nil then return nil end
    local position = player.Position
    return { x = math.floor(position.X), y = math.floor(position.Y), z = math.floor(position.Z) }
end

---@param sender CommandSender
---@return table|nil
local function requireSelection(sender)
    local selection = selections[sender.Name]
    if selection == nil or selection.x1 == nil or selection.x2 == nil then
        sender:SendError("No selection yet. Use /wpos1 and /wpos2 to mark the corners.")
        return nil
    end
    return selection
end

-- Normalizes the corners and clamps Y into the config-driven world height range.
---@param selection table
---@return table
local function normalizeSelection(selection)
    return {
        x1 = math.min(selection.x1, selection.x2),
        y1 = clamp(math.min(selection.y1, selection.y2), WE_MIN_Y, WE_MAX_Y),
        z1 = math.min(selection.z1, selection.z2),
        x2 = math.max(selection.x1, selection.x2),
        y2 = clamp(math.max(selection.y1, selection.y2), WE_MIN_Y, WE_MAX_Y),
        z2 = math.max(selection.z1, selection.z2),
    }
end

---@param selection table
---@return number
local function selectionVolume(selection)
    local normalized = normalizeSelection(selection)
    return (normalized.x2 - normalized.x1 + 1)
        * (normalized.y2 - normalized.y1 + 1)
        * (normalized.z2 - normalized.z1 + 1)
end

---@param sender CommandSender
---@param selection table
---@return boolean
local function checkVolume(sender, selection)
    local volume = selectionVolume(selection)
    if volume > WE_MAX_VOLUME then
        sender:SendError("Selection is " .. volume .. " blocks (max " .. WE_MAX_VOLUME
            .. "). Shrink it or raise worldedit_max_volume in the config.")
        return false
    end
    return true
end

---@param sender CommandSender
---@return table|nil
local function requireCheckedSelection(sender)
    local selection = requireSelection(sender)
    if selection == nil then return nil end
    if not checkVolume(sender, selection) then return nil end
    return normalizeSelection(selection)
end

-- ==== Handlers ====

---@param sender CommandSender
---@param args string[]
function WorldEditMod.OnPos1(sender, args)
    local position = getPlayerPos(sender)
    if position == nil then
        sender:SendError("Could not read your position.")
        return false
    end
    local selection = selections[sender.Name] or {}
    selection.x1 = position.x
    selection.y1 = position.y
    selection.z1 = position.z
    selections[sender.Name] = selection
    sender:SendSuccess("Position 1 = " .. position.x .. ", " .. position.y .. ", " .. position.z)
    return true
end

---@param sender CommandSender
---@param args string[]
function WorldEditMod.OnPos2(sender, args)
    local position = getPlayerPos(sender)
    if position == nil then
        sender:SendError("Could not read your position.")
        return false
    end
    local selection = selections[sender.Name] or {}
    selection.x2 = position.x
    selection.y2 = position.y
    selection.z2 = position.z
    selections[sender.Name] = selection
    sender:SendSuccess("Position 2 = " .. position.x .. ", " .. position.y .. ", " .. position.z)
    return true
end

---@param sender CommandSender
---@param args string[]
function WorldEditMod.OnSelection(sender, args)
    local selection = requireSelection(sender)
    if selection == nil then return false end
    sender:SendMessage("Selection: (" .. selection.x1 .. ", " .. selection.y1 .. ", " .. selection.z1
        .. ") -> (" .. selection.x2 .. ", " .. selection.y2 .. ", " .. selection.z2
        .. ") = " .. selectionVolume(selection) .. " blocks (max " .. WE_MAX_VOLUME .. ")")
    return true
end

---@param sender CommandSender
---@param args string[]
function WorldEditMod.OnSet(sender, args)
    if #args < 1 then
        sender:SendError("Usage: /wset <blockId>")
        return false
    end
    local selection = requireCheckedSelection(sender)
    if selection == nil then return false end
    local count = World:FillBlocks(WE_DIMENSION,
        selection.x1, selection.y1, selection.z1,
        selection.x2, selection.y2, selection.z2, args[1])
    if count < 0 then
        sender:SendError("Fill failed: unknown block '" .. args[1] .. "' or rejected region.")
        return false
    end
    sender:SendSuccess("Set " .. count .. " blocks to " .. args[1])
    return true
end

---@param sender CommandSender
---@param args string[]
function WorldEditMod.OnReplace(sender, args)
    if #args < 2 then
        sender:SendError("Usage: /wreplace <fromBlock> <toBlock>")
        return false
    end
    local selection = requireCheckedSelection(sender)
    if selection == nil then return false end
    local count = World:ReplaceBlocks(WE_DIMENSION,
        selection.x1, selection.y1, selection.z1,
        selection.x2, selection.y2, selection.z2, args[1], args[2])
    if count < 0 then
        sender:SendError("Replace failed: unknown block '" .. args[1] .. "' or '" .. args[2] .. "'.")
        return false
    end
    sender:SendSuccess("Replaced " .. count .. " blocks of " .. args[1] .. " with " .. args[2])
    return true
end

-- Fills only the four vertical faces of the selection (perimeter walls).
---@param sender CommandSender
---@param args string[]
function WorldEditMod.OnWalls(sender, args)
    if #args < 1 then
        sender:SendError("Usage: /wwalls <blockId>")
        return false
    end
    local selection = requireCheckedSelection(sender)
    if selection == nil then return false end
    local x1 = selection.x1
    local y1 = selection.y1
    local z1 = selection.z1
    local x2 = selection.x2
    local y2 = selection.y2
    local z2 = selection.z2

    local total = 0
    local faces = {
        { x1, y1, z1, x2, y2, z1 },
        { x1, y1, z2, x2, y2, z2 },
        { x1, y1, z1, x1, y2, z2 },
        { x2, y1, z1, x2, y2, z2 },
    }
    for _, face in ipairs(faces) do
        local count = World:FillBlocks(WE_DIMENSION,
            face[1], face[2], face[3], face[4], face[5], face[6], args[1])
        if count < 0 then
            sender:SendError("Walls failed: unknown block '" .. args[1] .. "'.")
            return false
        end
        total = total + count
    end
    sender:SendSuccess("Filled " .. total .. " wall blocks with " .. args[1])
    return true
end

-- Direct fill without a selection: /wfill <x1> <y1> <z1> <x2> <y2> <z2> <blockId>
---@param sender CommandSender
---@param args string[]
function WorldEditMod.OnFill(sender, args)
    if #args < 7 then
        sender:SendError("Usage: /wfill <x1> <y1> <z1> <x2> <y2> <z2> <blockId>")
        return false
    end
    local x1 = tonumber(args[1])
    local y1 = tonumber(args[2])
    local z1 = tonumber(args[3])
    local x2 = tonumber(args[4])
    local y2 = tonumber(args[5])
    local z2 = tonumber(args[6])
    if x1 == nil or y1 == nil or z1 == nil or x2 == nil or y2 == nil or z2 == nil then
        sender:SendError("All coordinates must be numbers.")
        return false
    end
    local selection = normalizeSelection({ x1 = x1, y1 = y1, z1 = z1, x2 = x2, y2 = y2, z2 = z2 })
    if not checkVolume(sender, selection) then return false end
    local count = World:FillBlocks(WE_DIMENSION,
        selection.x1, selection.y1, selection.z1,
        selection.x2, selection.y2, selection.z2, args[7])
    if count < 0 then
        sender:SendError("Fill failed: unknown block '" .. args[7] .. "' or rejected region.")
        return false
    end
    sender:SendSuccess("Set " .. count .. " blocks to " .. args[7])
    return true
end

---@param sender CommandSender
---@param args string[]
---@return string[]
function WorldEditMod.SuggestBlocks(sender, args)
    return WE_SUGGEST_BLOCKS
end

function WorldEditMod.RegisterCommands()
    local registered = 0
    local commands = {
        {
            name = "wpos1",
            description = "Set selection corner 1 to your position.",
            handler = WorldEditMod.OnPos1,
        },
        {
            name = "wpos2",
            description = "Set selection corner 2 to your position.",
            handler = WorldEditMod.OnPos2,
        },
        {
            name = "wsel",
            description = "Show the current selection and its block count.",
            handler = WorldEditMod.OnSelection,
        },
        {
            name = "wset",
            description = "Fill the selection with a block.",
            usage = "/wset <blockId>",
            handler = WorldEditMod.OnSet,
            suggestions = WorldEditMod.SuggestBlocks,
        },
        {
            name = "wreplace",
            description = "Replace one block type with another inside the selection.",
            usage = "/wreplace <fromBlock> <toBlock>",
            handler = WorldEditMod.OnReplace,
            suggestions = WorldEditMod.SuggestBlocks,
        },
        {
            name = "wwalls",
            description = "Fill only the perimeter walls of the selection.",
            usage = "/wwalls <blockId>",
            handler = WorldEditMod.OnWalls,
            suggestions = WorldEditMod.SuggestBlocks,
        },
        {
            name = "wfill",
            description = "Fill a box between two coordinates directly.",
            usage = "/wfill <x1> <y1> <z1> <x2> <y2> <z2> <blockId>",
            handler = WorldEditMod.OnFill,
        },
    }

    for _, command in ipairs(commands) do
        local definition = {
            Description = command.description,
            PermissionLevel = 0,
            ConsoleAllowed = false,
            Usage = command.usage or ("/" .. command.name),
            Handler = command.handler,
        }
        if command.suggestions ~= nil then
            definition.Suggestions = command.suggestions
        end
        if Commands:Register(command.name, definition) then
            registered = registered + 1
        end
    end
    print("[command_test_mod] Registered " .. registered .. " worldedit command(s).")
end

return WorldEditMod
