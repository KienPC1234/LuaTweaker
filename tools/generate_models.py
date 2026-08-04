"""
Generate Minecraft item/block model JSON files for Arcane RPG
"""
import json
import os

MODELS_ITEM = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker/models/item"
MODELS_BLOCK = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker/models/block"
BLOCKSTATES = r"neoforge-platform/luamods/arcane_rpg/assets/luatweaker/blockstates"

os.makedirs(MODELS_ITEM, exist_ok=True)
os.makedirs(MODELS_BLOCK, exist_ok=True)
os.makedirs(BLOCKSTATES, exist_ok=True)


def gen_item_model(item_id, texture_path=None):
    """Generate item/generated model"""
    tex = texture_path or f"luatweaker:item/{item_id}"
    model = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": tex
        }
    }
    path = os.path.join(MODELS_ITEM, f"{item_id}.json")
    with open(path, 'w') as f:
        json.dump(model, f, indent=2)
    print(f"  models/item/{item_id}.json")


def gen_handheld_model(item_id, texture_path=None):
    """Generate item/handheld model (tools)"""
    tex = texture_path or f"luatweaker:item/{item_id}"
    model = {
        "parent": "minecraft:item/handheld",
        "textures": {
            "layer0": tex
        }
    }
    path = os.path.join(MODELS_ITEM, f"{item_id}.json")
    with open(path, 'w') as f:
        json.dump(model, f, indent=2)
    print(f"  models/item/{item_id}.json")


def gen_block_model(block_id):
    """Generate block cube_all model"""
    model = {
        "parent": "minecraft:block/cube_all",
        "textures": {
            "all": f"luatweaker:block/{block_id}"
        }
    }
    path = os.path.join(MODELS_BLOCK, f"{block_id}.json")
    with open(path, 'w') as f:
        json.dump(model, f, indent=2)
    print(f"  models/block/{block_id}.json")

    blockstate = {
        "variants": {
            "": {
                "model": f"luatweaker:block/{block_id}"
            }
        }
    }
    bs_path = os.path.join(BLOCKSTATES, f"{block_id}.json")
    with open(bs_path, 'w') as f:
        json.dump(blockstate, f, indent=2)
    print(f"  blockstates/{block_id}.json")

    block_item = {
        "parent": f"luatweaker:block/{block_id}"
    }
    bi_path = os.path.join(MODELS_ITEM, f"{block_id}.json")
    with open(bi_path, 'w') as f:
        json.dump(block_item, f, indent=2)
    print(f"  models/item/{block_id}.json (block item)")


if __name__ == "__main__":
    print("Generating model JSONs...")

    # Items
    gen_item_model("crystal_shard")
    gen_handheld_model("crystal_staff")
    gen_handheld_model("crystal_sword")
    gen_item_model("crystal_helmet")
    gen_item_model("crystal_chestplate")
    gen_item_model("crystal_leggings")
    gen_item_model("crystal_boots")
    gen_item_model("mana_potion")
    gen_item_model("spell_tome")

    # Blocks
    gen_block_model("crystal_ore")
    gen_block_model("crystal_block")
    gen_block_model("crystal_altar")

    print(f"\nDone! Generated models in:")
    print(f"  {MODELS_ITEM}")
    print(f"  {MODELS_BLOCK}")
    print(f"  {BLOCKSTATES}")
