package com.luatweaker.platform.content;

import com.luatweaker.api.content.*;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.registries.RegisterEvent;

import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NeoForgeContentRegistry {


    private final IContentService contentService;
    private final Set<ResourceLocation> createdCustomTabs = new HashSet<>();
    private final java.util.Map<ResourceLocation, Block> createdBlocks = new java.util.LinkedHashMap<>();
    private final java.util.Map<ResourceLocation, Item> createdItems = new java.util.LinkedHashMap<>();

    public NeoForgeContentRegistry(IContentService contentService) {
        this.contentService = contentService;
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new GameBusListener());
    }


    private class GameBusListener {
        @SubscribeEvent
        public void onProjectileImpact(ProjectileImpactEvent event) {
            if (event.getRayTraceResult() instanceof EntityHitResult hitResult) {
                Projectile proj = event.getProjectile();
                for (IItemBuilder b : contentService.getRegisteredItems()) {
                    String tagCheck = "lt_proj_" + b.getId();
                    if (proj.getTags().contains(tagCheck)) {
                        if (b.getOnHitEntityHandler() != null && proj.getOwner() instanceof Player player) {
                            try {
                                b.getOnHitEntityHandler().accept(
                                        new com.luatweaker.platform.entity.NeoForgeEntityWrapper(hitResult.getEntity()),
                                        new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player)
                                );
                            } catch (Exception e) {
                                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed projectile onHitEntity handler for " + b.getId() + ": " + e.getMessage());
                            }
                        }
                        break;
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onRegister(RegisterEvent event) {
        if (event.getRegistryKey().equals(Registries.ITEM)) {
            registerItems(event);
            registerBlockItems(event);
            registerFluidItems(event);
        } else if (event.getRegistryKey().equals(Registries.BLOCK)) {
            registerBlocks(event);
            registerFluidBlocks(event);
        } else if (event.getRegistryKey().equals(Registries.FLUID)) {
            registerFluids(event);
        } else if (event.getRegistryKey().equals(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES)) {
            registerFluidTypes(event);
        } else if (event.getRegistryKey().equals(Registries.CREATIVE_MODE_TAB)) {
            registerCreativeTab(event);
        }
    }

    private void registerFluidTypes(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            try {
                ResourceLocation rl = parseLocation(builder.getId());
                ResourceLocation stillTex = builder.getStillTexture() != null
                        ? parseLocation(builder.getStillTexture())
                        : ResourceLocation.fromNamespaceAndPath("luatweaker", "block/" + builder.getId() + "_still");
                ResourceLocation flowTex = builder.getFlowingTexture() != null
                        ? parseLocation(builder.getFlowingTexture())
                        : ResourceLocation.fromNamespaceAndPath("luatweaker", "block/" + builder.getId() + "_flow");

                net.neoforged.neoforge.fluids.FluidType.Properties props = net.neoforged.neoforge.fluids.FluidType.Properties.create()
                        .descriptionId("fluid.luatweaker." + builder.getId())
                        .temperature(builder.getTemperature())
                        .viscosity(builder.getViscosity());

                net.neoforged.neoforge.fluids.FluidType type = new net.neoforged.neoforge.fluids.FluidType(props) {
                    @Override
                    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions> consumer) {
                        consumer.accept(new net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions() {
                            @Override
                            public ResourceLocation getStillTexture() { return stillTex; }
                            @Override
                            public ResourceLocation getFlowingTexture() { return flowTex; }
                            @Override
                            public int getTintColor() { return builder.getColor() | 0xFF000000; }
                        });
                    }
                };

                event.register(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES, rl, () -> type);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom FluidType: " + rl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register fluid type " + builder.getId() + ": " + e.getMessage());
            }
        }
    }



    private void registerBlockItems(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            ResourceLocation rl = parseLocation(builder.getId());
            Block block = createdBlocks.get(rl);
            if (block == null) {
                block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(rl);
            }
            if (block != null && block != net.minecraft.world.level.block.Blocks.AIR) {
                Block finalBlock = block;
                Item blockItem = new net.minecraft.world.item.BlockItem(finalBlock, new Item.Properties());
                createdItems.put(rl, blockItem);
                event.register(Registries.ITEM, rl, () -> blockItem);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BlockItem to NeoForge: " + rl);
            }
        }
    }



    private void registerCreativeTab(RegisterEvent event) {
        createdCustomTabs.clear();
        var userTabs = contentService.getRegisteredTabs();
        if (!userTabs.isEmpty()) {
            for (ICreativeTabBuilder tabBuilder : userTabs) {
                ResourceLocation tabLoc = parseLocation(tabBuilder.getId());
                if (!createdCustomTabs.add(tabLoc)) continue;

                event.register(Registries.CREATIVE_MODE_TAB, tabLoc, () -> net.minecraft.world.item.CreativeModeTab.builder()
                        .title(Component.literal(tabBuilder.getTitle() != null ? tabBuilder.getTitle() : tabBuilder.getId()))
                        .icon(() -> {
                            String iconId = tabBuilder.getIconItem() != null ? tabBuilder.getIconItem() : "luatweaker:custom_ruby";
                            Item item = createdItems.get(parseLocation(iconId));
                            if (item == null) item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(parseLocation(iconId));
                            return new ItemStack(item != null && item != net.minecraft.world.item.Items.AIR ? item : net.minecraft.world.item.Items.DIAMOND);
                        })
                        .displayItems((params, output) -> {
                            for (IItemBuilder b : contentService.getRegisteredItems()) {
                                if (b.getCreativeTab() != null && (tabBuilder.getId().equalsIgnoreCase(b.getCreativeTab()) || parseLocation(tabBuilder.getId()).equals(parseLocation(b.getCreativeTab())))) {
                                    Item item = createdItems.get(parseLocation(b.getId()));
                                    if (item == null) item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(parseLocation(b.getId()));
                                    if (item != null && item != net.minecraft.world.item.Items.AIR) output.accept(item);
                                }
                            }
                            for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
                                if (b.getCreativeTab() != null && (tabBuilder.getId().equalsIgnoreCase(b.getCreativeTab()) || parseLocation(tabBuilder.getId()).equals(parseLocation(b.getCreativeTab())))) {
                                    Item item = createdItems.get(parseLocation(b.getId()));
                                    if (item == null) item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(parseLocation(b.getId()));
                                    if (item != null && item != net.minecraft.world.item.Items.AIR) output.accept(item);
                                }
                            }
                        })
                        .build()
                );
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Creative Tab: " + tabLoc);
            }
        }

        ResourceLocation defaultTabId = ResourceLocation.fromNamespaceAndPath("luatweaker", "ruby_tab");
        if (!createdCustomTabs.contains(defaultTabId)) {
            createdCustomTabs.add(defaultTabId);
            event.register(Registries.CREATIVE_MODE_TAB, defaultTabId, () -> net.minecraft.world.item.CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.luatweaker.ruby_tab"))
                    .icon(() -> {
                        Item rubyItem = createdItems.get(ResourceLocation.fromNamespaceAndPath("luatweaker", "custom_ruby"));
                        if (rubyItem == null) rubyItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("luatweaker", "custom_ruby"));
                        return new ItemStack(rubyItem != null ? rubyItem : net.minecraft.world.item.Items.DIAMOND);
                    })
                    .displayItems((params, output) -> {
                        for (IItemBuilder b : contentService.getRegisteredItems()) {
                            if (b.getCreativeTab() == null || b.getCreativeTab().isEmpty() || b.getCreativeTab().contains("ruby_tab")) {
                                Item item = createdItems.get(parseLocation(b.getId()));
                                if (item == null) item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(parseLocation(b.getId()));
                                if (item != null && item != net.minecraft.world.item.Items.AIR) output.accept(item);
                            }
                        }
                        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
                            if (b.getCreativeTab() == null || b.getCreativeTab().isEmpty() || b.getCreativeTab().contains("ruby_tab")) {
                                Item item = createdItems.get(parseLocation(b.getId()));
                                if (item == null) item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(parseLocation(b.getId()));
                                if (item != null && item != net.minecraft.world.item.Items.AIR) output.accept(item);
                            }
                        }
                    })
                    .build()
            );
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Default Creative Tab: " + defaultTabId);
        }
    }

    @SubscribeEvent
    public void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        ResourceLocation currentTabLoc = event.getTabKey().location();
        // Custom tabs created by LuaTweaker already populate their items in their displayItems callback
        if (currentTabLoc.getNamespace().equalsIgnoreCase("luatweaker") || isLuaTweakerTab(currentTabLoc)) {
            return;
        }

        for (IItemBuilder b : contentService.getRegisteredItems()) {
            if (b.getCreativeTab() != null && !b.getCreativeTab().isBlank() && parseLocation(b.getCreativeTab()).equals(currentTabLoc)) {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(parseLocation(b.getId()));
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    event.accept(item);
                }
            }
        }
        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            if (b.getCreativeTab() != null && !b.getCreativeTab().isBlank() && parseLocation(b.getCreativeTab()).equals(currentTabLoc)) {
                Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(parseLocation(b.getId()));
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    event.accept(item);
                }
            }
        }
    }

    private boolean isLuaTweakerTab(ResourceLocation tabLoc) {
        for (ICreativeTabBuilder tab : contentService.getRegisteredTabs()) {
            if (parseLocation(tab.getId()).equals(tabLoc)) return true;
        }
        return false;
    }





    private Holder<ArmorMaterial> resolveArmorMaterial(IItemBuilder builder, ArmorItem.Type armorType) {
        String matId = builder.getArmorMaterial();
        IArmorMaterialBuilder explicitMat = matId != null ? contentService.getArmorMaterial(matId) : null;

        String layerPath;
        int defense;
        float toughness;
        float knockbackRes;
        int enchantability;
        String soundId;

        if (explicitMat != null) {
            layerPath = explicitMat.getLayer();
            defense = builder.getDefense() > 0 ? builder.getDefense() : explicitMat.getDefense(armorType.name().toLowerCase());
            toughness = builder.getToughness() > 0 ? builder.getToughness() : explicitMat.getToughness();
            knockbackRes = builder.getKnockbackResistance() > 0 ? builder.getKnockbackResistance() : explicitMat.getKnockbackResistance();
            enchantability = builder.getEnchantability() > 0 ? builder.getEnchantability() : explicitMat.getEnchantability();
            soundId = explicitMat.getEquipSound();
        } else {
            if (builder.getArmorTexture() != null && !builder.getArmorTexture().isBlank()) {
                layerPath = builder.getArmorTexture();
            } else if (builder.getTexture() != null && !builder.getTexture().isBlank()) {
                layerPath = builder.getTexture();
            } else {
                layerPath = "luatweaker:ruby";
            }
            defense = builder.getDefense() > 0 ? builder.getDefense() : 4;
            toughness = builder.getToughness() > 0 ? builder.getToughness() : 3.0f;
            knockbackRes = builder.getKnockbackResistance() > 0 ? builder.getKnockbackResistance() : 0.1f;
            enchantability = builder.getEnchantability() > 0 ? builder.getEnchantability() : 25;
            soundId = "minecraft:item.armor.equip_diamond";
        }

        ResourceLocation layerLoc = parseLocation(layerPath.contains(":") ? layerPath : "luatweaker:" + layerPath);
        List<ArmorMaterial.Layer> layers = List.of(new ArmorMaterial.Layer(layerLoc));

        java.util.EnumMap<ArmorItem.Type, Integer> defenseMap = new java.util.EnumMap<>(ArmorItem.Type.class);
        defenseMap.put(ArmorItem.Type.HELMET, armorType == ArmorItem.Type.HELMET ? defense : 4);
        defenseMap.put(ArmorItem.Type.CHESTPLATE, armorType == ArmorItem.Type.CHESTPLATE ? defense : 9);
        defenseMap.put(ArmorItem.Type.LEGGINGS, armorType == ArmorItem.Type.LEGGINGS ? defense : 7);
        defenseMap.put(ArmorItem.Type.BOOTS, armorType == ArmorItem.Type.BOOTS ? defense : 4);

        ResourceLocation soundLoc = parseLocation(soundId != null && !soundId.isBlank() ? soundId : "minecraft:item.armor.equip_diamond");
        Holder<net.minecraft.sounds.SoundEvent> soundHolder = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getHolder(soundLoc)
                .map(h -> (Holder<net.minecraft.sounds.SoundEvent>) h)
                .orElse(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_DIAMOND);


        return Holder.direct(new ArmorMaterial(
                defenseMap,
                enchantability,
                soundHolder,
                () -> net.minecraft.world.item.crafting.Ingredient.EMPTY,
                layers,
                toughness,
                knockbackRes
        ));
    }


    private void registerItems(RegisterEvent event) {
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            ResourceLocation rl = parseLocation(builder.getId());
            Item.Properties props = new Item.Properties();

            if (builder.getDurability() > 0) {
                props.durability(builder.getDurability());
            } else {
                props.stacksTo(builder.getMaxStackSize());
            }

            Rarity rarity = switch (builder.getRarity()) {
                case "UNCOMMON" -> Rarity.UNCOMMON;
                case "RARE" -> Rarity.RARE;
                case "EPIC" -> Rarity.EPIC;
                default -> Rarity.COMMON;
            };
            props.rarity(rarity);

            if (builder.getAttackDamage() > 0 || builder.getAttackSpeed() != 0) {
                net.minecraft.world.item.component.ItemAttributeModifiers.Builder attrBuilder = net.minecraft.world.item.component.ItemAttributeModifiers.builder();
                if (builder.getAttackDamage() > 0) {
                    attrBuilder.add(
                            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE,
                            new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                    ResourceLocation.fromNamespaceAndPath("luatweaker", "weapon_attack_damage"),
                                    builder.getAttackDamage(),
                                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                            ),
                            net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND
                    );
                }
                if (builder.getAttackSpeed() != 0) {
                    attrBuilder.add(
                            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_SPEED,
                            new net.minecraft.world.entity.ai.attributes.AttributeModifier(
                                    ResourceLocation.fromNamespaceAndPath("luatweaker", "weapon_attack_speed"),
                                    builder.getAttackSpeed(),
                                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE
                            ),
                            net.minecraft.world.entity.EquipmentSlotGroup.MAINHAND
                    );
                }
                props.attributes(attrBuilder.build());
            }

            if (builder.getFoodHunger() > 0) {
                net.minecraft.world.food.FoodProperties.Builder foodB = new net.minecraft.world.food.FoodProperties.Builder()
                        .nutrition(builder.getFoodHunger())
                        .saturationModifier(builder.getFoodSaturation());
                if (builder.isAlwaysEdible()) {
                    foodB.alwaysEdible();
                }
                props.food(foodB.build());
            }

            String typeStr = builder.getType() != null ? builder.getType().toUpperCase() : "ITEM";
            Item item;

            if (typeStr.equals("HELMET") || typeStr.equals("CHESTPLATE") || typeStr.equals("LEGGINGS") || typeStr.equals("BOOTS")) {
                ArmorItem.Type armorType = switch (typeStr) {
                    case "HELMET" -> ArmorItem.Type.HELMET;
                    case "CHESTPLATE" -> ArmorItem.Type.CHESTPLATE;
                    case "LEGGINGS" -> ArmorItem.Type.LEGGINGS;
                    default -> ArmorItem.Type.BOOTS;
                };
                Holder<ArmorMaterial> material = resolveArmorMaterial(builder, armorType);

                item = new ArmorItem(material, armorType, props) {
                    @Override
                    public Component getName(ItemStack stack) {
                        if (builder.getDisplayName() != null && !builder.getDisplayName().isBlank()) return Component.literal(builder.getDisplayName());
                        return super.getName(stack);
                    }
                    @Override
                    public boolean isFoil(ItemStack stack) { return builder.isGlow() || super.isFoil(stack); }
                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        for (String t : builder.getTooltips()) tooltipComponents.add(Component.literal(t));
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    }
                };
            } else if (typeStr.equals("SWORD")) {
                item = new net.minecraft.world.item.SwordItem(net.minecraft.world.item.Tiers.DIAMOND, props) {
                    @Override
                    public Component getName(ItemStack stack) {
                        if (builder.getDisplayName() != null && !builder.getDisplayName().isBlank()) return Component.literal(builder.getDisplayName());
                        return super.getName(stack);
                    }
                    @Override
                    public boolean isFoil(ItemStack stack) { return builder.isGlow() || super.isFoil(stack); }
                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        for (String t : builder.getTooltips()) tooltipComponents.add(Component.literal(t));
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    }
                };
            } else if (typeStr.equals("PICKAXE")) {
                item = new net.minecraft.world.item.PickaxeItem(net.minecraft.world.item.Tiers.DIAMOND, props) {
                    @Override
                    public Component getName(ItemStack stack) {
                        if (builder.getDisplayName() != null && !builder.getDisplayName().isBlank()) return Component.literal(builder.getDisplayName());
                        return super.getName(stack);
                    }
                    @Override
                    public boolean isFoil(ItemStack stack) { return builder.isGlow() || super.isFoil(stack); }
                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        for (String t : builder.getTooltips()) tooltipComponents.add(Component.literal(t));
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    }
                };
            } else if (typeStr.equals("AXE")) {
                item = new net.minecraft.world.item.AxeItem(net.minecraft.world.item.Tiers.DIAMOND, props) {
                    @Override
                    public Component getName(ItemStack stack) {
                        if (builder.getDisplayName() != null && !builder.getDisplayName().isBlank()) return Component.literal(builder.getDisplayName());
                        return super.getName(stack);
                    }
                    @Override
                    public boolean isFoil(ItemStack stack) { return builder.isGlow() || super.isFoil(stack); }
                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        for (String t : builder.getTooltips()) tooltipComponents.add(Component.literal(t));
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    }
                };
            } else if (typeStr.equals("SHOVEL")) {
                item = new net.minecraft.world.item.ShovelItem(net.minecraft.world.item.Tiers.DIAMOND, props) {
                    @Override
                    public Component getName(ItemStack stack) {
                        if (builder.getDisplayName() != null && !builder.getDisplayName().isBlank()) return Component.literal(builder.getDisplayName());
                        return super.getName(stack);
                    }
                    @Override
                    public boolean isFoil(ItemStack stack) { return builder.isGlow() || super.isFoil(stack); }
                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        for (String t : builder.getTooltips()) tooltipComponents.add(Component.literal(t));
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    }
                };
            } else if (typeStr.equals("HOE")) {
                item = new net.minecraft.world.item.HoeItem(net.minecraft.world.item.Tiers.DIAMOND, props) {
                    @Override
                    public Component getName(ItemStack stack) {
                        if (builder.getDisplayName() != null && !builder.getDisplayName().isBlank()) return Component.literal(builder.getDisplayName());
                        return super.getName(stack);
                    }
                    @Override
                    public boolean isFoil(ItemStack stack) { return builder.isGlow() || super.isFoil(stack); }
                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        for (String t : builder.getTooltips()) tooltipComponents.add(Component.literal(t));
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    }
                };
            } else if (typeStr.equals("RANGED") || typeStr.equals("BOW") || typeStr.equals("STAFF")) {
                item = new Item(props) {
                    @Override
                    public Component getName(ItemStack stack) {
                        if (builder.getDisplayName() != null && !builder.getDisplayName().isBlank()) return Component.literal(builder.getDisplayName());
                        return super.getName(stack);
                    }
                    @Override
                    public boolean isFoil(ItemStack stack) { return builder.isGlow() || super.isFoil(stack); }
                    @Override
                    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                        ItemStack stack = player.getItemInHand(hand);
                        if (!level.isClientSide()) {
                            net.minecraft.world.entity.projectile.SmallFireball fireball = new net.minecraft.world.entity.projectile.SmallFireball(
                                    level, player, player.getLookAngle().normalize()
                            );
                            fireball.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                            fireball.addTag("lt_proj_" + builder.getId());
                            level.addFreshEntity(fireball);
                            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                                    net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
                        }
                        if (builder.getRightClickHandler() != null) {
                            try {
                                builder.getRightClickHandler().accept(new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player), stack);
                            } catch (Exception e) {
                                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed right-click handler for " + builder.getId() + ": " + e.getMessage());
                            }
                        }
                        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
                    }

                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        for (String t : builder.getTooltips()) tooltipComponents.add(Component.literal(t));
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    }
                };
            } else {
                item = new Item(props) {
                    @Override
                    public Component getName(ItemStack stack) {
                        if (builder.getDisplayName() != null && !builder.getDisplayName().isBlank()) return Component.literal(builder.getDisplayName());
                        return super.getName(stack);
                    }
                    @Override
                    public boolean isFoil(ItemStack stack) { return builder.isGlow() || super.isFoil(stack); }

                    @Override
                    public ItemStack finishUsingItem(ItemStack stack, Level level, net.minecraft.world.entity.LivingEntity entity) {
                        ItemStack result = super.finishUsingItem(stack, level, entity);
                        if (builder.getOnConsumeHandler() != null && entity instanceof Player player) {
                            try {
                                builder.getOnConsumeHandler().accept(new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player), stack);
                            } catch (Exception e) {
                                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onConsume handler for " + builder.getId() + ": " + e.getMessage());
                            }
                        }
                        return result;
                    }

                    @Override
                    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
                        ItemStack stack = player.getItemInHand(hand);
                        if (builder.getRightClickHandler() != null) {
                            try {
                                builder.getRightClickHandler().accept(new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player), stack);
                            } catch (Exception e) {
                                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed right-click handler for " + builder.getId() + ": " + e.getMessage());
                            }
                        }
                        return super.use(level, player, hand);
                    }

                    @Override
                    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
                        for (String t : builder.getTooltips()) tooltipComponents.add(Component.literal(t));
                        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
                    }

                    @Override
                    public int getBurnTime(ItemStack itemStack, net.minecraft.world.item.crafting.RecipeType<?> recipeType) {
                        return builder.getBurnTime() > 0 ? builder.getBurnTime() : super.getBurnTime(itemStack, recipeType);
                    }
                };
            }

            event.register(Registries.ITEM, rl, () -> item);
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Item (" + builder.getType() + ") to NeoForge: " + rl);
        }
    }


    private void registerBlocks(RegisterEvent event) {
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            ResourceLocation rl = parseLocation(builder.getId());
            BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                    .destroyTime(builder.getHardness())
                    .explosionResistance(builder.getResistance())
                    .lightLevel(state -> builder.getLightLevel());

            SoundType soundType = switch (builder.getSoundType()) {
                case "WOOD" -> SoundType.WOOD;
                case "GRAVEL" -> SoundType.GRAVEL;
                case "METAL" -> SoundType.METAL;
                case "GLASS" -> SoundType.GLASS;
                case "GRASS" -> SoundType.GRASS;
                case "SAND" -> SoundType.SAND;
                case "SNOW" -> SoundType.SNOW;
                case "WOOL" -> SoundType.WOOL;
                case "ANVIL" -> SoundType.ANVIL;
                case "NETHERITE" -> SoundType.NETHERITE_BLOCK;
                default -> SoundType.STONE;
            };
            props.sound(soundType);

            if (builder.getRequiresTool()) {
                props.requiresCorrectToolForDrops();
            }
            if (builder.getFriction() != 0.6f) {
                props.friction(builder.getFriction());
            }

            Block block;
            if (builder.getId().endsWith("_stairs")) {
                Block baseBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(parseLocation(builder.getId().replace("_stairs", "_block")));
                if (baseBlock == null || baseBlock == net.minecraft.world.level.block.Blocks.AIR) {
                    baseBlock = net.minecraft.world.level.block.Blocks.OAK_PLANKS;
                }
                block = new net.minecraft.world.level.block.StairBlock(baseBlock.defaultBlockState(), props);
            } else if (builder.getId().endsWith("_slab")) {
                block = new net.minecraft.world.level.block.SlabBlock(props);
            } else if (builder.getId().endsWith("_wall")) {
                block = new net.minecraft.world.level.block.WallBlock(props);
            } else {

                block = new Block(props) {
                    @Override
                    protected List<ItemStack> getDrops(BlockState state, net.minecraft.world.level.storage.loot.LootParams.Builder params) {
                        if (builder.getDropItemId() != null && !builder.getDropItemId().isBlank()) {
                            Item dropItem = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(parseLocation(builder.getDropItemId()));
                            if (dropItem != null && dropItem != net.minecraft.world.item.Items.AIR) {
                                int count = builder.getMinDropCount();
                                if (builder.getMaxDropCount() > builder.getMinDropCount()) {
                                    count += params.getLevel().getRandom().nextInt(builder.getMaxDropCount() - builder.getMinDropCount() + 1);
                                }
                                return List.of(new ItemStack(dropItem, count));
                            }
                        }
                        return super.getDrops(state, params);
                    }

                    @Override
                    protected InteractionResult useWithoutItem(BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player, BlockHitResult hitResult) {
                        if (builder.getRightClickHandler() != null) {
                            try {
                                builder.getRightClickHandler().accept(new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player), state);
                            } catch (Exception e) {
                                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed block right-click handler for " + builder.getId() + ": " + e.getMessage());
                            }
                            return InteractionResult.sidedSuccess(level.isClientSide());
                        }
                        return super.useWithoutItem(state, level, pos, player, hitResult);
                    }
                };
            }

            event.register(Registries.BLOCK, rl, () -> block);
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Block to NeoForge: " + rl);
        }
    }




    private final java.util.Map<ResourceLocation, net.minecraft.world.level.material.Fluid> createdFluids = new java.util.LinkedHashMap<>();

    private void registerFluids(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            try {
                ResourceLocation rl = parseLocation(builder.getId());
                net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties properties = new net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties(
                        () -> (net.neoforged.neoforge.fluids.FluidType) net.neoforged.neoforge.registries.NeoForgeRegistries.FLUID_TYPES.get(rl),
                        () -> createdFluids.get(rl),
                        () -> createdFluids.get(parseLocation("flowing_" + builder.getId()))
                );

                net.minecraft.world.level.material.Fluid stillFluid = new net.neoforged.neoforge.fluids.BaseFlowingFluid.Source(properties);
                net.minecraft.world.level.material.Fluid flowingFluid = new net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing(properties);

                createdFluids.put(rl, stillFluid);
                createdFluids.put(parseLocation("flowing_" + builder.getId()), flowingFluid);

                event.register(Registries.FLUID, rl, () -> stillFluid);
                event.register(Registries.FLUID, parseLocation("flowing_" + builder.getId()), () -> flowingFluid);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Fluid: " + rl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register fluid " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    private void registerFluidBlocks(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            try {
                ResourceLocation rl = parseLocation(builder.getId());
                ResourceLocation blockRl = parseLocation(builder.getId() + "_block");
                net.minecraft.world.level.material.FlowingFluid stillFluid = (net.minecraft.world.level.material.FlowingFluid) createdFluids.get(rl);
                if (stillFluid != null) {
                    Block liquidBlock = new net.minecraft.world.level.block.LiquidBlock(stillFluid, BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.WATER)) {
                        @Override
                        public void entityInside(BlockState state, Level level, net.minecraft.core.BlockPos pos, net.minecraft.world.entity.Entity entity) {
                            super.entityInside(state, level, pos, entity);
                            if (builder.getTouchHandler() != null && entity instanceof Player player) {
                                try {
                                    builder.getTouchHandler().accept(new com.luatweaker.platform.entity.NeoForgePlayerWrapper(player));
                                } catch (Exception e) {
                                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed fluid onTouch handler for " + builder.getId() + ": " + e.getMessage());
                                }
                            }
                        }
                    };
                    createdBlocks.put(blockRl, liquidBlock);
                    event.register(Registries.BLOCK, blockRl, () -> liquidBlock);
                    LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom LiquidBlock: " + blockRl);
                }
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register fluid block " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    private void registerFluidItems(RegisterEvent event) {
        for (IFluidBuilder builder : contentService.getRegisteredFluids()) {
            try {
                ResourceLocation rl = parseLocation(builder.getId());
                ResourceLocation bucketRl = parseLocation(builder.getId() + "_bucket");
                ResourceLocation altBucketRl = parseLocation(builder.getId().replace("_fluid", "") + "_bucket");
                net.minecraft.world.level.material.Fluid stillFluid = createdFluids.get(rl);
                if (stillFluid != null) {
                    Item bucketItem = new net.minecraft.world.item.BucketItem(stillFluid, new Item.Properties().craftRemainder(net.minecraft.world.item.Items.BUCKET).stacksTo(1)) {
                        @Override
                        public Component getName(ItemStack stack) {
                            return Component.literal("Ruby Liquid Bucket");
                        }
                    };
                    createdItems.put(bucketRl, bucketItem);
                    createdItems.put(altBucketRl, bucketItem);
                    event.register(Registries.ITEM, bucketRl, () -> bucketItem);
                    LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom BucketItem: " + bucketRl);
                }
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register fluid bucket " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    private ResourceLocation parseLocation(String id) {
        if (id == null || id.isBlank()) {
            return ResourceLocation.fromNamespaceAndPath("luatweaker", "empty");
        }
        if (id.contains(":")) {
            String[] parts = id.split(":", 2);
            return ResourceLocation.fromNamespaceAndPath(parts[0], parts[1]);
        }
        return ResourceLocation.fromNamespaceAndPath("luatweaker", id);
    }

}

