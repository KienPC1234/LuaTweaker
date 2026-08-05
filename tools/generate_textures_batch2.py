"""
Generate missing Arcane RPG textures - batch 2
"""
from PIL import Image, ImageDraw
import random
import math
import os

random.seed(42)

OUTPUT_ITEM = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker/textures/item"
OUTPUT_BLOCK = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker/textures/block"

os.makedirs(OUTPUT_ITEM, exist_ok=True)
os.makedirs(OUTPUT_BLOCK, exist_ok=True)


def noise2d(x, y, scale=10):
    ix, iy = int(x / scale), int(y / scale)
    fx, fy = (x / scale) - ix, (y / scale) - iy
    def hash_coord(x, y):
        h = (x * 374761393 + y * 668265263) & 0xFFFFFFFF
        h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
        return (h & 0xFFFF) / 65535.0
    v00 = hash_coord(ix, iy)
    v10 = hash_coord(ix + 1, iy)
    v01 = hash_coord(ix, iy + 1)
    v11 = hash_coord(ix + 1, iy + 1)
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    return (v00 * (1-fx) + v10 * fx) * (1-fy) + (v01 * (1-fx) + v11 * fx) * fy


def gen_armor_piece(name, base_color, accent_color, pattern="plate"):
    """Generate armor item texture"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    
    br, bg, bb = base_color
    ar, ag, ab = accent_color
    
    if pattern == "helmet":
        # Helmet shape
        for y in range(3, 14):
            for x in range(3, 13):
                dist = math.sqrt((x-8)**2 + (y-7)**2)
                if dist < 6:
                    n = noise2d(x, y, 3)
                    r = int(br * (0.7 + 0.3 * n))
                    g = int(bg * (0.7 + 0.3 * n))
                    b = int(bb * (0.7 + 0.3 * n))
                    img.putpixel((x, y), (r, g, b, 255))
        # Visor
        for x in range(5, 11):
            for y in range(7, 10):
                img.putpixel((x, y), (ar, ag, ab, 255))
    
    elif pattern == "chestplate":
        # Torso shape
        for y in range(2, 15):
            width = 5 if y < 5 else 6
            for x in range(8-width, 8+width):
                if 0 <= x < 16:
                    n = noise2d(x, y, 3)
                    r = int(br * (0.7 + 0.3 * n))
                    g = int(bg * (0.7 + 0.3 * n))
                    b = int(bb * (0.7 + 0.3 * n))
                    img.putpixel((x, y), (r, g, b, 255))
        # Center gem
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                img.putpixel((8+dx, 6+dy), (ar, ag, ab, 255))
    
    elif pattern == "leggings":
        # Two leg pieces
        for y in range(2, 15):
            for x in range(4, 7):
                n = noise2d(x, y, 3)
                r = int(br * (0.7 + 0.3 * n))
                g = int(bg * (0.7 + 0.3 * n))
                b = int(bb * (0.7 + 0.3 * n))
                img.putpixel((x, y), (r, g, b, 255))
            for x in range(9, 12):
                n = noise2d(x, y, 3)
                r = int(br * (0.7 + 0.3 * n))
                g = int(bg * (0.7 + 0.3 * n))
                b = int(bb * (0.7 + 0.3 * n))
                img.putpixel((x, y), (r, g, b, 255))
    
    elif pattern == "boots":
        # Boot shapes
        for y in range(8, 15):
            for x in range(2, 7):
                n = noise2d(x, y, 3)
                r = int(br * (0.7 + 0.3 * n))
                g = int(bg * (0.7 + 0.3 * n))
                b = int(bb * (0.7 + 0.3 * n))
                img.putpixel((x, y), (r, g, b, 255))
            for x in range(9, 14):
                n = noise2d(x, y, 3)
                r = int(br * (0.7 + 0.3 * n))
                g = int(bg * (0.7 + 0.3 * n))
                b = int(bb * (0.7 + 0.3 * n))
                img.putpixel((x, y), (r, g, b, 255))
    
    # Crystal accents
    for _ in range(3):
        px = random.randint(4, 12)
        py = random.randint(4, 12)
        img.putpixel((px, py), (255, 255, 255, 200))
    
    img.save(os.path.join(OUTPUT_ITEM, f"{name}.png"))
    print(f"  {name}.png")


def gen_sword():
    """Crystal sword"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    
    # Handle (brown)
    for i in range(6):
        x, y = 3 + i, 13 - i
        for dx in range(-1, 2):
            if 0 <= x+dx < 16:
                n = noise2d(x+dx, y, 2)
                img.putpixel((x+dx, y), (int(120+30*n), int(80+20*n), int(40+10*n), 255))
    
    # Guard (gold)
    for x in range(5, 11):
        img.putpixel((x, 9), (200, 180, 50, 255))
    
    # Blade (crystal blue, diagonal)
    for i in range(8):
        x, y = 9 + i//2, 8 - i
        if 0 <= x < 16 and 0 <= y < 16:
            n = noise2d(x, y, 2)
            r = int(60 * (0.7 + 0.3*n))
            g = int(180 * (0.7 + 0.3*n))
            b = int(255 * (0.7 + 0.3*n))
            img.putpixel((x, y), (r, g, b, 255))
            if x+1 < 16:
                img.putpixel((x+1, y), (r//2, g//2, b, 200))
    
    # Tip sparkle
    img.putpixel((13, 1), (255, 255, 255, 255))
    
    img.save(os.path.join(OUTPUT_ITEM, "crystal_sword.png"))
    print("  crystal_sword.png")


def gen_spell_tome():
    """Spell tome book"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    
    # Book cover (dark purple)
    for y in range(3, 14):
        for x in range(3, 13):
            n = noise2d(x, y, 4)
            r = int(60 * (0.7 + 0.3*n))
            g = int(20 * (0.7 + 0.3*n))
            b = int(100 * (0.7 + 0.3*n))
            img.putpixel((x, y), (r, g, b, 255))
    
    # Pages (white edge)
    for y in range(4, 13):
        img.putpixel((12, y), (220, 220, 200, 255))
    
    # Crystal gem on cover
    for dy in range(-1, 2):
        for dx in range(-1, 2):
            img.putpixel((8+dx, 8+dy), (100, 200, 255, 255))
    
    # Glow effect
    img.putpixel((8, 8), (255, 255, 255, 255))
    
    img.save(os.path.join(OUTPUT_ITEM, "spell_tome.png"))
    print("  spell_tome.png")


def gen_crystal_altar():
    """Crystal altar block"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    
    # Base stone
    for y in range(10, 16):
        for x in range(0, 16):
            n = noise2d(x, y, 3)
            base = int(80 + 30 * n)
            img.putpixel((x, y), (base, base-5, base-10, 255))
    
    # Pillar
    for y in range(4, 12):
        for x in range(5, 11):
            n = noise2d(x, y, 3)
            r = int(100 * (0.7 + 0.3*n))
            g = int(100 * (0.7 + 0.3*n))
            b = int(120 * (0.7 + 0.3*n))
            img.putpixel((x, y), (r, g, b, 255))
    
    # Crystal on top
    for y in range(1, 5):
        for x in range(6, 10):
            n = noise2d(x, y, 2)
            r = int(60 * (0.7 + 0.3*n))
            g = int(180 * (0.7 + 0.3*n))
            b = int(255 * (0.7 + 0.3*n))
            img.putpixel((x, y), (r, g, b, 255))
    
    # Glow
    img.putpixel((8, 2), (255, 255, 255, 255))
    
    img.save(os.path.join(OUTPUT_BLOCK, "crystal_altar.png"))
    print("  crystal_altar.png")


if __name__ == "__main__":
    print("Generating Arcane RPG textures - batch 2...")
    gen_armor_piece("crystal_helmet", (60, 140, 255), (100, 200, 255), "helmet")
    gen_armor_piece("crystal_chestplate", (60, 140, 255), (100, 200, 255), "chestplate")
    gen_armor_piece("crystal_leggings", (60, 140, 255), (100, 200, 255), "leggings")
    gen_armor_piece("crystal_boots", (60, 140, 255), (100, 200, 255), "boots")
    gen_sword()
    gen_spell_tome()
    gen_crystal_altar()
    print("Done!")
