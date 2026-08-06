#!/usr/bin/env python3
"""Regenerate the LuaTweaker container GUI textures.

Layout contract (matches ContainerLayout, the vanilla chest geometry):
  - crate_panel.png : 256x256 sheet, frame drawn at (0,0,176,222). Only the
    frame is used: top strip (0..16), 1px side borders (x=0 and x=175),
    bottom strip (213..221). The screen stretches the side borders so any
    row count 1..6 renders a complete frame.
  - slot.png : 18x18 default slot cell (1px border + dark fill).
  - wood_crate_custom.png / wood_crate_slot.png : themed sample GUI for the
    ruby_mod demo (176x186 panel = 4-row container) + wood cell.

Run:  python tools/generate_gui_textures.py
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLATFORM_GUI = os.path.join(ROOT, "neoforge-platform", "src", "main", "resources",
                            "assets", "luatweaker", "textures", "gui")
PLATFORM_ITEM = os.path.join(ROOT, "neoforge-platform", "src", "main", "resources",
                             "assets", "luatweaker", "textures", "item")
RUBY_GUI = os.path.join(ROOT, "neoforge-platform", "luamods", "ruby_mod",
                        "assets", "luatweaker", "textures", "gui")
RUBY_BLOCK = os.path.join(ROOT, "neoforge-platform", "luamods", "ruby_mod",
                          "assets", "luatweaker", "textures", "block")


def flat_cell(draw, x0, y0, border, fill):
    """1px bordered 18x18 cell at (x0, y0)."""
    draw.rectangle((x0, y0, x0 + 17, y0 + 17), fill=border)
    draw.rectangle((x0 + 1, y0 + 1, x0 + 16, y0 + 16), fill=fill)


def make_panel(path, width, height, interior, top_left, bottom_right,
               plank_line=None):
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    # interior
    draw.rectangle((1, 1, width - 2, height - 2), fill=interior)
    # vanilla-style bevel border
    draw.line((0, 0, width - 1, 0), fill=top_left, width=1)
    draw.line((0, 0, 0, height - 1), fill=top_left, width=1)
    draw.line((width - 1, 0, width - 1, height - 1), fill=bottom_right, width=1)
    draw.line((0, height - 1, width - 1, height - 1), fill=bottom_right, width=1)
    if plank_line is not None:
        for py in range(1, height - 1, 8):
            draw.line((1, py, width - 2, py), fill=plank_line)
    img.save(path)
    print("wrote", os.path.relpath(path, ROOT), f"({img.size[0]}x{img.size[1]})")


def main():
    os.makedirs(PLATFORM_GUI, exist_ok=True)
    os.makedirs(PLATFORM_ITEM, exist_ok=True)
    os.makedirs(RUBY_GUI, exist_ok=True)

    # Default crate panel: 176x222 frame (fits rows 1..6).
    make_panel(os.path.join(PLATFORM_GUI, "crate_panel.png"),
               176, 222, (139, 139, 139, 255), (198, 198, 198, 255), (63, 63, 63, 255))

    # Default slot cell: 18x18 (the old 16x16 png was blitted as 18x18 -> UV overflow).
    cell = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    flat_cell(ImageDraw.Draw(cell), 0, 0, (198, 198, 198, 255), (63, 63, 63, 255))
    cell.save(os.path.join(PLATFORM_GUI, "slot.png"))
    print("wrote", os.path.relpath(os.path.join(PLATFORM_GUI, "slot.png"), ROOT), "(18x18)")

    # Wood crate custom GUI: 176x186 panel (4 rows) for the ruby_mod demo.
    make_panel(os.path.join(RUBY_GUI, "wood_crate_custom.png"),
               176, 186, (160, 110, 60, 255), (110, 70, 25, 255), (60, 30, 10, 255),
               plank_line=(140, 95, 50, 255))

    # Wood crate slot cell.
    cell = Image.new("RGBA", (18, 18), (0, 0, 0, 0))
    flat_cell(ImageDraw.Draw(cell), 0, 0, (110, 70, 25, 255), (55, 30, 12, 255))
    cell.save(os.path.join(RUBY_GUI, "wood_crate_slot.png"))
    print("wrote", os.path.relpath(os.path.join(RUBY_GUI, "wood_crate_slot.png"), ROOT), "(18x18)")

    machine_demo_textures()


def machine_demo_textures():
    """16x16 placeholder block textures for the ruby_charger machine demo
    (user replaces them with real art): off, running (glowing), and pipe."""
    os.makedirs(RUBY_BLOCK, exist_ok=True)
    machine_off = Image.new("RGBA", (16, 16), (52, 52, 58, 255))
    draw = ImageDraw.Draw(machine_off)
    draw.rectangle((1, 1, 14, 14), fill=(66, 66, 74, 255), outline=(30, 30, 34, 255))
    draw.rectangle((4, 6, 11, 9), fill=(90, 40, 45, 255))
    draw.rectangle((4, 6, 8, 9), fill=(140, 45, 55, 255))
    machine_off.save(os.path.join(RUBY_BLOCK, "ruby_charger.png"))
    print("wrote", os.path.relpath(os.path.join(RUBY_BLOCK, "ruby_charger.png"), ROOT), "(16x16)")

    machine_on = Image.new("RGBA", (16, 16), (52, 52, 58, 255))
    draw = ImageDraw.Draw(machine_on)
    draw.rectangle((1, 1, 14, 14), fill=(70, 70, 78, 255), outline=(30, 30, 34, 255))
    draw.rectangle((4, 6, 11, 9), fill=(150, 60, 60, 255))
    draw.rectangle((4, 6, 8, 9), fill=(255, 90, 95, 255))
    draw.line((4, 3, 11, 3), fill=(255, 150, 100, 255))
    machine_on.save(os.path.join(RUBY_BLOCK, "ruby_charger_running.png"))
    print("wrote", os.path.relpath(os.path.join(RUBY_BLOCK, "ruby_charger_running.png"), ROOT), "(16x16)")

    pipe = Image.new("RGBA", (16, 16), (40, 40, 46, 255))
    draw = ImageDraw.Draw(pipe)
    draw.rectangle((4, 4, 11, 11), fill=(120, 45, 55, 255))
    draw.rectangle((6, 6, 9, 9), fill=(200, 70, 80, 255))
    draw.line((4, 7, 11, 7), fill=(70, 70, 78, 255))
    draw.line((4, 8, 11, 8), fill=(70, 70, 78, 255))
    pipe.save(os.path.join(RUBY_BLOCK, "ruby_pipe.png"))
    print("wrote", os.path.relpath(os.path.join(RUBY_BLOCK, "ruby_pipe.png"), ROOT), "(16x16)")

    wrench = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(wrench)
    # wrench head
    draw.polygon([(1, 6), (3, 4), (6, 7), (6, 10), (3, 13), (1, 11), (4, 8)], fill=(160, 165, 175, 255))
    draw.polygon([(1, 6), (3, 4), (6, 7), (6, 10), (3, 13), (1, 11), (4, 8)], outline=(60, 65, 75, 255))
    # handle
    draw.rectangle((6, 6, 8, 15), fill=(150, 140, 120, 255))
    draw.rectangle((6, 6, 8, 15), outline=(60, 50, 40, 255))
    draw.rectangle((8, 10, 13, 12), fill=(150, 140, 120, 255), outline=(60, 50, 40, 255))
    wrench.save(os.path.join(PLATFORM_ITEM, "wrench.png"))
    print("wrote", os.path.relpath(os.path.join(PLATFORM_ITEM, "wrench.png"), ROOT), "(16x16)")


if __name__ == "__main__":
    main()
