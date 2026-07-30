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

                String type = builder.getType() != null ? builder.getType().toUpperCase() : "SIMPLE";
                Item item = switch (type) {
                    case "SWORD" -> new SwordItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "PICKAXE" -> new PickaxeItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "AXE" -> new AxeItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "SHOVEL" -> new ShovelItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    case "HOE" -> new HoeItem(Tiers.IRON, props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (builder.getRightClickHandler() != null) {
                                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }
                    };
                    default -> new Item(props) {
                        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
                            if (builder.getRightClickHandler() != null) {
                                try {
                                    builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), player.getItemInHand(hand));
                                } catch (Exception e) {
                                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed right-click handler for " + builder.getId() + ": " + e.getMessage());
                                }
                                return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide());
                            }
                            return super.use(level, player, hand);
                        }

                        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
                            if (builder.getOnConsumeHandler() != null && entity instanceof Player player) {
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
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Item (" + builder.getType() + ") to NeoForge: " + rl);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register item " + builder.getId() + ": " + e.getMessage());
            }
        }
    }
}
