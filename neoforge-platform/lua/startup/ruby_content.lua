-- ===================================================================
-- LuaTweaker Custom Content: Full Ruby Gear, Tools, Armor & Custom Models
-- ===================================================================

-- 0. Explicit Custom Creative Tabs
startup:createTab("ruby_tab", function(tab)
    tab:title("Ruby Treasures")
       :icon("luatweaker:custom_ruby")
end)

startup:createTab("magic_tab", function(tab)
    tab:title("Arcane Treasures")
       :icon("luatweaker:magic_staff")
end)

-- 1. Ruby Gem Item & Enchanted Ruby Apple (Food & Consumable)
startup:createItem("custom_ruby", function(item)
    item:maxStackSize(64)
        :rarity("EPIC")
        :burnTime(400)
        :displayName("Enchanted Ruby Gem")
        :creativeTab("ruby_tab")
        :tag("c:gems/ruby")
        :tag("c:gems")
        :onRightClick(function(player, itemStack)
            player:sendActionBar("§6Enchanted Ruby Gem shines bright!")
            player:playSound("luatweaker:ruby_equip", 1.0, 1.2)
        end)
end)

startup:createItem("ruby_apple", function(item)
    item:displayName("Enchanted Ruby Apple")
        :rarity("EPIC")
        :glow(true)
        :tooltip("§dHồi phục sinh lực tức thì & Kháng cự gia tăng")
        :food(6, 1.2)
        :alwaysEdible()
        :creativeTab("ruby_tab")
        :onConsume(function(player, stack)
            player:sendActionBar("§dYou feel radiant Ruby magic surging through you!")
            player:addEffect("regeneration", 200, 1)
            player:addEffect("resistance", 6000, 0)
            player:playSound("minecraft:entity.player.burp", 1.0, 1.0)
        end)
end)

-- 2. Ruby Blocks (kèm Inline Block Drops & EXP Drops)
startup:createBlock("ruby_ore", function(block)
    block:hardness(4.5)
         :resistance(15.0)
         :lightLevel(3)
         :soundType("STONE")
         :mineableWith("PICKAXE")
         :miningLevel("IRON")
         :drop("luatweaker:custom_ruby", 1, 2)
         :dropExperience(3, 7)
         :creativeTab("ruby_tab")
         :tag("luatweaker:ores")
         :onRightClick(function(player, blockState)
             player:sendActionBar("§eYou tapped a raw Ruby Ore vein!")
             player:playSound("luatweaker:ruby_mine", 0.8, 1.0)
         end)
end)

-- 3. Block Variants (Stairs, Slab & Wall)
startup:createStairs("ruby_stairs", "luatweaker:ruby_block")
startup:createSlab("ruby_slab", "luatweaker:ruby_block")
startup:createWall("ruby_wall", "luatweaker:ruby_block")


startup:createBlock("ruby_block", function(block)
    block:hardness(5.0)
         :resistance(30.0)
         :lightLevel(8)
         :soundType("METAL")
         :mineableWith("PICKAXE")
         :miningLevel("IRON")
         :creativeTab("ruby_tab")
         :tag("minecraft:beacon_base_blocks")
         :tag("c:storage_blocks/ruby")
         :onRightClick(function(player, blockState)
             player:sendActionBar("§cRadiant Ruby Block interacted!")
         end)
end)

startup:createBlock("ruby_lamp", function(block)
    block:hardness(2.0)
         :emissive(true)
         :creativeTab("ruby_tab")
         :onRightClick(function(player, blockState)
             player:sendActionBar("§6Radiant Ruby Lamp glowing bright!")
         end)
end)

-- 4. Custom Ruby Fluid & Bucket with Player Touch Effect
startup:createFluid("ruby_fluid", function(fluid)
    fluid:stillTexture("luatweaker:block/ruby_fluid_still")
         :flowingTexture("luatweaker:block/ruby_fluid_flow")
         :temperature(1000)
         :viscosity(2000)
         :creativeTab("ruby_tab")
         :onTouch(function(player)
             player:sendActionBar("§cYou are bathing in warm Ruby Magic Liquid!")
             player:addEffect("regeneration", 100, 1)
             player:addEffect("fire_resistance", 200, 0)
         end)
end)

-- 5. Custom Entity: Ruby Guardian Zombie Mob (Explicit Parent: minecraft:zombie)
startup:createEntity("ruby_guardian", function(entity)
    entity:parent("minecraft:zombie")
          :category("MONSTER")
          :dimensions(0.6, 1.95)
          :maxHealth(50.0)
          :movementSpeed(0.28)
          :attackDamage(8.0)
          :armor(4.0)
          :texture("luatweaker:textures/entity/ruby_guardian.png")
          :spawnEgg(0xE61C40, 0x500010)
          :experience(12)
          :creativeTab("ruby_tab")
end)




-- 3. Explicit Ruby Weapons & Tools (with miningLevel & miningSpeed)
startup:createSword("ruby_sword", function(item)
    item:durability(1800)
        :miningLevel("DIAMOND")
        :attackDamage(9.0)
        :attackSpeed(-2.4)
        :enchantability(22)
        :rarity("EPIC")
        :displayName("Ruby Greatsword")
        :creativeTab("ruby_tab")
        :onRightClick(function(player, itemStack)
            player:sendActionBar("§cYou slash with the Ruby Greatsword!")
            player:playSound("minecraft:entity.player.attack.sweep", 1.0, 0.9)
        end)
end)

startup:createPickaxe("ruby_pickaxe", function(item)
    item:durability(1800)
        :miningLevel("DIAMOND")
        :miningSpeed(9.0)
        :attackDamage(5.0)
        :attackSpeed(-2.8)
        :enchantability(22)
        :rarity("EPIC")
        :displayName("Ruby Pickaxe")
        :creativeTab("ruby_tab")
end)

startup:createAxe("ruby_axe", function(item)
    item:durability(1800)
        :miningLevel("DIAMOND")
        :miningSpeed(9.0)
        :attackDamage(10.0)
        :attackSpeed(-3.0)
        :enchantability(22)
        :rarity("EPIC")
        :displayName("Ruby Battleaxe")
        :creativeTab("ruby_tab")
end)

startup:createShovel("ruby_shovel", function(item)
    item:durability(1800)
        :miningLevel("DIAMOND")
        :miningSpeed(9.0)
        :attackDamage(5.5)
        :attackSpeed(-3.0)
        :enchantability(22)
        :rarity("EPIC")
        :displayName("Ruby Excavator Shovel")
        :creativeTab("ruby_tab")
end)

startup:createHoe("ruby_hoe", function(item)
    item:durability(1800)
        :miningLevel("DIAMOND")
        :miningSpeed(9.0)
        :attackDamage(1.0)
        :attackSpeed(0.0)
        :enchantability(22)
        :rarity("EPIC")
        :displayName("Ruby Scythe Hoe")
        :creativeTab("ruby_tab")
end)

-- 0. Explicit Custom Armor Material
startup:createArmorMaterial("ruby", function(mat)
    mat:layer("luatweaker:ruby")
       :equipSound("minecraft:item.armor.equip_diamond")
       :defense("helmet", 4)
       :defense("chestplate", 9)
       :defense("leggings", 7)
       :defense("boots", 4)
       :toughness(3.0)
       :knockbackResistance(0.1)
       :enchantability(25)
end)

-- 4. Explicit Ruby Armor Set
startup:createHelmet("ruby_helmet", function(item)
    item:armorMaterial("ruby")
        :durability(450)
        :rarity("EPIC")
        :displayName("Ruby Helmet")
        :creativeTab("ruby_tab")
end)

startup:createChestplate("ruby_chestplate", function(item)
    item:armorMaterial("ruby")
        :durability(650)
        :rarity("EPIC")
        :displayName("Ruby Chestplate")
        :creativeTab("ruby_tab")
end)

startup:createLeggings("ruby_leggings", function(item)
    item:armorMaterial("ruby")
        :durability(600)
        :rarity("EPIC")
        :displayName("Ruby Leggings")
        :creativeTab("ruby_tab")
end)

startup:createBoots("ruby_boots", function(item)
    item:armorMaterial("ruby")
        :durability(500)
        :rarity("EPIC")
        :displayName("Ruby Boots")
        :creativeTab("ruby_tab")
end)

-- 5. Custom Ranged Weapon Item Model: Archmage Magic Staff
startup:createRangedItem("magic_staff", function(item)
    item:maxStackSize(1)
        :rarity("EPIC")
        :model("luatweaker:item/magic_staff")
        :displayName("Archmage Magic Staff")
        :creativeTab("magic_tab")
        :onRightClick(function(player, itemStack)
            if _G.HandleMagicStaffUse then
                _G.HandleMagicStaffUse(player, itemStack)
            else
                player:sendActionBar("§bMagic Staff unleashes Ruby Orb!")
                player:shootProjectile("luatweaker:ruby_orb", 1.8)
            end
        end)
        :onHitEntity(function(targetEntity, shooterPlayer)
            shooterPlayer:sendActionBar("§aMagic Staff struck " .. targetEntity:getName() .. "!")
            targetEntity:damage(10.0)
            targetEntity:addEffect("slowness", 100, 2)
            targetEntity:addEffect("glowing", 100, 0)
            targetEntity:setIgniteSeconds(5)
            targetEntity:playSound("minecraft:entity.lightning_bolt.thunder", 0.8, 1.4)
        end)
end)

-- 6. Custom Block Model: Wood Crate (placed inside Magic Staff tab as requested)
startup:createBlock("wood_crate", function(block)
    block:hardness(2.0)
         :resistance(5.0)
         :soundType("WOOD")
         :mineableWith("AXE")
         :model("luatweaker:block/wood_crate")
         :creativeTab("magic_tab")
         :onRightClick(function(player, blockState)
             player:sendActionBar("§6You opened the Reinforced Wood Crate!")
         end)
end)

print("[Ruby Content] Registered Ruby Gear, Magic Staff, Wood Crate with complete Mining Levels & Tool Tags!")

-- ===================================================================
-- Easy Tag Creation Examples:
-- ===================================================================

if tag then
    tag.item("luatweaker:ruby_items", {
        "luatweaker:custom_ruby",
        "luatweaker:ruby_sword",
        "luatweaker:ruby_pickaxe",
        "luatweaker:ruby_axe"
    })

    tag.block("minecraft:beacon_base_blocks", "luatweaker:ruby_block")
    print("[Custom Tags] Registered tags via clean tag.item and tag.block API!")
end

-- ===================================================================
-- 7. Custom Projectile Registration: Ruby Orb
-- ===================================================================
startup:registerProjectile("luatweaker:ruby_orb", {
    damage = 35,
    explosionPower = 2,
    trailParticle = "minecraft:flame",
    onHitEffect = "minecraft:wither",
    gravity = false,
    homing = true
})
print("[Ruby Content] Registered Custom Projectile: luatweaker:ruby_orb")

-- ===================================================================
-- 8. Custom Entity Type & Spawn Egg Registration: Ruby Boss
-- ===================================================================
startup:createEntity("ruby_boss", function(entity)
    entity:parent("zombie")
          :maxHealth(500)
          :attackDamage(25)
          :movementSpeed(0.35)
          :followRange(40)
          :spawnEgg(0xFF0000, 0x550000)
          :bossBar("§c[Ruby Overseer Boss]", "RED", "PROGRESS")
          :creativeTab("magic_tab")
          :bbmodel("luatweaker:models/ruby_boss.bbmodel")
end)
print("[Ruby Content] Registered Custom Entity Type & Auto Spawn Egg: luatweaker:ruby_boss")
