-- ===================================================================
-- Archmage Magic Staff: Server-Side Multi-Skill & Persistent Mana Engine
-- Clean ASCII Formatting & Storage Persistence
-- ===================================================================
local Entities = require("LuaTweaker.Entities")
local AIGoals  = require("LuaTweaker.AIGoals")
local Storage  = require("LuaTweaker.Storage")
local Network  = require("LuaTweaker.Network") or _G.Network or _G.NetworkService
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
    if not player or not state then return end
    local skill = SKILLS[state.selectedSkillIndex] or SKILLS[1]
    local now = os.clock()
    local lastCast = (state.lastCastTimes and state.lastCastTimes[state.selectedSkillIndex]) or 0
    local cdRemaining = math.max(0, skill.cooldown - (now - lastCast))
    
    local cdStatus = cdRemaining > 0 and string.format(" §c[CD: %.1fs]", cdRemaining) or " §a[READY]"
    local manaBarStr = buildManaBar(state.mana, state.maxMana)
    local barText = customMessage or string.format("§b[Staff] §e%s §7| §dMana: §b%s §7(%d/%d)%s", skill.name, manaBarStr, state.mana, state.maxMana, cdStatus)
    
    player:sendActionBar(barText)
    print("[MagicStaffHUD] ActionBar sent to " .. tostring(player:getName()) .. ": " .. tostring(barText))

    if Network then
        local syncEvent = Network:GetOrCreateRemoteEvent("StaffManaSync")
        if syncEvent then
            syncEvent:FireClient(player, state.mana, state.maxMana, skill.name)
        end
    end
end

-- DYNAMIC MANA REGENERATION & CONTINUOUS ACTION BAR HUD LOOP (+10 Mana per second)
print("[DEBUG] magic_staff.lua: Before Task.spawn")
Task.spawn(function()
    print("[MagicStaffHUD] Successfully started Task.spawn coroutine!")
    print("[MagicStaffHUD] Starting Mana Regeneration & HUD loop coroutine...")
    while true do
        Task.wait(0.4)
        local ok, err = pcall(function()
            local Players = require("LuaTweaker.Players")
            local online = Players and Players.GetPlayers and Players:GetPlayers()
            if online then
                for _, player in ipairs(online) do
                    local state = getOrCreatePlayerState(player)
                    if state then
                        if state.mana < state.maxMana then
                            state.mana = math.min(state.maxMana, state.mana + 4)
                            savePlayerState(player, state)
                        end
                        local mainItem = player:getMainHandItem()
                        local itemStr = tostring(mainItem or "")
                        if itemStr and (itemStr:find("magic_staff") or itemStr:find("staff") or itemStr:find("ruby")) then
                            updateActionBar(player, state)
                        end
                    end
                end
            end
        end)
        if not ok and err then
            print("[ERROR] [MagicStaffHUD] Loop error: " .. tostring(err))
        end
    end
end)
print("[DEBUG] magic_staff.lua: After Task.spawn")

-- HANDLE STAFF USE ACTION (Left-Click or Right-Click depending on binding)
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

    local skillEffectEvent = Network and Network.GetOrCreateRemoteEvent("StaffSkillEffectClient")

    if skillIdx == 1 then
        -- SKILL 1: RUBY ORB FIREBALL
        player:playSound("minecraft:entity.firework_rocket.blast", 1.0, 1.2)
        player:shootProjectile("luatweaker:ruby_orb", 1.8)
        if skillEffectEvent then skillEffectEvent:FireClient(player, "fireball") end
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
        if skillEffectEvent then skillEffectEvent:FireClient(player, "summon") end
        updateActionBar(player, state, "§a[Summon] Ruby Guardian summoned to defend you!")

    elseif skillIdx == 3 then
        -- SKILL 3: AEGIS SHIELD BARRIER
        player:playSound("minecraft:item.armor.equip_diamond", 1.0, 1.2)
        player:playSound("minecraft:entity.zombie_villager.cure", 1.0, 1.5)
        player:spawnParticle("minecraft:enchanted_hit", 40, 0.4)
        
        player:addEffect("resistance", 160, 2) -- 8s Resistance III
        player:addEffect("regeneration", 160, 1) -- 8s Regeneration II
        player:addEffect("absorption", 600, 1)   -- Absorption II
        if skillEffectEvent then skillEffectEvent:FireClient(player, "aegis") end
        updateActionBar(player, state, "§d[Aegis Barrier] Resistance III & Regeneration II Active!")
    end
end
_G.HandleMagicStaffUse = handleStaffUse

-- LISTEN TO MAGIC STAFF RIGHT-CLICK ACTION FROM STARTUP SCRIPT
local Events = require("LuaTweaker.Events")
print("[DEBUG] magic_staff.lua: Required Events")
if Events then
    print("[DEBUG] magic_staff.lua: Calling Events:Listen...")
    Events:Listen("MagicStaffUsed", function(payload)
        print("[Server] [MagicStaffSkills] MagicStaffUsed event received from client right-click!")
        if payload and payload.player then
            handleStaffUse(payload.player, payload.itemStack)
        end
    end)
end

-- REMOTE EVENTS FOR KEYBIND CAST ('G') AND SKILL SWAP ('R')
print("[DEBUG] magic_staff.lua: Checking Network...")
if Network then
    print("[DEBUG] magic_staff.lua: Registering StaffCastSkill...")
    print("[DEBUG] magic_staff.lua: Registering StaffCastSkill...")
    local castSkillEvent = Network:GetOrCreateRemoteEvent("StaffCastSkill")
    print("[DEBUG] magic_staff.lua: Checked castSkillEvent: ", tostring(castSkillEvent))
    if castSkillEvent then
        print("[DEBUG] magic_staff.lua: Checking OnServerEvent: ", tostring(castSkillEvent.OnServerEvent))
        if castSkillEvent.OnServerEvent then
            print("[DEBUG] magic_staff.lua: Calling Connect...")
            castSkillEvent.OnServerEvent:Connect(function(player)
                print("[Server] [MagicStaffSkills] Received CAST SKILL input from Client (" .. tostring(player:getName()) .. ")")
                local state = getOrCreatePlayerState(player)
                handleStaffUse(player, nil)
            end)
            print("[DEBUG] magic_staff.lua: Connect finished!")
        end
    end

    print("[DEBUG] magic_staff.lua: Registering StaffSwapSkill...")
    local swapSkillEvent = Network:GetOrCreateRemoteEvent("StaffSwapSkill")
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
