-- ================================================================
-- ARCANE RPG: Commands
-- /arcane resetmana, /arcane give mana, /arcane spawn boss
-- ================================================================
local Commands = require("LuaTweaker.Commands")

Commands:Register("arcane", {
    Description = "Arcane RPG admin commands",
    PermissionLevel = 2,
    ConsoleAllowed = false,
    Usage = "/arcane <resetmana|givemana|spawnboss> [args]",
    Aliases = { "arcanergp" },
    Handler = function(sender, args, raw)
        if #args < 1 then
            sender:sendMessage("§bUsage: /arcane <resetmana|givemana|spawnboss>")
            return
        end

        local subcommand = args[1]

        if subcommand == "resetmana" then
            local ManaSystem = require("LuaTweaker.ManaSystem")
            local cfg = mod:GetConfig()
            ManaSystem:SetMana(sender, cfg.mana.max_mana)
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
            local ManaSystem = require("LuaTweaker.ManaSystem")
            local actual = ManaSystem:RestoreMana(sender, amount)
            sender:sendMessage("§bRestored " .. actual .. " mana")

        elseif subcommand == "spawnboss" then
            local px = sender:getX()
            local py = sender:getY()
            local pz = sender:getZ()
            local EntityService = require("LuaTweaker.EntityService")
            local boss = EntityService:spawnEntity("luatweaker:crystal_golem", px + 3, py, pz + 3)
            if boss ~= nil then
                sender:sendMessage("§b§lCrystal Golem spawned!")
            else
                sender:sendMessage("§cFailed to spawn Crystal Golem")
            end

        elseif subcommand == "status" then
            local ManaSystem = require("LuaTweaker.ManaSystem")
            local current = ManaSystem:GetMana(sender)
            local maxMana = ManaSystem:GetMaxMana(sender)
            local percent = ManaSystem:GetManaPercent(sender)
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
