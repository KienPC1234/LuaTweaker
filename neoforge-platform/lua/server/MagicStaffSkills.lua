-- ===================================================================
-- Archmage Magic Staff: Server-Side Multi-Skill & Persistent Mana Engine
-- Clean ASCII Formatting & Storage Persistence
-- ===================================================================
local Entities = require("LuaTweaker.Entities")
local AIGoals  = require("LuaTweaker.AIGoals")
local Storage  = require("LuaTweaker.Storage")
local Network  = require("LuaTweaker.Network")
local Task     = require("LuaTweaker.Task")

print("[MagicStaffSkills] Initializing Magic Staff System with Storage Persistence...")

-- PLAYER STATE TRACKER (In-Memory Cache backed by Storage)
local playerStates = {}

local SKILLS = {
    { name = "Ruby Orb Fireball", cost = 20, cooldown = 1.5 },
    { name = "Summon Ruby Guardian", cost = 50, cooldown = 10.0 },
    { name = "Aegis Shield Barrier", cost = 35, cooldown = 8.0 }
}

local function getOrCreatePlayerState(player)
    local uuid = player:getUuid()
    if not playerStates[uuid] then
        local savedMana = Storage:get("StaffMana_" .. uuid, 100)
        local savedSkill = Storage:get("StaffSkillIndex_" .. uuid, 1)

        local mana = (savedMana and type(savedMana) == "number") and savedMana or 100
        local skillIdx = (savedSkill and type(savedSkill) == "number") and math.floor(savedSkill) or 1

        if skillIdx < 1 or skillIdx > #SKILLS then
            skillIdx = 1
        end

        playerStates[uuid] = {
            mana = math.clamp(mana, 0, 100),
            maxMana = 100,
            selectedSkillIndex = skillIdx,
            lastCastTimes = { 0, 0, 0 },
            player = player
        }
    else
        playerStates[uuid].player = player
    end
    return playerStates[uuid]
end

local function savePlayerState(player, state)
    local uuid = player:getUuid()
    Storage:set("StaffMana_" .. uuid, state.mana)
    Storage:set("StaffSkillIndex_" .. uuid, state.selectedSkillIndex)
end

local function buildManaBar(mana, maxMana)
    local totalSegments = 10
    local filled = math.floor((math.clamp(mana, 0, maxMana) / maxMana) * totalSegments)
    return string.rep("■", filled) .. string.rep("□", totalSegments - filled)
end

local function updateActionBar(player, state, customMessage)
    local skill = SKILLS[state.selectedSkillIndex]
    local now = os.clock()
    local lastCast = state.lastCastTimes[state.selectedSkillIndex] or 0
    local cdRemaining = math.max(0, skill.cooldown - (now - lastCast))
    
    local cdStatus = cdRemaining > 0 and string.format(" §c[CD: %.1fs]", cdRemaining) or " §a[READY]"
    local manaBarStr = buildManaBar(state.mana, state.maxMana)
    local barText = customMessage or string.format("§b[Staff] §e%s §7| §dMana: §b%s §7(%d/%d)%s", skill.name, manaBarStr, state.mana, state.maxMana, cdStatus)
    
    player:sendActionBar(barText)
end

-- DYNAMIC MANA REGENERATION & CONTINUOUS ACTION BAR HUD LOOP (+10 Mana per second)
Task:spawn(function()
    while true do
        Task:wait(0.4)
        for uuid, state in pairs(playerStates) do
            if state.mana < state.maxMana then
                state.mana = math.min(state.maxMana, state.mana + 4)
                Storage:set("StaffMana_" .. uuid, state.mana)
            end
            if state.player then
                local mainItem = state.player:getMainHandItem()
                if mainItem and (mainItem == "luatweaker:magic_staff" or mainItem:find("magic_staff")) then
                    updateActionBar(state.player, state)
                end
            end
        end
    end
end)

-- GLOBAL & LOCAL EXPOSED STAFF ACTION HANDLER
local function handleStaffUse(player, itemStack)
    print("[Server] [MagicStaffSkills] Executing HandleMagicStaffUse...")
    local state = getOrCreatePlayerState(player)
    local now = os.clock()

    if player:isSneaking() then
        -- SWAP SKILL MODE ON SHIFT + RIGHT CLICK
        state.selectedSkillIndex = (state.selectedSkillIndex % #SKILLS) + 1
        local newSkill = SKILLS[state.selectedSkillIndex]
        savePlayerState(player, state)
        player:playSound("minecraft:block.lever.click", 1.0, 1.4)
        updateActionBar(player, state, string.format("§6[Switched Skill] §e-> %s", newSkill.name))
        return
    end

    -- CAST CURRENTLY SELECTED SKILL
    local skillIdx = state.selectedSkillIndex
    local skill = SKILLS[skillIdx]
    local lastCast = state.lastCastTimes[skillIdx] or 0
    local timeSinceCast = now - lastCast

    if timeSinceCast < skill.cooldown then
        local remaining = skill.cooldown - timeSinceCast
        player:playSound("minecraft:entity.ender_eye.death", 0.8, 0.6)
        updateActionBar(player, state, string.format("§c[Cooldown] %s ready in %.1fs!", skill.name, remaining))
        return
    end

    if state.mana < skill.cost then
        player:playSound("minecraft:block.chest.locked", 1.0, 0.8)
        updateActionBar(player, state, string.format("§e[Low Mana] Need %d Mana (Current: %d)", skill.cost, state.mana))
        return
    end

    -- DEDUCT MANA & SET COOLDOWN
    state.mana = state.mana - skill.cost
    state.lastCastTimes[skillIdx] = now
    savePlayerState(player, state)

    if skillIdx == 1 then
        -- SKILL 1: RUBY ORB FIREBALL
        player:playSound("minecraft:entity.firework_rocket.blast", 1.0, 1.2)
        player:shootProjectile("luatweaker:ruby_orb", 1.8)
        updateActionBar(player, state, "§b[Cast] Ruby Orb Fireball launched!")

    elseif skillIdx == 2 then
        -- SKILL 2: SUMMON RUBY GUARDIAN MINION
        player:playSound("minecraft:entity.evoker.prepare_summon", 1.0, 1.0)
        player:spawnParticle("minecraft:totem_of_undying", 30, 0.5)
        local minion = player:spawnEntity("luatweaker:ruby_boss", 0, 1, 0)
        if minion then
            if AIGoals then
                AIGoals:clearGoals(minion)
                AIGoals:addNearestAttackableTargetGoal(minion, 1, "monster")
                AIGoals:addHurtByTargetGoal(minion, 2)
                AIGoals:addMeleeAttackGoal(minion, 3, 1.3, false)
            end
        end
        updateActionBar(player, state, "§a[Summon] Ruby Guardian summoned to defend you!")

    elseif skillIdx == 3 then
        -- SKILL 3: AEGIS SHIELD BARRIER
        player:playSound("minecraft:item.armor.equip_diamond", 1.0, 1.2)
        player:playSound("minecraft:entity.zombie_villager.cure", 1.0, 1.5)
        player:spawnParticle("minecraft:enchanted_hit", 40, 0.4)
        
        player:addEffect("resistance", 160, 2) -- 8s Resistance III
        player:addEffect("regeneration", 160, 1) -- 8s Regeneration II
        player:addEffect("absorption", 600, 1)   -- Absorption II
        updateActionBar(player, state, "§d[Aegis Barrier] Resistance III & Regeneration II Active!")
    end
end
_G.HandleMagicStaffUse = handleStaffUse

-- LISTEN TO MAGIC STAFF RIGHT-CLICK ACTION FROM STARTUP SCRIPT
local Events = require("LuaTweaker.Events")
if Events then
    Events:Listen("MagicStaffUsed", function(payload)
        print("[Server] [MagicStaffSkills] MagicStaffUsed event received from client right-click!")
        if payload and payload.player then
            handleStaffUse(payload.player, payload.itemStack)
        end
    end)
end

-- REMOTE EVENT FOR KEY 'Z' SKILL SWAP
if Network then
    local swapSkillEvent = Network.GetOrCreateRemoteEvent("StaffSwapSkill")
    if swapSkillEvent and swapSkillEvent.OnServerEvent then
        swapSkillEvent.OnServerEvent:Connect(function(player)
            print("[MagicStaffSkills] StaffSwapSkill RemoteEvent received for player: " .. tostring(player))
            if player then
                local state = getOrCreatePlayerState(player)
                if state then
                    state.selectedSkillIndex = (state.selectedSkillIndex % #SKILLS) + 1
                    local newSkill = SKILLS[state.selectedSkillIndex]
                    savePlayerState(player, state)
                    player:playSound("minecraft:block.lever.click", 1.0, 1.4)
                    updateActionBar(player, state, string.format("§6[Switched Skill] §e-> %s", newSkill.name))
                else
                    print("[ERROR] getOrCreatePlayerState returned NIL!")
                end
            else
                print("[ERROR] RemoteEvent player parameter is NIL!")
            end
        end)
    else
        print("[ERROR] Network.GetOrCreateRemoteEvent returned NIL or missing OnServerEvent!")
    end
else
    print("[ERROR] Network module is NIL in MagicStaffSkills.lua!")
end

print("[MagicStaffSkills] Staff Skill & Mana System Ready!")
