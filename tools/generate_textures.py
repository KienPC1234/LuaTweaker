"""
Arcane RPG Texture Generator
Generates procedural textures for crystal items/blocks using Python + Pillow
"""

from PIL import Image, ImageDraw
import random
import math
import os

random.seed(42)  # Reproducible

OUTPUT_ITEM = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker/textures/item"
OUTPUT_BLOCK = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker/textures/block"

os.makedirs(OUTPUT_ITEM, exist_ok=True)
os.makedirs(OUTPUT_BLOCK, exist_ok=True)


def noise2d(x, y, scale=10):
    """Simple value noise"""
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


def generate_crystal_shard():
    """16x16 crystal shard item texture"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    
    # Crystal body - elongated hexagonal shape
    points = [
        (8, 1), (11, 4), (11, 11), (8, 14), (5, 11), (5, 4)
    ]
    
    # Fill with gradient blues
    for y in range(16):
        for x in range(16):
            # Check if inside crystal shape
            nx, ny = (x - 8) / 4, (y - 8) / 7
            if abs(nx) + abs(ny) < 1.0:
                # Gradient + sparkle
                n = noise2d(x, y, 4)
                brightness = 0.5 + 0.5 * n
                r = int(60 * brightness)
                g = int(140 * brightness + 60)
                b = int(255 * brightness)
                
                # Sparkle
                if random.random() < 0.05:
                    r, g, b = 255, 255, 255
                
                img.putpixel((x, y), (r, g, b, 255))
    
    # Crystal edges - bright highlight
    for i in range(len(points)):
        p1 = points[i]
        p2 = points[(i + 1) % len(points)]
        draw.line([p1, p2], fill=(180, 220, 255, 255), width=1)
    
    img.save(os.path.join(OUTPUT_ITEM, "crystal_shard.png"))
    print("  crystal_shard.png")


def generate_crystal_ore():
    """16x16 crystal ore block texture"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    
    # Stone base
    for y in range(16):
        for x in range(16):
            n = noise2d(x, y, 3) * 0.3 + noise2d(x, y, 8) * 0.7
            base = int(100 + 40 * n)
            r, g, b = base, base - 5, base - 10
            img.putpixel((x, y), (r, g, b, 255))
    
    # Crystal deposits
    crystal_positions = [(3, 4), (10, 3), (7, 9), (12, 11), (4, 12)]
    
    for cx, cy in crystal_positions:
        size = random.randint(2, 3)
        for dy in range(-size, size + 1):
            for dx in range(-size, size + 1):
                px, py = cx + dx, cy + dy
                if 0 <= px < 16 and 0 <= py < 16:
                    dist = math.sqrt(dx*dx + dy*dy)
                    if dist <= size:
                        n = noise2d(px, py, 2)
                        brightness = 0.7 + 0.3 * n
                        r = int(40 * brightness)
                        g = int(160 * brightness + 40)
                        b = int(255 * brightness)
                        img.putpixel((px, py), (r, g, b, 255))
    
    img.save(os.path.join(OUTPUT_BLOCK, "crystal_ore.png"))
    print("  crystal_ore.png")


def generate_crystal_block():
    """16x16 pure crystal block"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    
    for y in range(16):
        for x in range(16):
            n = noise2d(x, y, 5)
            
            # Grid pattern for faceted look
            grid_x = (x % 4) / 4.0
            grid_y = (y % 4) / 4.0
            edge = min(grid_x, 1-grid_x, grid_y, 1-grid_y)
            edge_factor = 1.0 if edge > 0.1 else 0.7
            
            brightness = (0.6 + 0.4 * n) * edge_factor
            
            r = int(60 * brightness)
            g = int(180 * brightness + 20)
            b = int(255 * brightness)
            
            # Highlight
            if edge < 0.05:
                r, g, b = min(255, r + 80), min(255, g + 40), 255
            
            img.putpixel((x, y), (r, g, b, 255))
    
    img.save(os.path.join(OUTPUT_BLOCK, "crystal_block.png"))
    print("  crystal_block.png")


def generate_crystal_staff():
    """16x16 magic staff item"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    
    # Staff shaft (diagonal brown)
    for i in range(16):
        x = i
        y = 15 - i
        for dx in range(-1, 2):
            for dy in range(-1, 2):
                px, py = x + dx, y + dy
                if 0 <= px < 16 and 0 <= py < 16:
                    n = noise2d(px, py, 3)
                    r = int(120 + 30 * n)
                    g = int(80 + 20 * n)
                    b = int(40 + 10 * n)
                    img.putpixel((px, py), (r, g, b, 255))
    
    # Crystal tip (top-right)
    for dy in range(-3, 4):
        for dx in range(-3, 4):
            px, py = 12 + dx, 3 + dy
            if 0 <= px < 16 and 0 <= py < 16:
                dist = math.sqrt(dx*dx + dy*dy)
                if dist <= 3:
                    n = noise2d(px, py, 2)
                    brightness = 0.7 + 0.3 * n
                    r = int(80 * brightness)
                    g = int(200 * brightness)
                    b = int(255 * brightness)
                    img.putpixel((px, py), (r, g, b, 255))
    
    # Crystal glow
    for dy in range(-4, 5):
        for dx in range(-4, 5):
            px, py = 12 + dx, 3 + dy
            if 0 <= px < 16 and 0 <= py < 16:
                r, g, b, a = img.getpixel((px, py))
                if a == 0:
                    dist = math.sqrt(dx*dx + dy*dy)
                    if dist <= 4:
                        glow = int(60 * (1 - dist/4))
                        img.putpixel((px, py), (glow, glow * 2, glow * 3, glow))
    
    img.save(os.path.join(OUTPUT_ITEM, "crystal_staff.png"))
    print("  crystal_staff.png")


def generate_mana_potion():
    """16x16 mana potion"""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    
    # Bottle shape
    bottle_pixels = []
    for y in range(16):
        for x in range(16):
            # Bottle body (rounded rect)
            in_body = (4 <= x <= 11) and (5 <= y <= 14)
            # Neck
            in_neck = (6 <= x <= 9) and (2 <= y <= 5)
            # Cork
            in_cork = (7 <= x <= 8) and (1 <= y <= 2)
            
            if in_body or in_neck:
                bottle_pixels.append((x, y))
    
    # Fill bottle with blue liquid
    for x, y in bottle_pixels:
        n = noise2d(x, y, 3)
        
        # Liquid gradient (darker at bottom)
        liquid_level = (14 - y) / 10.0
        
        r = int(20 + 30 * n)
        g = int(60 + 80 * n)
        b = int(180 + 75 * n)
        
        # Bubbles
        if random.random() < 0.03:
            r, g, b = 200, 220, 255
        
        img.putpixel((x, y), (r, g, b, 255))
    
    # Cork
    for y in range(1, 3):
        for x in range(7, 9):
            img.putpixel((x, y), (140, 100, 60, 255))
    
    # Glass highlight
    for y in range(6, 13):
        img.putpixel((5, y), (220, 240, 255, 200))
    
    img.save(os.path.join(OUTPUT_ITEM, "mana_potion.png"))
    print("  mana_potion.png")


if __name__ == "__main__":
    print("Generating Arcane RPG textures...")
    generate_crystal_shard()
    generate_crystal_ore()
    generate_crystal_block()
    generate_crystal_staff()
    generate_mana_potion()
    print(f"\nDone! Generated 5 textures in:")
    print(f"  {OUTPUT_ITEM}")
    print(f"  {OUTPUT_BLOCK}")
