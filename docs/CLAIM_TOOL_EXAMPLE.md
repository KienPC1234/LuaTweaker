# Claim tool example (built from primitives)

This is a **reference composition**, not a built-in claim system. LuaTweaker supplies the
primitives; the claim logic lives entirely in your LuaMod.

## Primitives used

| Need | Primitive |
|---|---|
| Select a corner | `Content.NewItem(...):OnUseOnBlock(handler)` |
| Box math | `Region3.new(min, max)` -> `Contains`, `Intersects`, `Expand`, `Union`, `Volume`, `Center`, `Size` |
| Persist claims | `Storage:GetWorldStorage()` (`SetAsync` / `GetAsync` / `GetKeys` / `RemoveAsync`) |
| Trust list | `Storage:GetPlayerStorage(uuid)` |
| Deny break | `BlockBreak` event (return `false`), `BlockBreakSpeed` (`SetSpeed(0)` or return `false`) |
| Deny place | `BlockPlace` event (return `false`) |
| Deny chest / door use | `PlayerInteractBlock` event (return `false`) |
| Deny entity use | `PlayerInteractEntity` event (return `false`) |
| Protect from explosions | `ExplosionDetonate` + `RemoveBlock(x, y, z)` |
| Visualise borders | `Particle:Line(...)` |
| Commands | `Commands:Register(name, definition)` |

## 1. The wand (`src/startup/wand.lua`)

```lua
local Content = require("LuaTweaker.Content")

Content.NewItem("claim_wand")
    :MaxStackSize(1)
    :DisplayName("Claim Wand")
    :OnUseOnBlock(function(player, hit)
        -- hit = { X, Y, Z, Face }
        require(".src.server.claims"):SetCorner(player, hit)
        return true
    end)
    :Register()
```

## 2. Claim storage (`src/server/claims.lua`)

```lua
local Storage = require("LuaTweaker.Storage")

local Claims = {}
local WORLD = Storage:GetWorldStorage()
local MIN_SIZE = 2

local function claimKey(id)
    return "claim:" .. id
end

-- Returns the claim table for a point, or nil.
function Claims:FindAt(dimension, x, y, z)
    for _, id in ipairs(WORLD:GetKeys()) do
        if string.sub(id, 1, 6) == "claim:" then
            local claim = WORLD:GetAsync(id)
            if claim
                and claim.Dimension == dimension
                and claim.MinX <= x and x <= claim.MaxX
                and claim.MinY <= y and y <= claim.MaxY
                and claim.MinZ <= z and z <= claim.MaxZ then
                return claim
            end
        end
    end
    return nil
end

function Claims:IsAllowed(player, claim)
    if claim.Owner == player:GetUuid() then
        return true
    end
    local trusted = Storage:GetPlayerStorage(player:GetUuid()):GetAsync("trust")
    return trusted ~= nil and trusted[claim.Id] == true
end

function Claims:SetCorner(player, hit)
    local pending = Storage:GetPlayerStorage(player:GetUuid())
    local key = "claim_corner_" .. player:GetDimension()
    local corner = pending:GetAsync(key)
    if corner == nil then
        pending:SetAsync(key, { X = hit.X, Y = hit.Y, Z = hit.Z })
        player:SendMessage("§bCorner 1 set. Right-click again for corner 2.")
        return
    end
    local region = Region3.FromCoordinates(
        corner.X, corner.Y, corner.Z, hit.X, hit.Y, hit.Z)
    local size = region:Size()
    if size.X < MIN_SIZE or size.Z < MIN_SIZE then
        player:SendMessage("§cClaim is too small (min " .. MIN_SIZE .. " blocks wide).")
        return
    end
    local id = "claim_" .. math.floor(WORLD:IncrementAsync("claim_next_id", 1))
    WORLD:SetAsync(claimKey(id), {
        Id = id,
        Owner = player:GetUuid(),
        OwnerName = player:GetName(),
        Dimension = player:GetDimension(),
        MinX = region.MinX, MinY = region.MinY, MinZ = region.MinZ,
        MaxX = region.MaxX, MaxY = region.MaxY, MaxZ = region.MaxZ,
    })
    pending:SetAsync(key, nil)
    player:SendMessage("§aClaim created: §f" .. id)
end

return Claims
```

## 3. Protection listeners (`src/server/protection.lua`)

```lua
local Events = require("LuaTweaker.Events")
local Claims = require(".src.server.claims")

local function protectedAt(event)
    local claim = Claims:FindAt(event.Dimension, event.pos.X, event.pos.Y, event.pos.Z)
    if claim == nil or Claims:IsAllowed(event.Player, claim) then
        return nil
    end
    return claim
end

Events:Listen("BlockBreak", function(event)
    local claim = protectedAt(event)
    if claim then
        event.Player:SendMessage("§cThis area is claimed by " .. claim.OwnerName)
        return false -- cancel the break
    end
end)

Events:Listen("BlockPlace", function(event)
    if protectedAt(event) then
        return false
    end
end)

-- BlockBreakSpeed fires every destroy tick; zeroing the speed stalls mining
-- even before the break event would fire.
Events:Listen("BlockBreakSpeed", function(event)
    if protectedAt(event) then
        event:SetSpeed(0)
    end
end)

Events:Listen("PlayerInteractBlock", function(event)
    local claim = Claims:FindAt(event.Dimension, event.pos.X, event.pos.Y, event.pos.Z)
    if claim and not Claims:IsAllowed(event.Player, claim) then
        event.Player:SendMessage("§cYou cannot use blocks here.")
        return false
    end
end)

-- Explosions: keep only the blocks outside claims.
Events:Listen("ExplosionDetonate", function(event)
    local region = nil
    for _, pos in ipairs(event.AffectedBlocks) do
        region = Claims:FindAt(event.Dimension, pos.X, pos.Y, pos.Z)
        if region then
            event:RemoveBlock(pos)
        end
    end
end)
```

`RemoveBlock` edits the explosion's live block list before vanilla destroys anything, so the
claimed blocks survive the blast. `ClearBlocks()` is available when the whole blast must be
cancelled.

## 4. Commands (`src/server/commands.lua`)

```lua
local Commands = require("LuaTweaker.Commands")
local Storage  = require("LuaTweaker.Storage")
local Claims   = require(".src.server.claims")

Commands:Register("claim", {
    Description = "Claim management",
    PermissionLevel = 0,
    ConsoleAllowed = false,
    Handler = function(sender, args)
        local player = sender.Player
        if not player then
            sender:SendMessage("§cPlayers only.")
            return
        end
        local action = args[1] or "help"

        if action == "trust" and args[2] then
            local trusted = Storage:GetPlayerStorage(player:GetUuid()):GetAsync("trust") or {}
            trusted[args[2]] = true
            Storage:GetPlayerStorage(player:GetUuid()):SetAsync("trust", trusted)
            player:SendMessage("§aTrusted claim id " .. args[2])
        elseif action == "list" then
            sender:SendMessage("§bClaims owned by you:")
            for _, id in ipairs(Storage:GetWorldStorage():GetKeys()) do
                local claim = Storage:GetWorldStorage():GetAsync(id)
                if claim and claim.Owner == player:GetUuid() then
                    sender:SendMessage("§7- " .. claim.Id)
                end
            end
        else
            sender:SendMessage("§b/claim trust <id> | /claim list")
        end
    end,
})
```

## 5. Drawing a claim border

```lua
local Particle = require("LuaTweaker.Particle")

local function drawBox(dimension, region)
    local y = region.MinY + 0.1
    Particle:Line("minecraft:end_rod",
        region.MinX, y, region.MinZ, region.MaxX, y, region.MinZ, 0.5, 0, dimension)
    Particle:Line("minecraft:end_rod",
        region.MaxX, y, region.MinZ, region.MaxX, y, region.MaxZ, 0.5, 0, dimension)
    Particle:Line("minecraft:end_rod",
        region.MaxX, y, region.MaxZ, region.MinX, y, region.MaxZ, 0.5, 0, dimension)
    Particle:Line("minecraft:end_rod",
        region.MinX, y, region.MaxZ, region.MinX, y, region.MinZ, 0.5, 0, dimension)
end
```

`Region3` is geometry only: it never reads the world, so you decide which dimension/protection
rules apply.

## Honest limits

- `FindAt` scans every stored claim key. For hundreds of claims, build a chunk index in Lua
  (for example key `claim_chunk:<dimension>:<chunkX>:<chunkZ>` holding a list of claim ids).
- There is **no** container-slot click event, so a claim can block chest *opening* but cannot
  detect a scripted inventory stealer inside an allowed container. Use `ContainerOpen` /
  `ContainerClose` to rate-limit access.
- There is no block-change journal, so rollback needs your own log (every `BlockBreak` /
  `BlockPlace` payload is available to record).
- Explosion protection covers vanilla-style explosions that fire `ExplosionDetonate`; a mod that
  edits blocks directly without firing events bypasses it.
