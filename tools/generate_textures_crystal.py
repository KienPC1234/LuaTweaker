"""
Crystal Realm textures + block models generator (PIL).
Generates distinct 16x16 block textures, entity textures and the
blockstate/model JSONs wiring them, so every demo block has its own look.
"""
import math
import os
import random
import json

from PIL import Image, ImageDraw

random.seed(7)

BASE = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker"
TEX_BLOCK = os.path.join(BASE, "textures/block")
TEX_ENTITY = os.path.join(BASE, "textures/entity")
MODEL_BLOCK = os.path.join(BASE, "models/block")
MODEL_ITEM = os.path.join(BASE, "models/item")
STATE = os.path.join(BASE, "blockstates")
for d in (TEX_BLOCK, TEX_ENTITY, MODEL_BLOCK, MODEL_ITEM, STATE):
    os.makedirs(d, exist_ok=True)


def hash_noise(x, y, seed=0):
    h = (x * 374761393 + y * 668265263 + seed * 1442695040888963407) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return (h & 0xFFFF) / 65535.0


def crystal_texture(size, base, accent, noise_scale=2.2, facet=1.0):
    """Crystal-looking texture: base color + facet lines + sparkles."""
    img = Image.new("RGBA", (size, size), base + (255,))
    draw = ImageDraw.Draw(img)
    for y in range(size):
        for x in range(size):
            n = hash_noise(x, y, seed=3)
            shade = 0.75 + 0.5 * n
            r = min(255, int(base[0] * shade))
            g = min(255, int(base[1] * shade))
            b = min(255, int(base[2] * shade))
            img.putpixel((x, y), (r, g, b, 255))
    # facet lines (diagonal crystal edges)
    for i in range(-size, size, max(2, int(size / 6))):
        draw.line([(i, 0), (i + size, size)], fill=tuple(int(c * 1.25) for c in base) + (255,), width=1)
        draw.line([(i, size), (i + size, 0)], fill=tuple(int(c * 0.7) for c in base) + (255,), width=1)
    # sparkles
    for _ in range(max(4, size * size // 60)):
        sx, sy = random.randint(0, size - 1), random.randint(0, size - 1)
        img.putpixel((sx, sy), (255, 255, 255, 255))
    return img


def write_block(name, texture, model="cube_all"):
    img = crystal_texture(16, *texture)
    img.save(os.path.join(TEX_BLOCK, name + ".png"))
    parent = "minecraft:block/cube_all" if model == "cube_all" else "minecraft:block/cube_column"
    tex_key = "all" if model == "cube_all" else "side"
    model_json = {"parent": parent, "textures": {tex_key: f"luatweaker:block/{name}"}}
    if model == "cube_column":
        model_json["textures"]["end"] = f"luatweaker:block/{name}_top"
    with open(os.path.join(MODEL_BLOCK, name + ".json"), "w") as fh:
        json.dump(model_json, fh, indent=2)
    with open(os.path.join(MODEL_ITEM, name + ".json"), "w") as fh:
        json.dump({"parent": f"luatweaker:block/{name}"}, fh, indent=2)
    with open(os.path.join(STATE, name + ".json"), "w") as fh:
        json.dump({"variants": {"": {"model": f"luatweaker:block/{name}"}}}, fh, indent=2)
    print(f"block {name}")


# ---- blocks ---------------------------------------------------------------
write_block("crystal_stone", ((70, 130, 160), (160, 230, 255)))
write_block("crystal_grass", ((60, 140, 90), (180, 255, 200)))
write_block("crystal_dirt", ((90, 80, 110), (170, 150, 200)))
write_block("crystal_peak", ((150, 190, 235), (230, 250, 255)))
write_block("crystal_log", ((70, 160, 190), (150, 230, 255)))
write_block("crystal_leaves", ((80, 200, 170), (200, 255, 230)))
write_block("crystal_portal", ((150, 60, 220), (255, 130, 255)))
write_block("crystal_sky_portal", ((120, 180, 255), (230, 245, 255)))
# keep the legacy block its texture (already exists) but regenerate models that exist
write_block("crystal_block", ((110, 190, 235), (210, 245, 255)))
write_block("crystal_ore", ((80, 90, 120), (200, 220, 255)))
write_block("crystal_altar", ((100, 170, 220), (220, 240, 255)))


def entity_texture(name, size, base, accent, seed):
    img = Image.new("RGBA", (size, size), base + (255,))
    draw = ImageDraw.Draw(img)
    for y in range(size):
        for x in range(size):
            n = hash_noise(x, y, seed=seed)
            shade = 0.65 + 0.7 * n
            img.putpixel((x, y), (
                min(255, int(base[0] * shade)),
                min(255, int(base[1] * shade)),
                min(255, int(base[2] * shade)),
                255,
            ))
    # crystal facet grid so the box model looks crystalline
    step = max(4, size // 8)
    for i in range(0, size, step):
        draw.line([(i, 0), (i, size)], fill=tuple(int(c * 0.8) for c in base) + (255,), width=1)
        draw.line([(0, i), (size, i)], fill=tuple(int(c * 0.8) for c in base) + (255,), width=1)
    for _ in range(size * size // 80):
        sx, sy = random.randint(0, size - 1), random.randint(0, size - 1)
        img.putpixel((sx, sy), (255, 255, 255, 255))
    img.save(os.path.join(TEX_ENTITY, name + ".png"))
    print(f"entity {name}")


# ---- entities (crystal_golem is 128x128 for the iron_golem UV layout) -----
entity_texture("crystal_golem", 128, (90, 170, 220), (230, 250, 255), seed=11)
entity_texture("crystal_beast", 64, (70, 190, 160), (220, 255, 235), seed=13)
entity_texture("crystal_creeper", 64, (120, 220, 90), (240, 255, 200), seed=17)

print("done")
