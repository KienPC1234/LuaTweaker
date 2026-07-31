# In-Game Commands Guide (`/lt` & `/luatweaker`)

LuaTweaker provides in-game inspection, registry dumping, health diagnostics, and hot-reloading commands for modpack creators (Operator Permission Level 2).

---

## Command Reference Table

| Command | Category | Description |
| :--- | :--- | :--- |
| `/lt hand` | Inspection | Inspects main-hand item (ID, count, NBT). If hand is empty, inspects targeted block in crosshair and outputs copyable Lua code. |
| `/lt block` | Inspection | Detailed targeted block inspection (ID, state properties, light level, hardness, tile entity NBT). |
| `/lt tags` | Inspection | Dumps all item or block tags for hand or crosshair target (`/lt tags hand` \| `/lt tags block`). |
| `/lt inv` | Inspection | Dumps all items in player inventory with slot numbers and DSL snippets. |
| `/lt doctor` | Diagnostics | Scans scripts for syntax errors, unmapped IDs, dead recipe references, instruction counts, and script health. |
| `/lt dump mod <modid>` | Dumper | Dumps all items, blocks, and fluids of a specific mod into `lua/dumps/<modid>.txt`. |
| `/lt dump items [modid]` | Dumper | Dumps registered item IDs to `lua/dumps/items.txt`. |
| `/lt dump blocks [modid]` | Dumper | Dumps registered block IDs to `lua/dumps/blocks.txt`. |
| `/lt dump tags` | Dumper | Dumps item and block tags to `lua/dumps/tags.txt`. |
| `/lt log` | Diagnostics | Displays system memory usage, active custom content counts, and recipe metrics. |
| `/lt reload` | System | Real-time hot-reloader for scripts, custom content, recipes, tooltips, tags, and commands. |
| `/lt syntax` | Validation | Scans all stage files (`startup`, `server`, `client`) for compilation errors. |
| `/lt stubs` | Type Stubs | Re-generates EmmyLua IDE type definition stubs (`.luatweaker/stubs/luatweaker-api.lua`). |
| `/lt help` | Manual | Displays command help menu. |

---

## Doctor Diagnostics Output

Running `/lt doctor` performs an in-game health check:

```
=== LuaTweaker Script Doctor Diagnostics ===
[SUCCESS] Syntax Check: All stage scripts passed syntax validation!
[SUCCESS] Active Registrations: Items=2, Blocks=1, Recipes=15, Tooltips=4
[SUCCESS] Memory Health: System operating within normal RAM thresholds.
```
