-- ================================================================
-- ARCANE RPG: Commands
-- /arcane resetmana, /arcane give mana, /arcane spawn boss
-- ================================================================
local Commands = require("LuaTweaker.Commands")

local THIS_MOD = mod
local CONFIG = THIS_MOD and THIS_MOD:GetConfig() or {}

Commands:Register("arcane", {
    Description = "Arcane RPG admin commands",
    PermissionLevel = 2,
    ConsoleAllowed = false,
    Usage = "/arcane <resetmana|givemana|spawnboss> [args]",
    Aliases = { "arcanergp" },
    Handler = function(sender, args, raw)
        -- sender is a command-sender table; the real player (IPlayer Lua table)
        -- is exposed as sender.Player. Sender itself has no getX/getUuid etc.
        local player = sender.Player
        if player == nil or player == false then
            sender:sendMessage("§cThis command requires a player.")
            return
        end

        if #args < 1 then
            sender:sendMessage("§bUsage: /arcane <resetmana|givemana|spawnboss>")
            return
        end

        local subcommand = args[1]

        if subcommand == "resetmana" then
            local ManaSystem = require(".src.server.mana_system")
            local cfg = CONFIG
            ManaSystem:SetMana(player, cfg.mana.max_mana)
            sender:sendMessage("§bMana reset to " .. cfg.mana.max_mana)

        elseif subcommand == "givemana" then
            if #args < 2 then
                sender:sendMessage("§cUsage: /arcane givemana <amount>")
                return
            end
            local amount = tonumber(args[2])
            if amount == nil then
                sender:sendMessage("§cInvalid amount: " .. args[2])
                return
            end
            local ManaSystem = require(".src.server.mana_system")
            local actual = ManaSystem:RestoreMana(player, amount)
            sender:sendMessage("§bRestored " .. actual .. " mana")

        elseif subcommand == "spawnboss" then
            local px = player:getX()
            local py = player:getY()
            local pz = player:getZ()
            local EntityService = require("LuaTweaker.Entities")
            local boss = EntityService:spawnEntity("luatweaker:crystal_golem", px + 3, py, pz + 3)
            if boss ~= nil then
                sender:sendMessage("§b§lCrystal Golem spawned!")
            else
                sender:sendMessage("§cFailed to spawn Crystal Golem")
            end

        elseif subcommand == "status" then
            local ManaSystem = require(".src.server.mana_system")
            local current = ManaSystem:GetMana(player)
            local maxMana = ManaSystem:GetMaxMana(player)
            local percent = ManaSystem:GetManaPercent(player)
            sender:sendMessage(string.format(
                "§bMana: §f%.0f§b/§f%.0f §b(§f%.0f%%§b)",
                current, maxMana, percent * 100
            ))

        else
            sender:sendMessage("§cUnknown subcommand: " .. subcommand)
        end
    end
})

print("[ArcaneRPG] Commands registered: /arcane")
