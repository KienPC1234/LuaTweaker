-- ================================================================
-- LuaTweaker Test: Content Registration
-- Items, Blocks, Entity Types, Creative Tabs
-- ================================================================
local Content = require("LuaTweaker.Content")
local Players = require("LuaTweaker.Players")

local cfg = mod and mod:GetConfig() or {}
local boltCfg = cfg.skills and cfg.skills.crystal_bolt or {}

-- ==== CREATIVE TAB ====
Content.createTab("arcane_tab", function(tab)
    tab:title("LuaTweaker Test")
       :icon("luatweaker:crystal_shard")
end)

-- ==== ITEMS ====

local CrystalShard = Content.NewItem("crystal_shard")
    :MaxStackSize(64)
    :Rarity("UNCOMMON")
    :DisplayName("Arcane Crystal Shard")
    :CreativeTab("arcane_tab")
    :Tag("c:gems/crystal")
    :Glow(true)
    :Tooltip("§bPulses with arcane energy")
    :Register()

local CrystalScythe = Content.NewItem("crystal_scythe")
    :MaxStackSize(1)
    :Rarity("EPIC")
    :Durability(1500)
    :AttackDamage(12.0)
    :AttackSpeed(1.2)
    :DisplayName("Crystal Scythe")
    :CreativeTab("arcane_tab")
    :Tooltip("§bAnimated GeckoLib 3D Scythe")
    :Tooltip("§7Right-click to trigger whirlwind spin animation")
    :GeoModel("luatweaker:geo/item/crystal_scythe.geo.json", "luatweaker:textures/item/crystal_scythe.png", "luatweaker:animations/item/crystal_scythe.animation.json", "idle")
    :OnRightClick(function(player, stack)
        player:triggerAnimation("spin", "controller")
        player:sendActionBar("§bCasting Scythe Whirlwind Spin!")
    end)
    :Register()

local CrystalStaff = Content.NewItem("crystal_staff")
    :MaxStackSize(1)
    :Rarity("EPIC")
    :Durability(500)
    :DisplayName("Crystal Staff")
    :CreativeTab("arcane_tab")
    :Glow(true)
    :Tooltip("§bChannel the arcane through this crystalline focus")
    :Tooltip("§7Right-click to cast Crystal Bolt")
    :OnRightClick(function(player, itemStack)
        if player and player.PlayItemAnimation then
            player:PlayItemAnimation("recoil", 10, 1.2)
        end
        local Events = require("LuaTweaker.Events")
        Events:Fire("ArcaneCastSkill", { player = player, skill = "crystal_bolt" })
    end)
    :Register()

local CrystalSword = Content.NewItem("crystal_sword")
    :Rarity("RARE")
    :Durability(800)
    :AttackDamage(9.0)
    :AttackSpeed(1.6)
    :DisplayName("Crystal Blade")
    :CreativeTab("arcane_tab")
    :Tooltip("§bA blade forged from pure crystal")
    :OnHitEntity(function(target, attacker)
        local swordCfg = mod and mod:GetConfig() or {}
        local slowChance = tonumber(swordCfg.crystal_sword_slow_chance) or 0.15
        local slowTicks = tonumber(swordCfg.crystal_sword_slow_ticks) or 40
        if math.random() < slowChance then
            target:addEffect("slowness", slowTicks, 1)
            attacker:sendActionBar("§bCrystal frost enchants your blade!")
        end
    end)
    :Register()

local CrystalWarhammer = Content.NewItem("crystal_warhammer")
    :Rarity("EPIC")
    :Durability(1200)
    :AttackDamage(11.0)
    :AttackSpeed(1.0)
    :MiningSpeed(8.5)
    :MiningLevel(4)
    :DisplayName("Crystal Warhammer")
    :CreativeTab("arcane_tab")
    :Tooltip("§bHold right-click to charge a seismic ground slam")
    :UseAnimation("bow")
    :UseDuration(72000)
    :OnUseTick(function(player, stack, count)
        local charged = 72000 - count
        if charged % 10 == 0 and charged > 0 then
            local World = require("LuaTweaker.World")
            World:SpawnParticle("minecraft:crit", player.Position.X, player.Position.Y + 0.5, player.Position.Z, 0.2, 0.2, 0.2, 5, 0.1)
        end
    end)
    :OnReleaseUsing(function(player, stack, timeLeft)
        local charged = 72000 - timeLeft
        if charged >= 20 then
            local World = require("LuaTweaker.World")
            local pos = player.Position
            World:SpawnParticle("minecraft:explosion", pos.X, pos.Y, pos.Z, 0.5, 0.1, 0.5, 3, 0.2)
            player:sendActionBar("§6Boom! Seismic ground slam triggered!")
        end
        return true
    end)
    :OnHurtEnemy(function(target, attacker)
        attacker:sendActionBar("§cCrushed enemy armor with Warhammer!")
    end)
    :OnMineBlock(function(player, state)
        player:sendActionBar("§7Shattered block with Warhammer!")
    end)
    :OnSwing(function(player, stack)
        -- custom swing handler
    end)
    :Register()

Content.createArmorMaterial("crystal", function(mat)
    mat:layer("luatweaker:crystal")
       :equipSound("minecraft:item.armor.equip_diamond")
       :toughness(1.5)
       :knockbackResistance(0.05)
       :enchantability(20)
       :defense("helmet", 3)
       :defense("chestplate", 8)
       :defense("leggings", 6)
       :defense("boots", 3)
end)

local CrystalHelmet = Content.NewItem("crystal_helmet")
    :Type("helmet")
    :Rarity("RARE")
    :Durability(600)
    :ArmorMaterial("crystal")
    :ArmorTexture("luatweaker:crystal")
    :DisplayName("Crystal Helm")
    :CreativeTab("arcane_tab")
    :Register()

local CrystalChestplate = Content.NewItem("crystal_chestplate")
    :Type("chestplate")
    :Rarity("RARE")
    :Durability(700)
    :ArmorMaterial("crystal")
    :ArmorTexture("luatweaker:crystal")
    :DisplayName("Crystal Cuirass")
    :CreativeTab("arcane_tab")
    :Register()

local CrystalLeggings = Content.NewItem("crystal_leggings")
    :Type("leggings")
    :Rarity("RARE")
    :Durability(650)
    :ArmorMaterial("crystal")
    :ArmorTexture("luatweaker:crystal")
    :DisplayName("Crystal Legguards")
    :CreativeTab("arcane_tab")
    :Register()

local CrystalBoots = Content.NewItem("crystal_boots")
    :Type("boots")
    :Rarity("RARE")
    :Durability(550)
    :ArmorMaterial("crystal")
    :ArmorTexture("luatweaker:crystal")
    :DisplayName("Crystal Boots")
    :CreativeTab("arcane_tab")
    :Register()

local ManaPotion = Content.NewItem("mana_potion")
    :MaxStackSize(16)
    :Rarity("UNCOMMON")
    :DisplayName("Mana Potion")
    :CreativeTab("arcane_tab")
    :Food(0, 0)
    :AlwaysEdible()
    :Tooltip("§9Restores 50 mana instantly")
    :OnConsume(function(player, stack)
        local ManaSystem = require(".src.server.mana_system")
        local potionCfg = mod and mod:GetConfig() or {}
        local restore = tonumber(potionCfg.mana and potionCfg.mana.potion_restore) or 50
        ManaSystem:RestoreMana(player, restore)
        player:sendActionBar("§9+" .. tostring(restore) .. " Mana restored!")
        player:playSound("minecraft:item.honey_bottle.drink", 1.0, 1.5)
    end)
    :Register()

local SpellTome = Content.NewItem("spell_tome")
    :MaxStackSize(1)
    :Rarity("EPIC")
    :DisplayName("Tome of Meteor Strike")
    :CreativeTab("arcane_tab")
    :Glow(true)
    :Tooltip("§dTeaches the ultimate arcane spell")
    :Tooltip("§7Right-click to learn Meteor Strike")
    :OnRightClick(function(player, itemStack)
        local alreadyLearned = player:getAttribute("spell_tome_used") == "true"
        if alreadyLearned then
            player:sendActionBar("§7You already know Meteor Strike.")
            return true
        end
        player:setAttribute("spell_tome_used", "true")
        player:sendActionBar("§dYou have learned §lMeteor Strike§r§d!")
        player:playSound("minecraft:entity.player.levelup", 1.0, 0.5)
        return true
    end)
    :Register()

local CrystalWrench = Content.NewItem("crystal_wrench")
    :MaxStackSize(1)
    :DisplayName("Crystal Wrench")
    :CreativeTab("arcane_tab")
    :Tooltip("§7Right-click a block face to configure side mode:")
    :Tooltip("§6[IN] §7-> §a[OUT] §7-> §c[DISABLED]")
    :OnUseOnBlock(function(player, hit)
        if not hit or not hit.Face then return false end
        local World = require("LuaTweaker.World")
        local data = World:GetBlockEntityData(hit.X, hit.Y, hit.Z) or {}
        local sideConfig = data.SideConfig or {}
        local cur = sideConfig[hit.Face] or "NONE"
        local nextMode = "IN"
        if cur == "IN" then
            nextMode = "OUT"
        elseif cur == "OUT" then
            nextMode = "DISABLED"
        elseif cur == "DISABLED" then
            nextMode = "IN"
        else
            nextMode = "IN"
        end
        sideConfig[hit.Face] = nextMode
        data.SideConfig = sideConfig
        World:SetBlockEntityData(hit.X, hit.Y, hit.Z, data)
        World:PlaySound("minecraft:block.lever.click", hit.X, hit.Y, hit.Z, 1.0, 1.6)
        if player and player.sendActionBar then
            player:sendActionBar("§6[Wrench] §e" .. string.upper(hit.Face) .. " §7face set to: §f[" .. nextMode .. "]")
        end
        return true
    end)
    :Register()

local CrystalDetector = Content.NewItem("crystal_detector")
    :MaxStackSize(1)
    :DisplayName("Crystal Ore & Energy Detector")
    :CreativeTab("arcane_tab")
    :Tooltip("§7Right-click to scan nearby area (24 blocks)")
    :Tooltip("§bHighlights ores and energy machines in 3D")
    :OnRightClick(function(player, itemStack)
        local World = require("LuaTweaker.World")
        local pos = player.Position
        local ores = World:FindBlocks(pos.X, pos.Y, pos.Z, 24, "#c:ores", 32)
        local machines = World:FindBlocks(pos.X, pos.Y, pos.Z, 24, "crystal_", 32)
        local results = {}
        for _, b in ipairs(ores) do table.insert(results, b) end
        for _, b in ipairs(machines) do table.insert(results, b) end

        World:PlaySound("minecraft:block.conduit.activate", pos.X, pos.Y, pos.Z, 1.0, 1.5)
        for _, b in ipairs(results) do
            local bx = b.x or b.X
            local by = b.y or b.Y
            local bz = b.z or b.Z
            if bx and by and bz then
                World:SpawnParticle("minecraft:end_rod", bx + 0.5, by + 1.2, bz + 0.5, 0.05, 0.1, 0.05, 3, 0.02)
            end
        end
        local Network = require("LuaTweaker.Network")
        local highlightEvent = Network:GetOrCreateRemoteEvent("HighlightOres")
        if highlightEvent and player then
            highlightEvent:FireClient(player, results)
        end
        if player and player.sendActionBar then
            player:sendActionBar("§a[Detector] Located §e" .. #results .. " §adeposits/machines!")
        end
        return true
    end)
    :Register()

local ArcaneDevice = Content.NewItem("arcane_device")
    :MaxStackSize(1)
    :DisplayName("Arcane Tuning Device")
    :Texture("minecraft:item/amethyst_shard")
    :CreativeTab("arcane_tab")
    :Glow(true)
    :Tooltip("§dArcane ImGui Control Interface")
    :Tooltip("§7Right-click to open/close the Arcane Control Panel")
    :OnRightClick(function(player, itemStack)
        local Network = require("LuaTweaker.Network")
        local toggleEvent = Network:GetOrCreateRemoteEvent("ToggleArcanePanel")
        if toggleEvent and player then
            toggleEvent:FireClient(player)
        end
        return true
    end)
    :Register()

local CrystalBlaster = Content.NewItem("crystal_blaster")
    :MaxStackSize(1)
    :Rarity("RARE")
    :DisplayName("Crystal Energy Blaster")
    :CreativeTab("arcane_tab")
    :Model("luatweaker:item/crystal_blaster")
    :Tooltip("§bHigh-tech 3D Energy Blaster")
    :Tooltip("§7Consumes §e200 RF §7per shot (20,000 RF max)")
    :Tooltip("§aRechargeable: §7place into Crystal Battery slot")
    :OnRightClick(function(player, itemStack)
        local World = require("LuaTweaker.World")
        local curEnergy = itemStack:GetCustomData("Energy")
        if curEnergy == nil then curEnergy = 20000 end
        local maxEnergy = itemStack:GetCustomData("MaxEnergy") or 20000
        if curEnergy >= 200 then
            itemStack:SetCustomData("Energy", curEnergy - 200)
            itemStack:SetCustomData("MaxEnergy", maxEnergy)
            local pos = player.Position
            player:shootProjectile("crystal_bolt", 3.8, 0.02)
            World:PlaySound("minecraft:entity.illusioner.cast_spell", pos.X, pos.Y, pos.Z, 0.8, 1.8)
            World:PlaySound("minecraft:item.crossbow.shoot", pos.X, pos.Y, pos.Z, 0.6, 1.6)
            World:SpawnParticle("minecraft:electric_spark", pos.X, pos.Y + 1.2, pos.Z, 0.15, 0.15, 0.15, 8, 0.12)
            if player and player.PlayItemAnimation then
                player:PlayItemAnimation("recoil", 6, 1.8)
            end
            if player and player.swingArm then
                player:swingArm()
            end
            if player and player.sendActionBar then
                player:sendActionBar("§b[Blaster] Energy: §e" .. math.floor(curEnergy - 200) .. " / " .. maxEnergy .. " RF")
            end
        else
            local pos = player.Position
            World:PlaySound("minecraft:block.dispenser.fail", pos.X, pos.Y, pos.Z, 1.0, 1.5)
            if player and player.sendActionBar then
                player:sendActionBar("§c[Blaster] Depleted! Charge in a Crystal Battery slot.")
            end
        end
        return true
    end)
    :Register()

-- ==== FLUIDS ====
local LiquidCrystal = Content.NewFluid("liquid_crystal")
    :DisplayName("Liquid Crystal")
    :Color(0x33CCFF)
    :Density(1200)
    :Viscosity(1500)
    :LightLevel(8)
    :CreativeTab("arcane_tab")
    :Register()

-- ==== BLOCKS ====

local CrystalOre = Content.NewBlock("crystal_ore")
    :Hardness(4.0)
    :Resistance(12.0)
    :LightLevel(5)
    :SoundType("STONE")
    :RequiresTool(true)
    :MineableWith("pickaxe")
    :MiningLevel("iron")
    :Drop("luatweaker:crystal_shard", 2, 5)
    :DropExperience(5, 12)
    :CreativeTab("arcane_tab")
    :Tag("c:ores/crystal")
    :Register()

local Worldgen = require("LuaTweaker.Worldgen")
Worldgen:AddOre("luatweaker:crystal_ore", "minecraft:overworld", -64, 48, 8, 12)

local CrystalBlock = Content.NewBlock("crystal_block")
    :Hardness(5.0)
    :Resistance(20.0)
    :LightLevel(10)
    :SoundType("METAL")
    :RequiresTool(true)
    :MineableWith("pickaxe")
    :MiningLevel("diamond")
    :MapColor("DIAMOND")
    :PushReaction("BLOCK")
    :RedstoneConductor(false)
    :CreativeTab("arcane_tab")
    :Register()

local CrystalAltar = Content.NewBlock("crystal_altar")
    :Hardness(3.0)
    :Resistance(10.0)
    :LightLevel(8)
    :SoundType("METAL")
    :RequiresTool(true)
    :MineableWith("pickaxe")
    :MiningLevel("iron")
    :MapColor("PURPLE")
    :DisplayName("Crystal Altar")
    :CreativeTab("arcane_tab")
    :Container(3, 9, "spill")
    :ContainerTitle("Crystal Altar")
    :OnRightClick(function(player, blockState)
        player:sendActionBar("§bThe altar hums with arcane power...")
        return true
    end)
    :Register()

local CrystalInfuser = Content.NewBlock("crystal_infuser")
    :Hardness(4.0)
    :Resistance(15.0)
    :SoundType("METAL")
    :RequiresTool(true)
    :MineableWith("pickaxe")
    :MiningLevel("iron")
    :DisplayName("Crystal Infuser")
    :CreativeTab("arcane_tab")
    :Container(3, 9, "spill")
    :ContainerTitle("Crystal Infuser")
    :ManualSlots()
    :SlotPosition(0, 48, 28)
    :SlotPosition(1, 116, 28)
    :GuiBar("energy_bar", 8, 20, 14, 48, "energy", 0xAA0000)
    :CustomBar("flux_gauge", 26, 20, 14, 48, "CurrentFlux", "MaxFlux", 0x33CCFF, "Flux: %d / %d Arcanum")
    :GuiBar("infusion_progress", 68, 31, 42, 10, "progress", 0x44FF88)
    :Button("infuse_btn", 112, 50, 56, 18, "Infuse")
    :Label(56, 54, "Infuser", 0x225566)
    :OnButtonClick(function(blockPos, buttonId)
        if buttonId == "infuse_btn" then
            print("[CrystalInfuser] Infusion button clicked at " .. blockPos.X .. ", " .. blockPos.Y .. ", " .. blockPos.Z)
        end
    end)
    :OnNeighborChanged(function(blockPos, neighborPos)
        -- Redstone signal changed or neighbor updated
    end)
    :Register()

local CrystalReanimator = Content.NewBlock("crystal_reanimator")
    :Hardness(3.5)
    :Resistance(10.0)
    :LightLevel(10)
    :DisplayName("Crystal Reanimator")
    :CreativeTab("arcane_tab")
    :GeoModel("luatweaker:geo/block/crystal_reanimator.geo.json", "luatweaker:textures/block/crystal_reanimator.png", "luatweaker:animations/block/crystal_reanimator.animation.json", "idle")
    :OnRightClick(function(player, block)
        local World = require("LuaTweaker.World")
        World:PlayAnimation(block, "activate")
        player:sendActionBar("§bReanimator core energized!")
    end)
    :Register()

-- ==== PIPES & MACHINES ====

local CrystalItemPipe = Content.NewBlock("crystal_item_pipe")
    :Hardness(2.0)
    :Resistance(5.0)
    :SoundType("METAL")
    :DisplayName("Crystal Item Pipe")
    :CreativeTab("arcane_tab")
    :Texture("minecraft:block/polished_andesite")
    :ConnectionState(true)
    :Container(1, 1, "packed")
    :OnTick(function(data)
        local World = require("LuaTweaker.World")
        local pos = { X = data.X, Y = data.Y, Z = data.Z }
        local blockData = World:GetBlockEntityData(pos) or {}
        local sideConfig = blockData.SideConfig or {}
        local DIRS = {
            up = { 0, 1, 0 }, down = { 0, -1, 0 },
            north = { 0, 0, -1 }, south = { 0, 0, 1 },
            west = { -1, 0, 0 }, east = { 1, 0, 0 }
        }
        for side, off in pairs(DIRS) do
            if sideConfig[side] == "IN" then
                local neighbor = { X = pos.X + off[1], Y = pos.Y + off[2], Z = pos.Z + off[3] }
                World:TransferItem(neighbor, -1, pos, 0, 1)
            end
        end
        for side, off in pairs(DIRS) do
            if sideConfig[side] == "OUT" then
                local neighbor = { X = pos.X + off[1], Y = pos.Y + off[2], Z = pos.Z + off[3] }
                World:TransferItem(pos, 0, neighbor, -1, 1)
            end
        end
    end)
    :Register()

local CrystalEnergyPipe = Content.NewBlock("crystal_energy_pipe")
    :Hardness(2.0)
    :Resistance(5.0)
    :SoundType("METAL")
    :DisplayName("Crystal Energy Pipe")
    :CreativeTab("arcane_tab")
    :Texture("minecraft:block/redstone_block")
    :ConnectionState(true)
    :EnergyStorage(10000, 1000, 1000)
    :OnTick(function(data)
        local World = require("LuaTweaker.World")
        local pos = { X = data.X, Y = data.Y, Z = data.Z }
        local blockData = World:GetBlockEntityData(pos) or {}
        local sideConfig = blockData.SideConfig or {}
        local DIRS = {
            up = { 0, 1, 0 }, down = { 0, -1, 0 },
            north = { 0, 0, -1 }, south = { 0, 0, 1 },
            west = { -1, 0, 0 }, east = { 1, 0, 0 }
        }
        for side, off in pairs(DIRS) do
            local mode = sideConfig[side] or "NONE"
            local neighbor = { X = pos.X + off[1], Y = pos.Y + off[2], Z = pos.Z + off[3] }
            if mode == "IN" then
                World:TransferEnergy(neighbor, pos, 200)
            elseif mode == "OUT" then
                World:TransferEnergy(pos, neighbor, 200)
            elseif mode == "NONE" and data.Energy > 0 then
                World:TransferEnergy(pos, neighbor, 50)
            end
        end
    end)
    :Register()

local CrystalFluidPipe = Content.NewBlock("crystal_fluid_pipe")
    :Hardness(2.0)
    :Resistance(5.0)
    :SoundType("METAL")
    :DisplayName("Crystal Fluid Pipe")
    :CreativeTab("arcane_tab")
    :Texture("minecraft:block/lapis_block")
    :ConnectionState(true)
    :FluidStorage(4000)
    :OnTick(function(data)
        local World = require("LuaTweaker.World")
        local pos = { X = data.X, Y = data.Y, Z = data.Z }
        local blockData = World:GetBlockEntityData(pos) or {}
        local sideConfig = blockData.SideConfig or {}
        local DIRS = {
            up = { 0, 1, 0 }, down = { 0, -1, 0 },
            north = { 0, 0, -1 }, south = { 0, 0, 1 },
            west = { -1, 0, 0 }, east = { 1, 0, 0 }
        }
        for side, off in pairs(DIRS) do
            local mode = sideConfig[side] or "NONE"
            local neighbor = { X = pos.X + off[1], Y = pos.Y + off[2], Z = pos.Z + off[3] }
            if mode == "IN" then
                World:TransferFluid(neighbor, pos, 100)
            elseif mode == "OUT" then
                World:TransferFluid(pos, neighbor, 100)
            elseif mode == "NONE" and data.FluidAmount > 0 then
                World:TransferFluid(pos, neighbor, 25)
            end
        end
    end)
    :Register()

local CrystalSmeltery = Content.NewBlock("crystal_smeltery")
    :Hardness(4.0)
    :Resistance(15.0)
    :SoundType("METAL")
    :MineableWith("pickaxe")
    :MiningLevel("iron")
    :DisplayName("Crystal Smeltery")
    :CreativeTab("arcane_tab")
    :Texture("minecraft:block/blast_furnace_side")
    :Container(3, 9, "spill")
    :ContainerTitle("Crystal Smeltery")
    :ManualSlots()
    :SlotPosition(0, 44, 32)
    :SlotPosition(1, 116, 32)
    :EnergyStorage(20000, 500, 0)
    :FluidStorage(8000)
    :GuiBar("smelt_progress", 68, 35, 42, 10, "progress", 0xFFAA00)
    :GuiBar("energy_bar", 8, 20, 14, 48, "energy", 0xAA0000)
    :GuiBar("fluid_tank", 154, 20, 14, 48, "fluid", 0x33CCFF)
    :Label(36, 18, "Ore Input", 0x404040)
    :Label(104, 18, "Byproduct", 0x404040)
    :OnTick(function(data)
        local World = require("LuaTweaker.World")
        local pos = { X = data.X, Y = data.Y, Z = data.Z }
        local inItem = World:GetItem(pos, 0)
        if inItem and inItem.Count > 0 and data.Energy >= 20 and data.FluidAmount + 250 <= data.FluidCapacity then
            World:ExtractEnergy(pos.X, pos.Y, pos.Z, 20)
            local nextProg = data.Progress + 0.05
            if nextProg >= 1.0 then
                nextProg = 0.0
                World:EjectContainerItem(pos.X, pos.Y, pos.Z, 0, 1)
                World:FillFluid(pos.X, pos.Y, pos.Z, "luatweaker:liquid_crystal", 250)
                World:SpawnParticle("minecraft:flame", pos.X + 0.5, pos.Y + 0.8, pos.Z + 0.5, 0.05, 0.05, 0.05, 3, 0.02)
            end
            World:SetProgress(pos, nextProg)
        elseif data.Progress > 0 then
            World:SetProgress(pos, 0.0)
        end
    end)
    :Register()

local CrystalFluidGenerator = Content.NewBlock("crystal_fluid_generator")
    :Hardness(4.0)
    :Resistance(15.0)
    :SoundType("METAL")
    :DisplayName("Crystal Fluid Generator")
    :CreativeTab("arcane_tab")
    :Texture("minecraft:block/copper_block")
    :Container(3, 9, "packed")
    :ContainerTitle("Fluid Generator Core")
    :ManualSlots()
    :FluidStorage(16000)
    :EnergyStorage(100000, 0, 2000)
    :GuiBar("energy_bar", 8, 20, 16, 50, "energy", 0xAA0000)
    :GuiBar("fluid_bar", 28, 20, 16, 50, "fluid", 0x33CCFF)
    :Label(50, 25, "Generator Multiblock", 0xFFFFFF)
    :Label(50, 40, "Burns Liquid Crystal", 0x33CCFF)
    :OnTick(function(data)
        local World = require("LuaTweaker.World")
        local pos = { X = data.X, Y = data.Y, Z = data.Z }
        local isBaseFormed = World:CheckPattern(pos.X, pos.Y, pos.Z, {
            { x = -1, y = -1, z = -1, block = "luatweaker:crystal_block" },
            { x =  0, y = -1, z = -1, block = "luatweaker:crystal_block" },
            { x =  1, y = -1, z = -1, block = "luatweaker:crystal_block" },
            { x = -1, y = -1, z =  0, block = "luatweaker:crystal_block" },
            { x =  0, y = -1, z =  0, block = "luatweaker:crystal_block" },
            { x =  1, y = -1, z =  0, block = "luatweaker:crystal_block" },
            { x = -1, y = -1, z =  1, block = "luatweaker:crystal_block" },
            { x =  0, y = -1, z =  1, block = "luatweaker:crystal_block" },
            { x =  1, y = -1, z =  1, block = "luatweaker:crystal_block" },
        })
        if isBaseFormed and data.FluidAmount >= 10 and data.Energy + 200 <= data.EnergyCapacity then
            World:DrainFluid(pos.X, pos.Y, pos.Z, "luatweaker:liquid_crystal", 10)
            World:ProduceEnergy(pos, 200)
            World:SpawnParticle("minecraft:portal", pos.X + 0.5, pos.Y + 1.1, pos.Z + 0.5, 0.1, 0.2, 0.1, 2, 0.05)
        end
    end)
    :Register()

local CrystalBattery = Content.NewBlock("crystal_battery")
    :Hardness(4.0)
    :Resistance(20.0)
    :SoundType("METAL")
    :DisplayName("Crystal Energy Battery")
    :CreativeTab("arcane_tab")
    :Texture("minecraft:block/crying_obsidian")
    :Container(3, 9, "packed")
    :ContainerTitle("Energy Battery")
    :ManualSlots()
    :SlotPosition(0, 80, 30)
    :EnergyStorage(500000, 5000, 5000)
    :GuiBar("energy_bar", 8, 20, 16, 50, "energy", 0x00FF88)
    :Label(60, 16, "Charging Slot", 0x00FF88)
    :OnTick(function(data)
        local World = require("LuaTweaker.World")
        local pos = { X = data.X, Y = data.Y, Z = data.Z }
        if data.Energy > 0 then
            local item = World:GetItem(pos, 0)
            if item then
                local cur = item:GetCustomData("Energy")
                local max = item:GetCustomData("MaxEnergy") or 20000
                if cur and cur < max then
                    local charge = math.min(100, math.min(max - cur, data.Energy))
                    item:SetCustomData("Energy", cur + charge)
                    item:SetCustomData("MaxEnergy", max)
                    World:ExtractEnergy(pos.X, pos.Y, pos.Z, charge)
                    World:SpawnParticle("minecraft:electric_spark", pos.X + 0.5, pos.Y + 0.8, pos.Z + 0.5, 0.05, 0.05, 0.05, 1, 0.02)
                end
            end
        end
    end)
    :Register()

-- ==== ENTITY: CRYSTAL GOLEM BOSS ====
local golemCfg = cfg.boss or {}
Content.createEntity("crystal_golem", function(entity)
    entity:category("MONSTER")
          :parent("iron_golem")
          :dimensions(1.4, 2.8)
          :maxHealth(tonumber(golemCfg.crystal_golem_health) or 500)
          :movementSpeed(tonumber(golemCfg.crystal_golem_speed) or 0.3)
          :attackDamage(tonumber(golemCfg.crystal_golem_damage) or 12.0)
          :followRange(tonumber(golemCfg.crystal_golem_follow_range) or 40.0)
          :armor(tonumber(golemCfg.crystal_golem_armor) or 10.0)
          :knockbackResistance(tonumber(golemCfg.crystal_golem_knockback_resistance) or 0.8)
          :spawnEgg(0x4488FF, 0x88CCFF)
          :texture("luatweaker:textures/entity/crystal_golem.png")
          :ambientSound("minecraft:entity.iron_golem.step")
          :hurtSound("minecraft:entity.iron_golem.hurt")
          :deathSound("minecraft:entity.iron_golem.death")
          :drop("luatweaker:crystal_shard", 10, 20)
          :drop("luatweaker:crystal_staff", 1, 1)
          :experience(200)
          :bossBar("§b§lCrystal Golem", "BLUE", "PROGRESS")
          :creativeTab("arcane_tab")
end)

-- ==== ENTITY: CRYSTAL BEAST (native quadruped, forest dweller) ====
Content.createEntity("crystal_beast", function(entity)
    entity:category("MONSTER")
          :parent("cow")
          :dimensions(0.9, 1.4)
          :maxHealth(40)
          :movementSpeed(0.25)
          :attackDamage(5.0)
          :followRange(24.0)
          :spawnEgg(0x46C9A0, 0xBDFFD9)
          :texture("luatweaker:textures/entity/crystal_beast.png")
          :hurtSound("minecraft:entity.cow.hurt")
          :deathSound("minecraft:entity.cow.death")
          :drop("luatweaker:crystal_shard", 2, 5)
          :experience(12)
          :creativeTab("arcane_tab")
end)

-- ==== ENTITY: CRYSTAL WISP (generic quadruped rig: custom sounds + hooks) ====
-- The beast texture is cow-UV, which matches the quadruped rig's CowModel-based
-- renderer. defaultGoals resolves to "monster" from the MONSTER category.
Content.createEntity("crystal_wisp", function(entity)
    entity:category("MONSTER")
          :parent("cow")
          :rig("quadruped")
          :dimensions(0.9, 1.4)
          :maxHealth(40)
          :movementSpeed(0.3)
          :attackDamage(5.0)
          :followRange(24.0)
          :spawnEgg(0x46C9A0, 0xBDFFD9)
          :texture("luatweaker:textures/entity/crystal_beast.png")
          :hurtSound("minecraft:entity.cow.hurt")
          :deathSound("minecraft:entity.cow.death")
          :drop("luatweaker:crystal_shard", 2, 5)
          :experience(12)
          :tickRate(10)
          :onTick(function(ent)
              -- Wisps flare up when a player looks at them (sensing primitive demo).
              -- distanceTo() is pure math; canSee() is a line-of-sight raytrace,
              -- so the cheap check runs first and tickRate stays at 2x/s.
              local allPlayers = Players.GetPlayers()
              for i = 1, #allPlayers do
                  local player = allPlayers[i]
                  if ent:distanceTo(player) <= 12.0 and ent:canSee(player) then
                      ent:spawnParticle("minecraft:end_rod", 1, 0.1)
                      break
                  end
              end
          end)
          :onHurt(function(ent, amount, attacker)
              -- Wisps resist half of every hit; nil would keep the damage.
              return amount * 0.5
          end)
          :onDeath(function(ent, killer)
              print("[LuaTweakerTest] Crystal Wisp dissipated.")
              return false
          end)
          :onInteract(function(ent, player)
              player:sendActionBar("§bThe wisp hums softly...")
              return true
          end)
          :creativeTab("arcane_tab")
end)

-- ==== ENTITY: CRYSTAL CREEPER (native, wastes dweller) ====
Content.createEntity("crystal_creeper", function(entity)
    entity:category("MONSTER")
          :parent("creeper")
          :dimensions(0.6, 1.7)
          :maxHealth(25)
          :movementSpeed(0.3)
          :followRange(24.0)
          :spawnEgg(0x78DC5A, 0xDEFFC8)
          :texture("luatweaker:textures/entity/crystal_creeper.png")
          :hurtSound("minecraft:entity.creeper.hurt")
          :deathSound("minecraft:entity.creeper.death")
          :drop("luatweaker:crystal_shard", 1, 3)
          :experience(8)
          :creativeTab("arcane_tab")
end)

-- Register custom projectile for Crystal Bolt skill (damage from config)
Content.registerProjectile("crystal_bolt", {
    damage = tonumber(boltCfg.damage) or 8.0,
    explosionPower = tonumber(boltCfg.explosion_power) or 0,
    trailParticle = "minecraft:end_rod"
})

print("[LuaTweakerTest] Content registered: 14 items, 10 blocks, 1 fluid, 1 boss entity, 1 creative tab")
