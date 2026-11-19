# LuaTweaker LuaMod template

Copy this folder to `run/luamods/<your_id>/` (dev: `neoforge-platform/luamods/<your_id>/`).

## Files

- `manifest.json` — identity + `environment` (`client` / `server` / `universal`) + deps.
- `main.lua` — the ONLY entrypoint; `require` your own `src/` tree from here.
- `default_config.json` — defaults copied to `luaconfig/<your_id>.json`; read via `mod:GetConfig()`.

## Dependencies

```json
{
  "dependencies": ["required_base"],
  "optional_dependencies": ["extras"]
}
```

- `dependencies`: hard — skipped with a `/lt doctor` error when absent.
- `optional_dependencies`: soft — ordered after the target when present, warns and continues when absent.
- Guard at runtime: `if mod:HasMod("extras") then ... end`, `mod:GetModVersion("extras")`.

## Share / consume APIs

```lua
mod:ExportAPI("MyApi", myApi)                 -- publish
local api = mod:ImportAPI("other_mod", "Api") -- consume (nil when absent)
```

## Rules (Roblox/Luau style)

- `local` everything; no implicit globals (except engine-provided `mod`, `Mod`, `Events`, ...).
- Colon calls (`object:method()`), max ~3 chain.
- Config-driven tunables: costs, cooldowns, spawn rates come from `mod:GetConfig()`.
- ASCII logs only.
