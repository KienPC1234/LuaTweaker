#!/usr/bin/env python3
"""Generate NBT structure templates for the arcane_rpg demo mod.

Creates <name>.nbt files in neoforge-platform/luamods/arcane_rpg/data/arcane_rpg/structures/
using the vanilla StructureTemplate NBT format (nbtlib). The templates are loaded
at runtime by World:PlaceStructure("arcane_rpg:<name>", ...) from the virtual datapack.

Usage: python tools/generate_structures.py
"""

import os
from pathlib import Path

import nbtlib

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "neoforge-platform" / "luamods" / "arcane_rpg" / "data" / "arcane_rpg" / "structures"


def block_entry(state: str) -> dict:
    """Vanilla palette entry: {"Name": "minecraft:stone", "Properties": {...}}."""
    if "[" in state:
        name, props = state.split("[", 1)
        props = props.rstrip("]")
        properties = {}
        for part in props.split(","):
            k, v = part.split("=", 1)
            properties[k] = v
        return {"Name": name, "Properties": properties}
    return {"Name": state}


def set_block(builder: dict, x: int, y: int, z: int, state: str) -> None:
    """Set one block in a {size, palette, blocks} StructureTemplate builder."""
    palette = builder["palette"]
    blocks = builder["blocks"]
    index = builder.setdefault("_index", {})
    if state not in index:
        index[state] = len(palette)
        palette.append(block_entry(state))
    blocks.append({
        "pos": [x, y, z],
        "state": index[state],
        "nbt": nbtlib.Compound()
    })


def fill(builder: dict, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, state: str) -> None:
    for x in range(min(x1, x2), max(x1, x2) + 1):
        for y in range(min(y1, y2), max(y1, y2) + 1):
            for z in range(min(z1, z2), max(z1, z2) + 1):
                set_block(builder, x, y, z, state)


def build(builder: dict) -> nbtlib.Compound:
    """Converts the builder dict into a valid StructureTemplate NBT compound."""
    palette = []
    for entry in builder["palette"]:
        compound = nbtlib.Compound()
        compound["Name"] = nbtlib.String(entry["Name"])
        if "Properties" in entry:
            props = nbtlib.Compound()
            for k, v in entry["Properties"].items():
                props[k] = nbtlib.String(v)
            compound["Properties"] = props
        palette.append(compound)
    blocks = []
    for b in builder["blocks"]:
        block = nbtlib.Compound({
            "pos": nbtlib.List([nbtlib.Int(x) for x in b["pos"]]),
            "state": nbtlib.Int(b["state"]),
        })
        if "nbt" in b and b["nbt"] is not None:
            block["nbt"] = b["nbt"]
        blocks.append(block)
    return nbtlib.Compound({
        "size": nbtlib.List([nbtlib.Int(x) for x in builder["size"]]),
        "palette": nbtlib.List(palette),
        "blocks": nbtlib.List(blocks),
    })


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)

    # ---- sky_monolith.nbt: a tall floating crystal pillar (7x24x7) ----
    mono = {"size": [7, 24, 7], "palette": [], "blocks": []}
    for y in range(0, 24):
        r = 1 + (y % 3)
        for x in range(-r, r + 1):
            for z in range(-r, r + 1):
                if abs(x) == r and abs(z) == r and y % 2 == 0:
                    continue  # chamfered edges
                if y >= 20 and (abs(x) > 1 or abs(z) > 1):
                    continue  # tapered tip
                set_block(mono, x + 3, y, z + 3, "luatweaker:crystal_peak")
    set_block(mono, 3, 23, 3, "minecraft:glowstone")  # tip beacon
    for x in range(7):
        for z in range(7):
            set_block(mono, x, 0, z, "luatweaker:crystal_grass")
    nbtlib.File(build(mono), gzipped=True).save(OUT / "sky_monolith.nbt")

    # ---- crystal_outpost.nbt: a small guard house (9x8x9) ----
    op = {"size": [9, 8, 9], "palette": [], "blocks": []}
    fill(op, 0, 0, 0, 8, 0, 8, "luatweaker:crystal_block")  # floor
    fill(op, 0, 1, 0, 0, 4, 8, "luatweaker:crystal_block")  # walls
    fill(op, 8, 1, 0, 8, 4, 8, "luatweaker:crystal_block")
    fill(op, 1, 1, 0, 7, 4, 0, "luatweaker:crystal_block")
    fill(op, 1, 1, 8, 7, 4, 8, "luatweaker:crystal_block")
    # Doorway (front, +z): 1x3 opening at x=4
    for y in range(1, 4):
        set_block(op, 4, y, 8, "minecraft:air")
    # Windows: 1x1 at x=2, z=0 and x=6, z=0
    set_block(op, 2, 3, 0, "minecraft:air")
    set_block(op, 6, 3, 0, "minecraft:air")
    # Roof cap
    fill(op, 0, 5, 0, 8, 5, 8, "luatweaker:crystal_peak")
    fill(op, 1, 6, 1, 7, 6, 7, "luatweaker:crystal_peak")
    fill(op, 2, 7, 2, 6, 7, 6, "luatweaker:crystal_peak")
    set_block(op, 4, 7, 4, "minecraft:glowstone")  # roof light
    set_block(op, 4, 1, 4, "luatweaker:crystal_altar")  # interior altar
    nbtlib.File(build(op), gzipped=True).save(OUT / "crystal_outpost.nbt")

    print(f"Structures written to {OUT}")
    for f in sorted(OUT.glob("*.nbt")):
        print(f"  {f.name} ({f.stat().st_size} bytes)")


if __name__ == "__main__":
    main()
