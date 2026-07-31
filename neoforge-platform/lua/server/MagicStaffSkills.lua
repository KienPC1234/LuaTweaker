-- ===================================================================
-- Archmage Magic Staff: Server-Side Multi-Skill & Persistent Mana Engine
-- Clean ASCII Minecraft Formatting (NO Emojis) & PlayerStorage Persistence
-- ===================================================================

print("[MagicStaffSkills] Initializing Magic Staff System with Storage Persistence...")

local EntityService = Mod:GetService("EntityService")
local AIGoals = Mod:GetService("AIGoals")
local PlayerStorage = Mod:GetService("PlayerStorage")
local SessionStorage = Mod:GetService("SessionStorage")
local NetworkService = Mod:GetService("NetworkService")
local Task = Mod:GetService("Task")

-- PLAYER STATE TRACKER (In-Memory Cache backed by PlayerStorage)
local playerStates = {}

local SKILLS = {
    { name = "Ruby Orb Fireball", cost = 20, cooldown = 1.5 },
    { name = "Summon Ruby Guardian", cost = 50, cooldown = 10.0 },
    { name = "Aegis Shield Barrier", cost = 35, cooldown = 8.0 }
}

local function getOrCreatePlayerState(player)
    local uuid = player:getUuid()
    if not playerStates[uuid] then
        local pStore = PlayerStorage:GetPlayerStorage(uuid)
        local savedMana = pStore:GetAsync("StaffMana")
        local savedSkill = pStore:GetAsync("StaffSkillIndex")

        local mana = (savedMana and type(savedMana) == "number") and savedMana or 100
        local skillIdx = (savedSkill and type(savedSkill) == "number") and math.floor(savedSkill) or 1

        if skillIdx < 1 or skillIdx > #SKILLS then
            skillIdx = 1
        end

        playerStates[uuid] = {
            mana = math.clamp(mana, 0, 100),
            maxMana = 100,
            selectedSkillIndex = skillIdx,
            lastCastTimes = { 0, 0, 0 }
        }
    end
    return playerStates[uuid]
end

local function savePlayerState(player, state)
    local uuid = player:getUuid()
    local pStore = PlayerStorage:GetPlayerStorage(uuid)
    pStore:SetAsync("StaffMana", state.mana)
    pStore:SetAsync("StaffSkillIndex", state.selectedSkillIndex)
    SessionStorage:SetAsync("Staff_" .. uuid .. "_Skill", state.selectedSkillIndex)
end

local function buildManaBar(mana, maxMana)
    local filled = math.floor((math.max(0, math.min(maxMana, mana)) / maxMana) * 10)
    return string.rep("=", filled) .. string.rep("-", 10 - filled)
end

local function updateActionBar(player, state, customMessage)
    local skill = SKILLS[state.selectedSkillIndex]
    local now = os.time()
    local lastCast = state.lastCastTimes[state.selectedSkillIndex] or 0
    local cdRemaining = math.max(0, skill.cooldown - (now - lastCast))
    
    local cdStatus = cdRemaining > 0 and string.format(" §c[CD: %.1fs]", cdRemaining) or " §a[READY]"
    local manaBarStr = buildManaBar(state.mana, state.maxMana)
    local barText = customMessage or string.format("§b[Staff] Skill: §e%s §7| §dMana: %d/%d §a[%s]%s", skill.name, state.mana, state.maxMana, manaBarStr, cdStatus)
    
    player:sendActionBar(barText)
end

-- DYNAMIC MANA REGENERATION & ACTION BAR DISPLAY LOOP (+10 Mana per second)
Task:spawn(function()
    while true do
        Task:wait(0.5)
        for uuid, state in pairs(playerStates) do
            -- Auto-regen Mana
            if state.mana < state.maxMana then
                state.mana = math.min(state.maxMana, state.mana + 5)
                SessionStorage:SetAsync("Mana_" .. uuid, state.mana)
            end
        end
    end
end)

-- GLOBAL EXPOSED STAFF ACTION HANDLER (Called from ruby_content.lua)
function _G.HandleMagicStaffUse(player, itemStack)
    local state = getOrCreatePlayerState(player)
    local now = os.time()

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
        
        local px, py, pz = player:getX(), player:getY(), player:getZ()
        local minion = EntityService:spawnEntity("luatweaker:ruby_guardian", px + 1.5, py, pz + 1.5)
        if minion then
            minion.CustomName = "§c[Minion] " .. player:getName() .. "'s Ruby Guardian"
            minion:addEffect("speed", 6000, 1)
            minion:addEffect("glowing", 6000, 0)
            
            -- Override Minion AI: Target hostile monsters & defend player!
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

-- REMOTE EVENT FOR KEY 'Z' SKILL SWAP
if NetworkService then
    local swapSkillEvent = NetworkService:GetOrCreateRemoteEvent("StaffSwapSkill")
    swapSkillEvent.OnServerEvent:Connect(function(player)
        if player then
            local state = getOrCreatePlayerState(player)
            state.selectedSkillIndex = (state.selectedSkillIndex % #SKILLS) + 1
            local newSkill = SKILLS[state.selectedSkillIndex]
            savePlayerState(player, state)
            player:playSound("minecraft:block.lever.click", 1.0, 1.4)
            updateActionBar(player, state, string.format("§6[Switched Skill] §e-> %s", newSkill.name))
        end
    end)
end

print("[MagicStaffSkills] Staff Skill & Mana System Ready!")
