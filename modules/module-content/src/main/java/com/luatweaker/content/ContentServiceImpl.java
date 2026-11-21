package com.luatweaker.content;

import com.luatweaker.api.content.IArmorMaterialBuilder;
import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.ICreativeTabBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.content.IItemBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ContentServiceImpl implements IContentService {

    /** Tool/armor presets are unstackable; a Lua callback may still override this. */
    private static final int SINGLE_ITEM_STACK = 1;

    private final Map<String, IItemBuilder> items = new ConcurrentHashMap<>();
    private final Map<String, IBlockBuilder> blocks = new ConcurrentHashMap<>();
    private final Map<String, IFluidBuilder> fluids = new ConcurrentHashMap<>();
    private final Map<String, ICreativeTabBuilder> tabs = new ConcurrentHashMap<>();
    private final Map<String, IArmorMaterialBuilder> armorMaterials = new ConcurrentHashMap<>();
    private final Map<String, IEntityBuilder> entities = new ConcurrentHashMap<>();

    @Override
    public IItemBuilder createItem(String id, Consumer<IItemBuilder> builderConsumer) {
        return register(items, id, new ItemBuilderImpl(id), builderConsumer);
    }

    @Override
    public IItemBuilder createSword(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "SWORD", builderConsumer);
    }

    @Override
    public IItemBuilder createPickaxe(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "PICKAXE", builderConsumer);
    }

    @Override
    public IItemBuilder createAxe(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "AXE", builderConsumer);
    }

    @Override
    public IItemBuilder createShovel(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "SHOVEL", builderConsumer);
    }

    @Override
    public IItemBuilder createHoe(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "HOE", builderConsumer);
    }

    @Override
    public IItemBuilder createHelmet(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "HELMET", builderConsumer);
    }

    @Override
    public IItemBuilder createChestplate(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "CHESTPLATE", builderConsumer);
    }

    @Override
    public IItemBuilder createLeggings(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "LEGGINGS", builderConsumer);
    }

    @Override
    public IItemBuilder createBoots(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "BOOTS", builderConsumer);
    }

    @Override
    public IItemBuilder createRangedItem(String id, Consumer<IItemBuilder> builderConsumer) {
        return createTypedItem(id, "RANGED", builderConsumer);
    }

    /**
     * Shared body of the tool/armor presets: seeds the DSL type before the Lua
     * callback runs, so a script can still override type or stack size.
     */
    private IItemBuilder createTypedItem(String id, String itemType, Consumer<IItemBuilder> builderConsumer) {
        return createItem(id, builder -> {
            builder.type(itemType).maxStackSize(SINGLE_ITEM_STACK);
            if (builderConsumer != null) {
                builderConsumer.accept(builder);
            }
        });
    }

    @Override
    public IBlockBuilder createBlock(String id, Consumer<IBlockBuilder> builderConsumer) {
        return register(blocks, id, new BlockBuilderImpl(id), builderConsumer);
    }

    @Override
    public IFluidBuilder createFluid(String id, Consumer<IFluidBuilder> builderConsumer) {
        return register(fluids, id, new FluidBuilderImpl(id), builderConsumer);
    }

    @Override
    public ICreativeTabBuilder createTab(String id, Consumer<ICreativeTabBuilder> builderConsumer) {
        return register(tabs, id, new CreativeTabBuilderImpl(id), builderConsumer);
    }

    @Override
    public IBlockBuilder createStairs(String id, String baseBlockId) {
        return createBlock(id, b -> b.shape("STAIRS").baseBlock(baseBlockId));
    }

    @Override
    public IBlockBuilder createSlab(String id, String baseBlockId) {
        return createBlock(id, b -> b.shape("SLAB").baseBlock(baseBlockId));
    }

    @Override
    public IBlockBuilder createWall(String id, String baseBlockId) {
        return createBlock(id, b -> b.shape("WALL").baseBlock(baseBlockId));
    }

    @Override
    public IArmorMaterialBuilder createArmorMaterial(String id, Consumer<IArmorMaterialBuilder> builderConsumer) {
        return register(armorMaterials, id, new ArmorMaterialBuilderImpl(id), builderConsumer);
    }

    @Override
    public IEntityBuilder createEntity(String id, Consumer<IEntityBuilder> builderConsumer) {
        return register(entities, id, new EntityBuilderImpl(id), builderConsumer);
    }

    /** Applies the optional Lua builder callback first, then records the builder. */
    private static <B> B register(Map<String, B> registry, String id, B builder, Consumer<B> builderConsumer) {
        if (builderConsumer != null) {
            builderConsumer.accept(builder);
        }
        registry.put(id, builder);
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
    public Collection<IEntityBuilder> getRegisteredEntities() {
        return Collections.unmodifiableCollection(entities.values());
    }

    @Override
    public IArmorMaterialBuilder getArmorMaterial(String id) {
        return armorMaterials.get(id);
    }
}
