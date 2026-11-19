# Block capabilities, RF machines and pipes

Everything here is implemented and bound today. The engine supplies the low-level pieces
(a storage buffer + standard NeoForge capabilities + raw Lua primitives); the pipe or
machine logic stays in Lua.

---

## 1. Making a block that other mods can connect to

```lua
local pipe = Content.NewBlock("mymod:flux_pipe")
    :Texture("mymod:block/flux_pipe")
    :ConnectionState(true)          -- 6 boolean properties + 64 blockstate/model variants
    :EnergyStorage(2000, 256, 256)  -- capacity, maxReceive/tick, maxExtract/tick
    :OnTick(function(self) ... end)
    :Register()
```

- `:EnergyStorage(...)` and `:FluidStorage(...)` now create the block entity by themselves.
  Before this change they were silently ignored unless the block was also a container or
  had `:OnTick`/`:OnRender`.
- The block is exposed through `Capabilities.EnergyStorage.BLOCK`, `FluidHandler.BLOCK` and
  `ItemHandler.BLOCK` on **every face** (`NeoForgeContentRegistry.onRegisterCapabilities`).
  Other mods' cables, hoppers, ducts and scanners see it exactly like a native machine.
- `:EnergyStorage` rates are enforced on the capability path (`receiveEnergy` /
  `extractEnergy`). Writing `Energy` through `World:SetBlockEntityData` is a raw write and
  bypasses the rate — enforce your own transfer budget in Lua.

Pipe geometry: `:ConnectionState(true)` connects only to blocks of the **same block type**.
It cannot visually join another pipe type or another mod's cable without a custom model.

---

## 2. Talking to other mods' machines (`World:*` capability primitives)

These use `Level#getCapability`, so they work with any mod that implements the standard
NeoForge capabilities — no NBT guessing.

```
World:GetEnergy(x, y, z [, side])                   -> { Stored, Max, CanExtract, CanReceive } | nil
World:InsertEnergy(x, y, z, amount [, side])        -> accepted
World:ExtractEnergy(x, y, z, amount [, side])       -> extracted

World:GetItemSlots(x, y, z [, side])                -> { { Slot, Id, Count }, ... } | nil
World:InsertItem(x, y, z, itemId, count [, side])   -> inserted
World:ExtractItem(x, y, z, slot, count [, side])    -> { Slot, Id, Count } | nil

World:GetFluid(x, y, z [, side])                    -> { Id, Amount, Capacity } | nil
World:FillFluid(x, y, z, fluidId, amount [, side])  -> accepted
World:DrainFluid(x, y, z, fluidId, amount [, side]) -> drained   ("*" = first non-empty tank)
```

- `side` is `up`, `down`, `north`, `south`, `west`, `east` (any case) or omitted for the
  unsided lookup. An invalid side is rejected before the capability is queried.
- A leading dimension string is accepted like every other `World` call:
  `World:GetEnergy("minecraft:the_nether", 10, 64, -5, "up")` (default
  `minecraft:overworld`).
- `nil` means "no block entity / no such capability / dimension not loaded".
  `0` (or an empty `GetItemSlots` table) means "the capability exists but refused".
  Scripts must distinguish the two.
- `InsertItem` fills the first slots that accept the stack and returns how many items
  actually went in. `ExtractItem` targets one slot so the Lua code decides the order.
- `GetFluid` reads tank 0; `FillFluid` fills the handler and `DrainFluid` drains the first
  tank holding the requested fluid.
- Energy is stored as a 32-bit int (max ~2.1 billion FE per block).

### Example: an RF pipe that pulls from a machine and pushes to the next pipe

```lua
Content.NewBlock("mymod:flux_pipe")
    :Texture("mymod:block/flux_pipe")
    :ConnectionState(true)
    :EnergyStorage(2000, 256, 256)
    :OnTick(function(self)
        -- target block below: its UP face
        local pulled = World:ExtractEnergy(self.X, self.Y - 1, self.Z, 64, "up")
        if pulled <= 0 then
            return
        end
        -- target block above: its DOWN face
        local accepted = World:InsertEnergy(self.X, self.Y + 1, self.Z, pulled, "down")
        if accepted < pulled then
            -- keep whatever the neighbour refused in our own buffer
            World:InsertEnergy(self.X, self.Y, self.Z, pulled - accepted)
        end
    end)
    :Register()
```

An item pipe follows the same shape:
`GetItemSlots` → choose a slot → `ExtractItem` → `InsertItem`; if the destination refuses
part of the stack, the Lua code decides whether to drop it or put it back.

---

## 3. State that survives chunk reloads

The generic block entity saves `Items`, `Energy`, `FluidId`, `FluidAmount`, `Progress`
automatically. Any **custom** key a script writes through `World:SetBlockEntityData` must
live inside the `LuaData` compound, otherwise it is discarded on save:

```lua
World:SetBlockEntityData(x, y, z, {
    LuaData = { mode = "pull", throughput = 256 },
})
local data = World:GetBlockEntityData(x, y, z)
local mode = data and data.LuaData and data.LuaData.mode or "pull"
```

`Energy` is only present in the snapshot when it is greater than 0; use `(data.Energy or 0)`.

---

## 4. `/lt reload` behaviour

Block/entity/menu/capability registrations are frozen by NeoForge, but the Lua closures
are not. After `loadLuaMods`, `ContainerSpecRebinder` rebuilds each `ContainerSpec` from the
fresh builder and rebinds:

- `:OnTick`, `:ItemFilter`, `:OnRightClick`, `:OnButtonClick`, `:OnNeighborChanged`
- energy/fluid capacity and rates, GUI layout (bars, buttons, labels), textures
- the block entity renderer resolves `:OnRender` per frame, so that one rebinds too

What still needs a **game restart** (a warning is logged when a script tries it):

- changing rows/cols, `:BooleanState(...)`, `:ConnectionState(...)`, `:Facing(...)` or
  `:Box(...)` — they define the registered block state and shape;
- adding `:OnRender` to a block that had none at load — the renderer is registered at load;
- adding `:OnTick` to a block that had none at load — the ticker is attached when the block
  entity is created, so breaking and replacing the block (or restarting) picks it up.

### Custom per-pipe state and reload

The tick handler receives a snapshot table; write state back with
`World:SetBlockEntityData`. The generic block entity re-reads it from NBT, so the next tick
sees the new value. State kept only in a Lua closure is lost on reload — persist it in
`LuaData` if it matters.
