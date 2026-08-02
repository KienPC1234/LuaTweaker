-- ===================================================================
-- Archmage Magic Staff: Server-Side Multi-Skill & Persistent Mana Engine
-- Clean ASCII Formatting & Storage Persistence
-- ===================================================================
local Entities = require("LuaTweaker.Entities")
local AIGoals  = require("LuaTweaker.AIGoals")
local Storage  = require("LuaTweaker.Storage")
local Network  = require("LuaTweaker.Network") or _G.Network or _G.NetworkService
local Task     = require("LuaTweaker.Task")
local World    = require("LuaTweaker.World")

print("[MagicStaffSkills] Initializing Magic Staff System with Storage Persistence...")

-- PLAYER STATE TRACKER (In-Memory Cache backed by Storage)
local playerStates = {}

-- CONFIG-DRIVEN TUNING (luaconfig/ruby_mod.json — default_config.json on first launch)
local cfg = mod and mod:GetConfig() or {}
local MAX_MANA = tonumber(cfg.staff_max_mana) or 100
local MANA_REGEN_PER_TICK = tonumber(cfg.staff_mana_regen_per_tick) or 4

local SKILLS = cfg.staff_skills or {
    { name = "Ruby Orb Fireball", cost = 20, cooldown = 1.5 },
    { name = "Summon Ruby Guardian", cost = 50, cooldown = 10.0 },
    { name = "Aegis Shield Barrier", cost = 35, cooldown = 8.0 }
}

local TARGET_MARK_MAX_DISTANCE = tonumber(cfg.target_mark_max_distance) or 64
local TARGET_MARK_RADIUS = tonumber(cfg.target_mark_radius) or 1.0
local HOMING_RANGE = tonumber(cfg.homing_range) or 24
local HOMING_DART_SPEED = tonumber(cfg.homing_dart_speed) or 2.0
local MINION_FOLLOW_SPEED = tonumber(cfg.minion_follow_speed) or 1.1
local MINION_ATTACK_SPEED = tonumber(cfg.minion_attack_speed) or 1.3

-- PRIVATE FUNCTIONS

---@param player Player
local function getOrCreatePlayerState(player)
    local uuid = player:getUuid()
    if not playerStates[uuid] then
        local savedMana = Storage:get("StaffMana_" .. uuid, MAX_MANA)
        local savedSkill = Storage:get("StaffSkillIndex_" .. uuid, 1)

        local mana = (savedMana and type(savedMana) == "number") and savedMana or MAX_MANA
        local skillIdx = (savedSkill and type(savedSkill) == "number") and math.floor(savedSkill) or 1

        if skillIdx < 1 or skillIdx > #SKILLS then
            skillIdx = 1
        end

        playerStates[uuid] = {
            mana = math.clamp(mana, 0, MAX_MANA),
            maxMana = MAX_MANA,
            selectedSkillIndex = skillIdx,
            lastCastTimes = {},
            player = player,
            markedTargetUuid = nil,
            markedTarget = nil,
            minion = nil,
            lastBarText = nil,
            lastSyncHolding = nil
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
    return string.rep("=", filled) .. string.rep("-", totalSegments - filled)
end

local function syncStaffState(player, state, isHolding)
    local skill = SKILLS[state.selectedSkillIndex] or SKILLS[1]
    if Network then
        local syncEvent = Network:GetOrCreateRemoteEvent("StaffManaSync")
        if syncEvent then
            syncEvent:FireClient(player, state.mana, state.maxMana, skill.name, isHolding == true)
        end
    end
end

local function updateActionBar(player, state, customMessage)
    if not player or not state then return end
    local skill = SKILLS[state.selectedSkillIndex] or SKILLS[1]
    local now = os.clock()
    local lastCast = (state.lastCastTimes and state.lastCastTimes[state.selectedSkillIndex]) or 0
    local cdRemaining = math.max(0, skill.cooldown - (now - lastCast))

    local cdStatus = cdRemaining > 0 and string.format(" [CD: %.1fs]", cdRemaining) or " [READY]"
    local manaBarStr = buildManaBar(state.mana, state.maxMana)
    local barText = customMessage or string.format("[Staff] %s | Mana: %s (%d/%d)%s", skill.name, manaBarStr, state.mana, state.maxMana, cdStatus)

    -- Dedupe: only push a new action bar + mana sync packet when the text
    -- actually changed. Idle players (full mana, no cooldown) cost nothing.
    if barText == state.lastBarText then return end
    state.lastBarText = barText
    state.lastSyncHolding = true

    player:sendActionBar(barText)
    print("[MagicStaffHUD] ActionBar sent to " .. tostring(player:getName()) .. ": " .. tostring(barText))
    syncStaffState(player, state, true)
end

local function getLookVector(player)
    local yaw = math.rad(player:getYaw())
    local pitch = math.rad(player:getPitch())
    local dx = -math.sin(yaw) * math.cos(pitch)
    local dy = -math.sin(pitch)
    local dz = math.cos(yaw) * math.cos(pitch)
    return dx, dy, dz
end

-- Pure-Lua raycast: nearest living entity inside a cone along the player's look direction.
---@param player Player
local function raycastNearestVisible(player, maxDistance, radius)
    local px, py, pz = player:getX(), player:getY() + 1.6, player:getZ()
    local dx, dy, dz = getLookVector(player)
    local entities = World:GetEntitiesInRadius(player, maxDistance)
    local best, bestT = nil, math.huge
    for _, ent in ipairs(entities or {}) do
        if ent ~= player and ent:isAlive() then
            local ex, ey, ez = ent:getX(), ent:getY() + 1.0, ent:getZ()
            local t = (ex - px) * dx + (ey - py) * dy + (ez - pz) * dz
            if t > 0 and t < maxDistance and t < bestT then
                local cx = px + dx * t
                local cy = py + dy * t
                local cz = pz + dz * t
                local dist = math.sqrt((ex - cx) * (ex - cx) + (ey - cy) * (ey - cy) + (ez - cz) * (ez - cz))
                if dist <= radius then
                    best, bestT = ent, t
                end
            end
        end
    end
    return best
end

-- The summoned guardian only FOLLOWS until its owner orders it to attack
-- (by marking a target). Ordering adds the attack goals + sets the target.
-- Declared BEFORE markTarget: Lua locals must exist before the call site is compiled.
local function orderMinionToAttack(player, state, target)
    local minion = state.minion
    if not minion or not minion:isAlive() then return end
    minion:setTarget(target)
    if AIGoals then
        AIGoals:addNearestAttackableTargetGoal(minion, 1, "monster")
        AIGoals:addHurtByTargetGoal(minion, 2)
        AIGoals:addMeleeAttackGoal(minion, 3, MINION_ATTACK_SPEED, false)
    end
end

local function markTarget(player, state, target)
    if target then
        state.markedTargetUuid = target:getUuid()
        state.markedTarget = target
        if state.minion and state.minion:isAlive() then
            orderMinionToAttack(player, state, target)
        end
    else
        state.markedTargetUuid = nil
        state.markedTarget = nil
    end
    local sync = Network and Network:GetOrCreateRemoteEvent("TargetMarked")
    if sync then
        sync:FireClient(player, state.markedTargetUuid or "")
    end
end

-- DYNAMIC MANA REGENERATION & CONTINUOUS ACTION BAR HUD LOOP
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
                            state.mana = math.min(state.maxMana, state.mana + MANA_REGEN_PER_TICK)
                            savePlayerState(player, state)
                        end
                        local mainItem = player:getMainHandItem()
                        local itemStr = tostring(mainItem or "")
                        local isHoldingStaff = itemStr ~= nil and itemStr:find("magic_staff") ~= nil
                        if isHoldingStaff then
                            updateActionBar(player, state)
                        else
                            -- Hide the client HUD when the staff is not held
                            -- (dedupe: send the "not holding" state only once).
                            if state.lastSyncHolding ~= false then
                                syncStaffState(player, state, false)
                                state.lastSyncHolding = false
                            end
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

-- Fire ONE projectile from the player and steer it every 0.05s toward the
-- locked target until it dies, the target dies, or the mark is removed.
---@param player Player
---@param state table
---@param target table (entity)
---@param speed number
local function fireHomingDart(player, state, target, speed)
    local lockedUuid = target:getUuid()
    Task.spawn(function()
        local dart = player:shootProjectileAt("luatweaker:ruby_orb", target, speed)
        if not dart then return end
        local homingTicks = tonumber(cfg.homing_max_ticks) or 100
        while homingTicks > 0 do
            if not dart:isAlive() then break end
            if lockedUuid ~= state.markedTargetUuid then dart:remove() break end
            local currentTarget = World and World.GetEntity and World:GetEntity(lockedUuid)
            if not currentTarget or not currentTarget:isAlive() then break end
            local tx, ty, tz = currentTarget:getX(), currentTarget:getY() + 1.0, currentTarget:getZ()
            local px, py, pz = dart:getX(), dart:getY(), dart:getZ()
            local dx, dy, dz = tx - px, ty - py, tz - pz
            local len = math.sqrt(dx * dx + dy * dy + dz * dz)
            if len <= 0.5 then break end
            dart:setMotion(dx / len * speed, dy / len * speed, dz / len * speed)
            Task.wait(0.05)
            homingTicks = homingTicks - 1
        end
    end)
end

-- HANDLE STAFF USE ACTION (Left-Click or Right-Click depending on binding)
---@param player Player
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
        updateActionBar(player, state, string.format("[Switched Skill] -> %s", newSkill.name))
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
        updateActionBar(player, state, string.format("[Cooldown] %s ready in %.1fs!", skill.name, remaining))
        return
    end

    if state.mana < skill.cost then
        player:playSound("minecraft:block.chest.locked", 1.0, 0.8)
        updateActionBar(player, state, string.format("[Low Mana] Need %d Mana (Current: %d)", skill.cost, state.mana))
        return
    end

    -- DEDUCT MANA & SET COOLDOWN
    state.mana = state.mana - skill.cost
    state.lastCastTimes[skillIdx] = now
    savePlayerState(player, state)

    local skillEffectEvent = Network and Network.GetOrCreateRemoteEvent("StaffSkillEffectClient")

    if skillIdx == 1 then
        -- SKILL 1: RUBY ORB FIREBALL — turns homing when a target is marked.
        player:playSound("minecraft:entity.firework_rocket.blast", 1.0, 1.2)
        local marked = state.markedTarget
        if marked and marked:isAlive() then
            fireHomingDart(player, state, marked, HOMING_DART_SPEED)
            updateActionBar(player, state, "[Cast] Ruby Orb homing toward " .. marked:getName())
        else
            player:shootProjectile("luatweaker:ruby_orb", 1.8)
            updateActionBar(player, state, "[Cast] Ruby Orb Fireball launched!")
        end
        if skillEffectEvent then skillEffectEvent:FireClient(player, "fireball") end

    elseif skillIdx == 2 then
        -- SKILL 2: SUMMON RUBY GUARDIAN MINION (follows owner; attacks only when ordered)
        player:playSound("minecraft:entity.evoker.prepare_summon", 1.0, 1.0)
        player:spawnParticle("minecraft:totem_of_undying", 30, 0.5)
        local minion = player:spawnEntity("luatweaker:ruby_boss", 0, 1, 0)
        if minion then
            state.minion = minion
            minion:addTag("ruby_minion")
            if AIGoals then
                AIGoals:clearGoals(minion)
                -- FOLLOW MODE: walk to the owner until a target is marked.
                AIGoals:addGoal(minion, 5, {
                    canUse = function() return true end,
                    tick = function()
                        if state.minion and state.minion:isAlive() and not state.markedTargetUuid then
                            state.minion:moveTo(player:getX(), player:getY(), player:getZ(), MINION_FOLLOW_SPEED)
                        end
                    end
                })
            end
            if state.markedTarget then
                orderMinionToAttack(player, state, state.markedTarget)
            end
        end
        if skillEffectEvent then skillEffectEvent:FireClient(player, "summon") end
        updateActionBar(player, state, "[Summon] Ruby Guardian summoned to defend you!")

    elseif skillIdx == 3 then
        -- SKILL 3: AEGIS SHIELD BARRIER
        player:playSound("minecraft:item.armor.equip_diamond", 1.0, 1.2)
        player:playSound("minecraft:entity.zombie_villager.cure", 1.0, 1.5)
        player:spawnParticle("minecraft:enchanted_hit", 40, 0.4)

        player:addEffect("resistance", 160, 2) -- 8s Resistance III
        player:addEffect("regeneration", 160, 1) -- 8s Regeneration II
        player:addEffect("absorption", 600, 1)   -- Absorption II
        if skillEffectEvent then skillEffectEvent:FireClient(player, "aegis") end
        updateActionBar(player, state, "[Aegis Barrier] Resistance III & Regeneration II Active!")

    elseif skillIdx == 4 then
        -- SKILL 4: HOMING RUBY DART — auto-locks the nearest visible entity,
        -- draws a client-only border box, then fires an aimed barrage at it.
        local target = raycastNearestVisible(player, HOMING_RANGE, TARGET_MARK_RADIUS)
        if not target then
            state.mana = math.min(state.maxMana, state.mana + skill.cost)
            state.lastCastTimes[skillIdx] = 0
            player:playSound("minecraft:entity.ender_eye.death", 0.8, 0.6)
            updateActionBar(player, state, "[Homing Dart] No target in view")
            return
        end
        markTarget(player, state, target)
        player:playSound("minecraft:entity.blaze.shoot", 1.0, 1.4)
        if skillEffectEvent then skillEffectEvent:FireClient(player, "homing") end
        updateActionBar(player, state, "[Homing Dart] Locked: " .. target:getName())

        fireHomingDart(player, state, target, HOMING_DART_SPEED)
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
    local castSkillEvent = Network:GetOrCreateRemoteEvent("StaffCastSkill")
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
                    updateActionBar(player, state, string.format("[Switched Skill] -> %s", newSkill.name))
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

    -- KEYBIND 'X': MARK TARGET — raycast the entity the player is looking at,
    -- order the minion to attack it, and notify the client to draw the outline.
    print("[DEBUG] magic_staff.lua: Registering TargetMark...")
    local markTargetEvent = Network:GetOrCreateRemoteEvent("TargetMark")
    if markTargetEvent and markTargetEvent.OnServerEvent then
        markTargetEvent.OnServerEvent:Connect(function(player)
            print("[MagicStaffSkills] TargetMark received for player: " .. tostring(player))
            if player then
                local state = getOrCreatePlayerState(player)
                local target = raycastNearestVisible(player, TARGET_MARK_MAX_DISTANCE, TARGET_MARK_RADIUS)
                markTarget(player, state, target)
                if target then
                    updateActionBar(player, state, "[Target] Marked: " .. target:getName())
                else
                    updateActionBar(player, state, "[Target] No entity in view")
                end
            end
        end)
    end
else
    print("[ERROR] Network module is NIL in MagicStaffSkills.lua!")
end

print("[MagicStaffSkills] Staff Skill & Mana System Ready!")
