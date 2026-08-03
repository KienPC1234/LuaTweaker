-- ==== SECTION: Server-side command registration ====
-- Every tunable value comes from mod:GetConfig() (AGENTS.md 5.8).
local config = mod:GetConfig()

local HELLO_PREFIX = config.hello_prefix or "[CMD]"
local ANNOUNCE_PREFIX = config.announce_prefix or "[ANNOUNCE]"
local SUM_MAX_DIGITS = config.sum_max_digits or 6
local SHOP_ITEMS = config.shop_items or { "ruby_sword", "ruby_pickaxe", "magic_staff" }

local Commands = require("LuaTweaker.Commands")
local World = require("LuaTweaker.World")
local WorldEditMod = require(".src.server.worldedit")

local CommandTestMod = {}

-- ---@param sender CommandSender
-- ---@param args string[]
function CommandTestMod.OnHello(sender, args)
    if #args < 1 then
        sender:SendError("Usage: /hello <name>")
        return false
    end
    sender:SendSuccess(HELLO_PREFIX .. " Hello, " .. args[1] .. "!")
    return true
end

function CommandTestMod.OnSum(sender, args)
    if #args < 2 then
        sender:SendError("Usage: /sum <a> <b>")
        return false
    end
    local a = tonumber(args[1])
    local b = tonumber(args[2])
    if a == nil or b == nil then
        sender:SendError("Both arguments must be numbers.")
        return false
    end
    if #args[1] > SUM_MAX_DIGITS or #args[2] > SUM_MAX_DIGITS then
        sender:SendError("Numbers are limited to " .. SUM_MAX_DIGITS .. " digits.")
        return false
    end
    sender:SendMessage(tostring(a) .. " + " .. tostring(b) .. " = " .. tostring(a + b))
    return true
end

-- ---@param sender CommandSender
-- ---@param args string[]
-- ---@param raw string
function CommandTestMod.OnAnnounce(sender, args, raw)
    if #args < 1 then
        sender:SendError("Usage: /announce <message>")
        return false
    end
    -- The raw tail preserves the text exactly as the player typed it.
    local message = raw
    local ok = World:ExecuteCommand('tellraw @a {"text":"' .. ANNOUNCE_PREFIX .. " " .. message .. '"}')
    if not ok then
        sender:SendError("Broadcast failed (server command execution unavailable).")
        return false
    end
    return true
end

-- Nested path demo: /shop buy <item> (tab-completes the item names).
function CommandTestMod.OnShopBuy(sender, args)
    if #args < 1 then
        sender:SendError("Usage: /shop buy <item>")
        return false
    end
    local item = args[1]
    if sender:HasPermission(2) then
        sender:SendSuccess("(op) You bought: " .. item)
    else
        sender:SendMessage("You can buy: " .. item .. " - check /shop list")
    end
    return true
end

-- Suggestion handler: called on Tab press with (sender, argsSoFar).
-- ---@param sender CommandSender
-- ---@param args string[]
-- ---@return string[]
function CommandTestMod.SuggestShopItems(sender, args)
    return SHOP_ITEMS
end

function CommandTestMod.RegisterAll()
    local registered = 0

    -- Top-level command with an alias (/hi -> /hello) and static suggestions.
    if Commands:Register("hello", {
        Description = "Greet a player with a configurable prefix.",
        PermissionLevel = 0,
        ConsoleAllowed = true,
        Usage = "/hello <name>",
        Aliases = { "hi" },
        Suggestions = { "world", "minecraft", "steve" },
        Handler = CommandTestMod.OnHello,
    }) then
        registered = registered + 1
    end

    -- Top-level command with raw-text access (args.Raw) and dynamic suggestions.
    if Commands:Register("announce", {
        Description = "Broadcast the exact typed text to all players (op only).",
        PermissionLevel = 2,
        ConsoleAllowed = true,
        Usage = "/announce <message>",
        Suggestions = CommandTestMod.SuggestShopItems,
        Handler = CommandTestMod.OnAnnounce,
    }) then
        registered = registered + 1
    end

    -- Top-level numeric command with validation.
    if Commands:Register("sum", {
        Description = "Add two numbers (config-limited digits).",
        PermissionLevel = 0,
        ConsoleAllowed = true,
        Usage = "/sum <a> <b>",
        Handler = CommandTestMod.OnSum,
    }) then
        registered = registered + 1
    end

    -- Nested path: /shop buy <item>, with function-based tab completion.
    if Commands:Register("shop/buy", {
        Description = "Buy an item from the shop (nested path demo).",
        PermissionLevel = 0,
        ConsoleAllowed = true,
        Usage = "/shop buy <item>",
        Suggestions = CommandTestMod.SuggestShopItems,
        Handler = CommandTestMod.OnShopBuy,
    }) then
        registered = registered + 1
    end

    print("[command_test_mod] Registered " .. registered .. " top-level command(s).")

    -- WorldEdit-style selection editing commands (/wpos1, /wset, /wreplace, ...).
    WorldEditMod.RegisterCommands()
end

return CommandTestMod
