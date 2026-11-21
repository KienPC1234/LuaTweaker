# JSON API

LuaTweaker provides high-performance, sandboxed JSON encoding and decoding through the `Json` service and per-mod file capability handles (`mod:Files()` and `mod:Exports()`).

All operations are backed by Java's Gson library with native type conversions into Lua primitives and tables.

---

## 1. Quick Start

### Global Access & Require

The service is accessible globally as `Json` (or `json`) or via `require`:

```lua
local Json = require("LuaTweaker.Json")

-- Decode JSON string into a Lua table
local data = Json.Decode('{"name": "Crystal Staff", "tier": 4, "tags": ["weapon", "magic"]}')
print(data.name)      -- "Crystal Staff"
print(data.tier)      -- 4
print(data.tags[1])   -- "weapon"

-- Encode a Lua table into JSON
local compact = Json.Encode(data)
local pretty  = Json.Encode(data, true)
```

---

## 2. API Specification

### `Json.Decode(jsonString)` / `Json.Parse(jsonString)`
- **Parameters**: `jsonString: string`
- **Returns**: `table | number | string | boolean | nil`
- **Aliases**: `decode`, `parse`, `Parse`
- **Call syntax**: `Json:Decode(s)` or `Json.Decode(s)` — both work because the first argument is a string, never a table.
- **Behavior**:
  - JSON objects map to Lua tables with string keys.
  - JSON arrays map to contiguous 1-based Lua tables (`#arr` matches array length).
  - Whole numbers remain integer numbers (`math.type(x) == "integer"`).
  - Floating point numbers become standard floats.
  - `null` maps to `nil`.
  - Malformed JSON raises a descriptive Lua error (can be caught via `pcall`).
- Use `Json.IsValid(s)` below as a cheap pre-check when you only need a boolean.

### `Json.IsValid(jsonString)`
- **Parameters**: `jsonString: string`
- **Returns**: `boolean` (never throws)
- **Aliases**: `isValid`, `Isvalid` via the PascalCase binder
- **Behavior**:
  - `true` for any valid JSON document (object, array, string, number, `true`/`false`/`null`).
  - `false` for `nil`, blank strings and malformed JSON — no error, no log line.
  - A pre-check only: if you need the value, still `Decode` inside `pcall`
    (the file can change between the check and the read).

### `Json.Encode(value [, pretty])` / `Json.Stringify(value [, pretty])`
- **Parameters**: `value: any`, `pretty: boolean` (optional, default `false`)
- **Returns**: `string`
- **Aliases**: `encode`, `stringify`, `Stringify`
- **Call syntax**: **must use the colon form** — `Json:Encode(t)`, `Json:Stringify(t)`.
  When `value` is a table, the binder cannot tell it apart from the implicit
  `self` of a colon call, so `Json.Encode(t)` is read as `self=t` with a missing
  argument and fails with `encode requires argument 1`. Scalars are unambiguous,
  so `Json.Encode(42)` also works.
- **Behavior**:
  - Contiguous 1-based arrays (`1..N`) encode as JSON arrays `[...]`.
  - Key-value tables and empty tables `{}` encode as JSON objects `{...}`.
  - Scalars (numbers, strings, booleans, nil) encode to their JSON counterparts.
  - Circular table references are detected and throw an error safely (no stack overflow).
  - Passing `pretty = true` formats the output with 2-space indentation.

---

## 3. Mod File I/O Integration

Lua mods can read and write JSON files directly through `mod:Files()` and `mod:Exports()` without manual string parsing.

### Reading Shipped JSON Files (`mod:Files():ReadJson`)

Read-only access to files inside your mod's directory or `.zip` (e.g. `luamods/<mod_id>/data/items.json`):

```lua
-- In your mod's Lua code
local files = mod:Files()

if files:Exists("data/custom_items.json") then
    local items = files:ReadJson("data/custom_items.json")
    for _, item in ipairs(items) do
        print("Loaded item: " .. item.id)
    end
end
```

### Reading & Writing User Exports (`mod:Exports():ReadJson` / `WriteJson`)

Read and write JSON files to the sandboxed export directory (`<gameDir>/luatweaker/exports/<mod_id>/`):

```lua
local exports = mod:Exports()

local saveData = {
    playerLevel = 25,
    skills = {"fireball", "dash"},
    timestamp = os.time()
}

-- Write atomic, formatted JSON
exports:WriteJson("saves/player_data.json", saveData, true)

-- Read it back
if exports:Exists("saves/player_data.json") then
    local loaded = exports:ReadJson("saves/player_data.json")
    print("Player level: " .. loaded.playerLevel)
end
```

### Optional config without `Exists` + `pcall` (`ReadJsonOr`)

`ReadJson` throws on a missing file, invalid UTF-8 and malformed JSON. When the
file is genuinely optional (default config, player cache), use `ReadJsonOr`
instead of an `Exists` + `ReadJson` pair (which races: the file can vanish in
between):

```lua
local files = mod:Files()
local exports = mod:Exports()

-- Missing / non-UTF8 / malformed -> fallback, no throw, no pcall needed.
local cfg = files:ReadJsonOr("data/config.json", { difficulty = "normal" })
local cache = exports:ReadJsonOr("saves/cache.json", {})

-- Omitted fallback yields nil (same as an explicit nil).
local maybe = files:ReadJsonOr("data/optional.json")
```

The fallback covers **only** those three data cases. These still throw, by design:
path-security violations (`..`, absolute paths, escapes), directories, files over
the 8 MiB cap, and real I/O failures. Fail-loud beats fail-silent for programming
errors; the fallback is for absent/optional data, not for ignoring broken code.

---

## 4. Error Handling

Use `pcall` to catch malformed JSON gracefully:

```lua
local ok, result = pcall(function()
    return Json.Decode("{invalid_json:}")
end)

if not ok then
    log:warn("Failed to parse JSON: " .. tostring(result))
end
```
