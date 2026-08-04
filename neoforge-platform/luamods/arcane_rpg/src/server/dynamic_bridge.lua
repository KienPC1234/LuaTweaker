-- ================================================================
-- ARCANE RPG: Low-Level Dynamic Bridge Demo
-- Demonstrates Tier 2 API: Direct Java access via DynamicJavaProxy
-- ================================================================
local Events = require("LuaTweaker.Events")

-- ================================================================
-- PATTERN 1: Raw Event Object Access (Tầng 2 - Dynamic Bridge)
-- Khi Events:Listen nhận raw NeoForge events, event object tự động
-- được wrap bởi DynamicJavaProxy. Modder gọi TRỰC TIẾP methods Java.
-- ================================================================

Events:Listen("EntityHurt", function(event)
    -- event là DynamicJavaProxy của LivingDamageEvent
    -- Truy cập trực tiếp Java methods không cần wrapper
    local amount = event.Amount           -- __index -> getAmount()
    local source = event.Source           -- __index -> getSource()
    
    -- Dynamic Bridge: nếu source có getDirectEntity(), gọi trực tiếp
    if source ~= nil then
        local attacker = source.DirectEntity  -- auto-resolved
    end
end)

-- ================================================================
-- PATTERN 2: Raw NBT Access via getPersistentData()
-- Modder cần lưu data phức tạp mà Storage API không hỗ trợ?
-- Truy cập thẳng CompoundTag của Minecraft qua Dynamic Bridge.
-- ================================================================

Events:Listen("PlayerJoin", function(event)
    if event == nil then return end
    local player = event
    
    -- Access raw Java Entity qua __entity field
    -- entity:getPersistentData() trả về CompoundTag
    -- DynamicJavaProxy tự động wrap kết quả
    -- player.Raw:getPersistentData():putInt("ArcaneLevel", 1)
end)

-- ================================================================
-- PATTERN 3: Cross-Mod Interop (GregTech, Create, Mekanism...)
-- Khi mod khác expose API qua Java methods, LuaTweaker tự động
-- resolve qua DynamicJavaProxy mà KHÔNG cần viết wrapper.
-- ================================================================

local function tryInteractWithModdedMachine(entity)
    -- Ví dụ: Nếu có mod "Create" cài đặt, entity có thể expose
    -- getKineticSpeed() mà LuaTweaker không biết trước
    -- DynamicJavaProxy tự động tìm và gọi
    local speed = entity.KineticSpeed    -- -> getKineticSpeed()
    local energy = entity.EnergyStored   -- -> getEnergyStored()
    return speed, energy
end

-- ================================================================
-- PATTERN 4: Advanced - Runtime Method Discovery
-- Khi không biết chính xác tên method, dùng signature matching
-- qua RuntimeRemapper để tìm methods theo "hình dáng"
-- ================================================================

local function findMethodByName(entity, baseName)
    -- DynamicJavaProxy internally uses RuntimeRemapper with 3-tier fallback:
    -- 1. Exact match: "getHealth"
    -- 2. Heuristic: case-insensitive, contains pattern
    -- 3. Signature: param types + return type
    -- Modder chỉ cần gọi tên đúng, engine tự resolve
    
    local health = entity.Health        -- tier 1 + 2
    local maxHp = entity.MaxHealth      -- tier 1 + 2
    return health, maxHp
end

-- ================================================================
-- PATTERN 5: Event Cancellation via Dynamic Bridge
-- Cancel raw NeoForge events trực tiếp qua proxy
-- ================================================================

Events:Listen("BlockBreak", function(event)
    if event == nil then return end
    
    -- Access raw event properties
    local player = event.Player         -- -> getPlayer()
    local block = event.Pos             -- -> getPos()
    
    -- Check if player holds Crystal Staff via raw Java access
    -- itemStack:is() check item tag
    if player ~= nil then
        local heldItem = player.MainHandItem  -- -> getMainHandItem()
        if heldItem ~= nil then
            local itemId = heldItem.Item  -- -> getItem() -> toString()
            if itemId == "luatweaker:crystal_staff" then
                -- Bonus XP khi phá block bằng Crystal Staff
                player:giveExperience(5)
                player:sendActionBar("§bCrystal Staff grants +5 XP!")
            end
        end
    end
end)

-- ================================================================
-- PATTERN 6: Boss Phase Transition with Raw Java Calls
-- Phase 3 của Crystal Golem: summon minions qua raw Level access
-- ================================================================

Events:Listen("ServerTick", function()
    local Players = require("LuaTweaker.Players")
    local allPlayers = Players.GetPlayers()
    if #allPlayers == 0 then return end

    local Workspace = require("LuaTweaker.Workspace")
    for i = 1, #allPlayers do
        local player = allPlayers[i]
        local nearby = Workspace:GetEntitiesInRadius(player, 40.0)
        for j = 1, #nearby do
            local entity = nearby[j]
            if entity:getType() == "luatweaker:crystal_golem" and entity:isAlive() then
                local health = entity:getHealth()
                local maxHealth = entity:getMaxHealth()
                local phase = entity:getAttribute("arcane_rpg.boss.phase") or "1"

                -- Phase 3: Below 25% HP -> summon crystal shards
                if health < maxHealth * 0.25 and phase == "2" then
                    entity:setAttribute("arcane_rpg.boss.phase", "3")
                    entity:addEffect("regeneration", 200, 2)
                    entity:spawnParticle("minecraft:dragon_breath", 50, 2.0)
                    entity:playSound("minecraft:entity.ender_dragon.growl", 3.0, 0.3)

                    for k = 1, #allPlayers do
                        allPlayers[k]:sendTitle(
                            "§d§lPHASE 3!",
                            "§7The Crystal Golem unleashes its final form!",
                            10, 40, 20
                        )
                    end
                end

                -- Phase 3 behavior: periodic AoE damage pulse
                if phase == "3" and math.random() < 0.005 then
                    local bossX = entity:getX()
                    local bossY = entity:getY()
                    local bossZ = entity:getZ()
                    local targets = Workspace:GetEntitiesInRadius(entity, 8.0)
                    for t = 1, #targets do
                        local target = targets[t]
                        if target:getUuid() ~= entity:getUuid() and target:isLiving() then
                            target:damage(8.0)
                            target:addEffect("weakness", 60, 1)
                            target:spawnParticle("minecraft:dragon_breath", 10, 0.5)
                        end
                    end
                    entity:playSound("minecraft:entity.warden.sonic_boom", 2.0, 1.5)
                end
            end
        end
    end
end)

print("[ArcaneRPG] Dynamic Bridge patterns loaded: 6 patterns, cross-mod interop ready")
