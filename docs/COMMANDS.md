# 🛠️ In-Game Commands Guide (`/lt` & `/luatweaker`)

LuaTweaker provides a rich suite of in-game inspection, debug, registry dumping, health diagnostics, and hot-reloading commands for modpack creators (OP / Permission level 2).

---

## 📋 Command List

| Command | Action | Description |
| :--- | :--- | :--- |
| `/lt hand` | Item & Block Inspection | Inspects main-hand item (ID, count, NBT). If hand is empty, **automatically inspects the targeted block** in crosshair (CraftTweaker `/ct hand` style) and outputs click-to-copy Lua code! |
| `/lt block` | Detailed Block Target | Detailed targeted block inspection (ID, state properties, light level, hardness, tile entity NBT). |
| `/lt tags` | Tag Inspector | Dumps all item or block tags for hand or crosshair target (`/lt tags hand` \| `/lt tags block`). |
| `/lt inv` | Inventory Dump | Dumps all items in player inventory with slot numbers and DSL snippets. |
| `/lt doctor` | **Script Doctor** | Scans stage scripts for syntax errors, unmapped IDs, dead recipe references, instruction counts & health! |
| `/lt dump mod <modid>` | Mod Registry Dump | Dumps all items, blocks & fluids of a specific mod into `lua/dumps/<modid>.txt`. |
| `/lt dump items [modid]` | Item Registry Dump | Dumps registered item IDs to `lua/dumps/items.txt`. |
| `/lt dump blocks [modid]` | Block Registry Dump | Dumps registered block IDs to `lua/dumps/blocks.txt`. |
| `/lt dump tags` | Tag Registry Dump | Dumps item and block tags to `lua/dumps/tags.txt`. |
| `/lt log` | System Diagnostics | Displays RAM usage, active custom item/block counts, and recipe metrics. |
| `/lt reload` | **Hot-Reload** | Real-time hot-reloader for scripts, custom items, blocks, recipes, tooltips, tags, and commands. |
| `/lt syntax` | Syntax Validation | Scans all script stage files (`startup_scripts`, `server_scripts`, `client_scripts`) for syntax errors. |
| `/lt stubs` | Type Stub Refresh | Re-generates IDE type definition stubs (`lua/stubs/luatweaker.d.lua`). |
| `/lt help` | Help Menu | Displays formatted command help menu. |

---

## 🩺 Doctor Diagnostics Example

Running `/lt doctor` performs an in-game health check:
```
=== 🩺 LuaTweaker Script Doctor Diagnostics ===
✅ Syntax Check: All stage scripts passed syntax validation!
✅ Active Registrations: Items=2, Blocks=1, Recipes=15, Tooltips=4
✅ Memory Health: System operating within normal RAM thresholds.
```
