-- ================================================================
-- ARCANE RPG: Low-Level Dynamic Bridge Demo
-- Demonstrates Tier 2 API: Direct Java access via DynamicJavaProxy
-- ================================================================
local Events = require("LuaTweaker.Events")

-- ================================================================
-- PATTERN 1: Raw Event Object Access
-- Events:Listen delivers raw NeoForge events wrapped as DynamicJavaProxy.
-- Modder can read Java getters directly as properties (event.Amount).
-- ================================================================
Events:Listen("EntityHurt", function(event)
    if event == nil then return end
    -- event is LivingDamageEvent.Pre wrapped as a proxy.
    local amount = event.Amount          -- -> getAmount()
    if amount ~= nil and amount > 40.0 then
        -- Cap absurd incoming damage (e.g. from exploits) at 40 HP.
        local newAmount = 40.0
        event:setAmount(newAmount)
    end
end)

-- ================================================================
-- PATTERN 2: Raw NBT Access via getPersistentData()
-- Reads/writes the raw Java CompoundTag of the entity through the proxy.
-- ================================================================
Events:Listen("PlayerJoin", function(event)
    if event == nil then return end
    local player = event.Entity          -- -> getEntity() (raw Java Player proxy)
    if player == nil then
        player = event:getEntity()
    end
    if player == nil then return end
    -- getPersistentData() returns a CompoundTag; putInt maps to putInt(int, int).
    -- This is real low-level NBT access without any LuaTweaker wrapper.
    local nbt = player:getPersistentData()
    if nbt ~= nil then
        nbt:putInt("ArcaneLevel", 1)
    end
end)

-- ================================================================
-- PATTERN 3: Raw Java Method Calls + Heuristic Resolution
-- BlockBreak event -> raw player. giveExperience() is resolved by the
-- RuntimeRemapper heuristic to Player#giveExperiencePoints(int) even though
-- the method name differs (obfuscation / naming variance safe).
-- NOTE: sendActionBar is an IPlayer wrapper method that does NOT exist on the
-- raw Java Player, so it must NOT be called on a raw proxy.
-- ================================================================
Events:Listen("BlockBreak", function(event)
    if event == nil then return end
    local player = event.Player          -- -> getPlayer() (raw Java Player proxy)
    if player ~= nil then
        -- getMainHandItem() returns an ItemStack proxy. Compare via the item's
        -- description id ("item.luatweaker.crystal_staff"), NOT against a string.
        local stack = player.MainHandItem
        if stack ~= nil then
            local item = stack.Item      -- -> getItem()
            local descId = item ~= nil and item:getDescriptionId() or ""
            if descId == "item.luatweaker.crystal_staff" then
                -- Heuristic resolves giveExperience -> giveExperiencePoints.
                player:giveExperience(5)
            end
        end
    end
end)

print("[ArcaneRPG] Dynamic Bridge patterns loaded: 3 working patterns")
