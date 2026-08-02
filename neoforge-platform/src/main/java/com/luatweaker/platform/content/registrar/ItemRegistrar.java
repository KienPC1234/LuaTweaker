package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.function.Function;

public final class ItemRegistrar {
    private final IContentService contentService;
    private final Map<ResourceLocation, Item> createdItems;
    private final Function<String, ResourceLocation> locationParser;

    public ItemRegistrar(IContentService contentService, Map<ResourceLocation, Item> createdItems, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdItems = createdItems;
        this.locationParser = locationParser;
    }

    public void registerItems(RegisterEvent event) {
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            try {
                ResourceLocation rl = locationParser.apply(builder.getId());
                Item.Properties props = new Item.Properties();
                if (builder.getMaxStackSize() > 0) props.stacksTo(builder.getMaxStackSize());
                if (builder.getDurability() > 0) props.durability(builder.getDurability());

                if (builder.getFoodHunger() > 0) {
                    net.minecraft.world.food.FoodProperties.Builder foodBuilder = new net.minecraft.world.food.FoodProperties.Builder()
                            .nutrition(builder.getFoodHunger())
                            .saturationModifier(builder.getFoodSaturation());
                    if (builder.isAlwaysEdible()) {
                        foodBuilder.alwaysEdible();
                    }
                    props.food(foodBuilder.build());
                }

                float dmg = builder.getAttackDamage();
                float spd = builder.getAttackSpeed() != 0 ? builder.getAttackSpeed() : -2.4f;
                if (dmg > 0) {
                    props.attributes(SwordItem.createAttributes(Tiers.DIAMOND, Math.max(0, (int) dmg - 3), spd));
                }

                String type = builder.getType() != null ? builder.getType().toUpperCase() : "SIMPLE";

                Item item = switch (type) {
                    case "SWORD" -> new SwordItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "PICKAXE" -> new PickaxeItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "AXE" -> new AxeItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "SHOVEL" -> new ShovelItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "HOE" -> new HoeItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "HELMET" -> new CustomArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.HELMET, props, builder);
                    case "CHESTPLATE" -> new CustomArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.CHESTPLATE, props, builder);
                    case "LEGGINGS" -> new CustomArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.LEGGINGS, props, builder);
                    case "BOOTS" -> new CustomArmorItem(ArmorMaterials.DIAMOND, ArmorItem.Type.BOOTS, props, builder);
                    default -> new Item(props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (!level.isClientSide() && builder.getRightClickHandler() != null) {
                                try {
                                    builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                } catch (Exception e) {
                                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed right-click handler for " + builder.getId() + ": " + e.getMessage());
                                }
                            }
                            if (builder.getFoodHunger() > 0) {
                                player.startUsingItem(hand);
                                return InteractionResultHolder.consume(player.getItemInHand(hand));
                            }
                            return builder.getRightClickHandler() != null
                                    ? InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide())
                                    : super.use(level, player, hand);
                        }

                        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
                            if (!level.isClientSide() && builder.getOnConsumeHandler() != null && entity instanceof Player player) {
                                try {
                                    builder.getOnConsumeHandler().accept(new NeoForgePlayerWrapper(player), stack);
                                } catch (Exception e) {
                                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onConsume handler for " + builder.getId() + ": " + e.getMessage());
                                }
                            }
                            return super.finishUsingItem(stack, level, entity);
                        }
                    };
                };

                event.register(Registries.ITEM, rl, () -> item);
                createdItems.put(rl, item);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register item " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public static class CustomArmorItem extends ArmorItem {
        private final IItemBuilder builder;

        public CustomArmorItem(net.minecraft.core.Holder<ArmorMaterial> material, Type type, Properties properties, IItemBuilder builder) {
            super(material, type, properties);
            this.builder = builder;
        }

        @Override
        public ResourceLocation getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
            String customTex = builder.getArmorTexture();
            if (customTex == null || customTex.isEmpty()) {
                customTex = builder.getArmorMaterial();
            }
            if (customTex != null && !customTex.isEmpty()) {
                String domain = "luatweaker";
                String name = customTex;
                if (customTex.contains(":")) {
                    String[] parts = customTex.split(":", 2);
                    domain = parts[0];
                    name = parts[1];
                }
                String layerSuffix = (slot == net.minecraft.world.entity.EquipmentSlot.LEGS || getType() == Type.LEGGINGS) ? "layer_2" : "layer_1";
                if (!name.startsWith("textures/")) {
                    name = "textures/models/armor/" + name + "_" + layerSuffix + ".png";
                }
                return ResourceLocation.fromNamespaceAndPath(domain, name);
            }
            return super.getArmorTexture(stack, entity, slot, layer, inner);
        }
    }
}
