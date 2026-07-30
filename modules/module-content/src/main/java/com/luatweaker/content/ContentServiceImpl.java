package com.luatweaker.content;

import com.luatweaker.api.content.*;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ContentServiceImpl implements IContentService {

    private final Map<String, IItemBuilder> items = new LinkedHashMap<>();
    private final Map<String, IBlockBuilder> blocks = new LinkedHashMap<>();
    private final Map<String, IFluidBuilder> fluids = new LinkedHashMap<>();

    @Override
    public IItemBuilder createItem(String id, Consumer<IItemBuilder> builderConsumer) {
        ItemBuilderImpl builder = new ItemBuilderImpl(id);
        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }
        items.put(id, builder);
        return builder;
    }

    @Override
    public IItemBuilder createSword(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("SWORD").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createPickaxe(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("PICKAXE").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createAxe(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("AXE").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createShovel(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("SHOVEL").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createHoe(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("HOE").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createHelmet(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("HELMET").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createChestplate(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("CHESTPLATE").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createLeggings(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("LEGGINGS").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createBoots(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("BOOTS").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override
    public IItemBuilder createRangedItem(String id, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, b -> {
            b.type("RANGED").maxStackSize(1);
            if (builderConsumer != null) builderConsumer.accept(b);
        });
    }

    @Override

    public IBlockBuilder createBlock(String id, Consumer<IBlockBuilder> builderConsumer) {
        BlockBuilderImpl builder = new BlockBuilderImpl(id);
        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }
        blocks.put(id, builder);
        return builder;
    }

    @Override
    public IFluidBuilder createFluid(String id, Consumer<IFluidBuilder> builderConsumer) {
        FluidBuilderImpl builder = new FluidBuilderImpl(id);
        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }
        fluids.put(id, builder);
        return builder;
    }

    private final Map<String, ICreativeTabBuilder> tabs = new LinkedHashMap<>();

    @Override
    public ICreativeTabBuilder createTab(String id, Consumer<ICreativeTabBuilder> builderConsumer) {
        CreativeTabBuilderImpl builder = new CreativeTabBuilderImpl(id);
        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }
        tabs.put(id, builder);
        return builder;
    }

    @Override
    public IBlockBuilder createStairs(String id, String baseBlockId) {
        return createBlock(id, b -> {
            b.model("minecraft:block/stairs");
        });
    }

    @Override
    public IBlockBuilder createSlab(String id, String baseBlockId) {
        return createBlock(id, b -> {
            b.model("minecraft:block/slab");
        });
    }

    @Override
    public IBlockBuilder createWall(String id, String baseBlockId) {
        return createBlock(id, b -> {
            b.model("minecraft:block/wall");
        });
    }


    private final Map<String, IArmorMaterialBuilder> armorMaterials = new LinkedHashMap<>();

    @Override
    public IArmorMaterialBuilder createArmorMaterial(String id, Consumer<IArmorMaterialBuilder> builderConsumer) {
        ArmorMaterialBuilderImpl builder = new ArmorMaterialBuilderImpl(id);
        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }
        armorMaterials.put(id, builder);
        return builder;
    }

    @Override
    public Collection<IItemBuilder> getRegisteredItems() {
        return Collections.unmodifiableCollection(items.values());
    }

    @Override
    public Collection<IBlockBuilder> getRegisteredBlocks() {
        return Collections.unmodifiableCollection(blocks.values());
    }

    @Override
    public Collection<IFluidBuilder> getRegisteredFluids() {
        return Collections.unmodifiableCollection(fluids.values());
    }

    @Override
    public Collection<ICreativeTabBuilder> getRegisteredTabs() {
        return Collections.unmodifiableCollection(tabs.values());
    }

    @Override
    public Collection<IArmorMaterialBuilder> getRegisteredArmorMaterials() {
        return Collections.unmodifiableCollection(armorMaterials.values());
    }

    @Override
    public IArmorMaterialBuilder getArmorMaterial(String id) {
        return armorMaterials.get(id);
    }
}


