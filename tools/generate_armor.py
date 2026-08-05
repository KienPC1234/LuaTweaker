"""
Generate crystal armor layer textures (64x32 standard armor texture format).
Layer 1: helmet + chestplate + boots. Layer 2: leggings.
"""
from PIL import Image
import os

OUT = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker/textures/models/armor"
os.makedirs(OUT, exist_ok=True)


def gen_armor_layer(filename, base_rgb, accent_rgb):
    """64x32 armor layer with crystal-blue plates and highlights."""
    img = Image.new('RGBA', (64, 32), (0, 0, 0, 0))
    br, bg, bb = base_rgb
    ar, ag, ab = accent_rgb

    # Body/arm plates (upper half of the 64x32 atlas covers torso/head/limbs).
    for y in range(0, 32):
        for x in range(0, 64):
            # Skip the transparent "gap" columns (standard armor atlas shape).
            if (x % 16 == 15) or (x % 16 == 0 and (y % 8 == 0 or y % 8 == 7)):
                continue
            img.putpixel((x, y), (br, bg, bb, 255))

    # Accent seams / crystal glints.
    for y in range(0, 32, 4):
        for x in range(0, 64, 8):
            img.putpixel((x, y), (ar, ag, ab, 255))

    # Bright highlights.
    for i in range(0, 32, 3):
        img.putpixel((3, i), (255, 255, 255, 200))
        img.putpixel((19, i), (255, 255, 255, 200))

    img.save(os.path.join(OUT, filename))
    print(f"  {filename}")


if __name__ == "__main__":
    print("Generating crystal armor layers...")
    gen_armor_layer("crystal_layer_1.png", (60, 140, 255), (150, 220, 255))
    gen_armor_layer("crystal_layer_2.png", (45, 110, 210), (120, 190, 255))
    print("Done!")
