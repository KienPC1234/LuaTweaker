package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IArmorMaterialBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.platform.entity.NeoForgeEntityWrapper;
import com.luatweaker.platform.entity.NeoForgePlayerWrapper;
import com.luatweaker.platform.interaction.NeoForgeInteractableItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.Map;
import java.util.function.Function;

public final class ItemRegistrar {
    /** Vanilla sword attack speed when the Lua builder does not set one. */
    private static final float DEFAULT_ATTACK_SPEED = -2.4f;
    /** Default namespace for armor textures when the Lua builder omits one. */
    private static final String DEFAULT_NAMESPACE = "luatweaker";
    /** Relative resource path prefix for custom armor layer textures. */
    private static final String ARMOR_TEXTURE_DIR = "textures/models/armor/";

    public static final Map<Item, IItemBuilder> BUILDER_BY_ITEM = new java.util.IdentityHashMap<>();

    private final IContentService contentService;
    private final Map<ResourceLocation, Item> createdItems;
    private final Function<String, ResourceLocation> locationParser;

    /** Armor materials built from {@code startup:createArmorMaterial(...)}, keyed by requested id. */
    private final Map<String, net.minecraft.core.Holder<ArmorMaterial>> armorMaterials = new java.util.HashMap<>();

    public ItemRegistrar(IContentService contentService, Map<ResourceLocation, Item> createdItems, Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdItems = createdItems;
        this.locationParser = locationParser;
    }

    /**
     * Resolves the armor material declared by {@code item:armorMaterial("ruby")}. Falls back to
     * diamond (with a loud warning) when the Lua material was never registered, instead of
     * silently registering diamond stats under a custom name.
     */
    private net.minecraft.core.Holder<ArmorMaterial> resolveArmorMaterial(IItemBuilder builder) {
        String requested = builder.getArmorMaterial();
        if (requested == null || requested.isBlank()) {
            return ArmorMaterials.DIAMOND;
        }
        net.minecraft.core.Holder<ArmorMaterial> cached = armorMaterials.get(requested);
        if (cached != null) {
            return cached;
        }
        IArmorMaterialBuilder definition = contentService.getArmorMaterial(requested);
        if (definition == null && !requested.contains(":")) {
            definition = contentService.getArmorMaterial(DEFAULT_NAMESPACE + ":" + requested);
        }
        if (definition == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Item '" + builder.getId() + "' requests armor material '" + requested
                            + "' which startup:createArmorMaterial never defined; falling back to diamond");
            return ArmorMaterials.DIAMOND;
        }
        net.minecraft.core.Holder<ArmorMaterial> material = buildArmorMaterial(definition);
        armorMaterials.put(requested, material);
        return material;
    }

    private static net.minecraft.core.Holder<ArmorMaterial> buildArmorMaterial(IArmorMaterialBuilder definition) {
        Map<ArmorItem.Type, Integer> defense = new java.util.EnumMap<>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, definition.getDefense("helmet"));
        defense.put(ArmorItem.Type.CHESTPLATE, definition.getDefense("chestplate"));
        defense.put(ArmorItem.Type.LEGGINGS, definition.getDefense("leggings"));
        defense.put(ArmorItem.Type.BOOTS, definition.getDefense("boots"));

        ResourceLocation layerId = ResourceLocation.tryParse(
                definition.getLayer() != null ? definition.getLayer() : DEFAULT_NAMESPACE + ":" + definition.getId());
        if (layerId == null) {
            layerId = ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE, definition.getId().toLowerCase());
        }
        ResourceLocation soundId = ResourceLocation.tryParse(definition.getEquipSound());
        net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent> equipSound = soundId == null
                ? net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_DIAMOND
                : net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getHolder(soundId)
                        .map(holder -> (net.minecraft.core.Holder<net.minecraft.sounds.SoundEvent>) holder)
                        .orElse(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_DIAMOND);

        ArmorMaterial material = new ArmorMaterial(
                defense,
                definition.getEnchantability(),
                equipSound,
                () -> net.minecraft.world.item.crafting.Ingredient.EMPTY,
                java.util.List.of(new ArmorMaterial.Layer(layerId)),
                definition.getToughness(),
                definition.getKnockbackResistance());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered custom armor material '" + definition.getId() + "' (layer " + layerId
                        + ", toughness " + definition.getToughness() + ")");
        return net.minecraft.core.Holder.direct(material);
    }

    /**
     * Lua-defined {@code OnUseOnBlock} hook shared by every item type. Returns
     * {@code null} when the item has no handler (caller falls back to the item's
     * default behavior), otherwise the interaction result of the Lua callback
     * (SUCCESS = click consumed, PASS = block handles the click).
     */
    static InteractionResult handleUseOnBlock(net.minecraft.world.item.context.UseOnContext context, IItemBuilder builder) {
        if (context.getLevel().isClientSide() || builder.getOnUseOnBlockHandler() == null) {
            return null;
        }
        try {
            net.minecraft.core.BlockPos pos = context.getClickedPos();
            java.util.Map<String, Object> hit = java.util.Map.of(
                    "X", pos.getX(), "Y", pos.getY(), "Z", pos.getZ(),
                    "Face", context.getClickedFace().getName());
            boolean consumed = Boolean.TRUE.equals(builder.getOnUseOnBlockHandler().apply(
                    new NeoForgePlayerWrapper(context.getPlayer()), hit));
            return consumed ? InteractionResult.SUCCESS : InteractionResult.PASS;
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed onUseOnBlock handler for " + builder.getId() + ": " + e.getMessage());
            return InteractionResult.PASS;
        }
    }

    public static UseAnim resolveUseAnim(String name) {
        if (name == null || name.isBlank()) return UseAnim.NONE;
        return switch (name.toUpperCase()) {
            case "EAT" -> UseAnim.EAT;
            case "DRINK" -> UseAnim.DRINK;
            case "BLOCK" -> UseAnim.BLOCK;
            case "BOW" -> UseAnim.BOW;
            case "SPEAR" -> UseAnim.SPEAR;
            case "CROSSBOW" -> UseAnim.CROSSBOW;
            case "SPYGLASS" -> UseAnim.SPYGLASS;
            case "TOOT_HORN" -> UseAnim.TOOT_HORN;
            case "BRUSH" -> UseAnim.BRUSH;
            default -> UseAnim.NONE;
        };
    }

    public static int resolveUseDuration(IItemBuilder builder) {
        if (builder.getUseDuration() > 0) return builder.getUseDuration();
        if (builder.getFoodHunger() > 0) return 32;
        return 0;
    }

    public static Tier resolveTier(IItemBuilder builder) {
        int level = builder.getMiningLevel();
        Tier base = switch (level) {
            case 0 -> Tiers.WOOD;
            case 1 -> Tiers.STONE;
            case 3 -> Tiers.DIAMOND;
            case 4, 5, 6, 7, 8, 9, 10 -> Tiers.NETHERITE;
            default -> Tiers.IRON;
        };
        float customSpeed = builder.getMiningSpeed();
        int customDurability = builder.getDurability();
        int customEnchant = builder.getEnchantability();
        if (customSpeed == 4.0f && customDurability <= 0 && customEnchant == 10) {
            return base;
        }
        return new Tier() {
            @Override public int getUses() { return customDurability > 0 ? customDurability : base.getUses(); }
            @Override public float getSpeed() { return customSpeed > 0 ? customSpeed : base.getSpeed(); }
            @Override public float getAttackDamageBonus() { return base.getAttackDamageBonus(); }
            @Override public net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> getIncorrectBlocksForDrops() {
                return base.getIncorrectBlocksForDrops();
            }
            @Override public int getEnchantmentValue() { return customEnchant > 0 ? customEnchant : base.getEnchantmentValue(); }
            @Override public net.minecraft.world.item.crafting.Ingredient getRepairIngredient() { return base.getRepairIngredient(); }
        };
    }

    private static NeoForgeInteractableItem wrapItem(Player player, ItemStack stack) {
        int slot = -1;
        if (player != null) {
            var inv = player.getInventory();
            if (player.getMainHandItem() == stack) {
                slot = inv.selected;
            } else {
                for (int i = 0; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i) == stack) {
                        slot = i;
                        break;
                    }
                }
            }
        }
        return new NeoForgeInteractableItem(player, slot, stack);
    }

    public static InteractionResultHolder<ItemStack> handleItemUse(Level level, Player player, net.minecraft.world.InteractionHand hand, IItemBuilder builder) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide() && builder.getRightClickHandler() != null) {
            try {
                builder.getRightClickHandler().accept(new NeoForgePlayerWrapper(player), wrapItem(player, stack));
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed right-click handler for " + builder.getId() + ": " + e.getMessage());
            }
        }
        if (builder.getUseDuration() > 0 || builder.getFoodHunger() > 0) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return builder.getRightClickHandler() != null
                ? InteractionResultHolder.sidedSuccess(stack, level.isClientSide())
                : null;
    }

    public static void handleUseTick(Level level, LivingEntity entity, ItemStack stack, int count, IItemBuilder builder) {
        if (!level.isClientSide() && builder.getOnUseTickHandler() != null && entity instanceof Player player) {
            try {
                builder.getOnUseTickHandler().accept(new NeoForgePlayerWrapper(player), wrapItem(player, stack), count);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onUseTick handler for " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public static void handleReleaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft, IItemBuilder builder) {
        if (!level.isClientSide() && builder.getOnReleaseUsingHandler() != null && entity instanceof Player player) {
            try {
                builder.getOnReleaseUsingHandler().accept(new NeoForgePlayerWrapper(player), wrapItem(player, stack), timeLeft);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onReleaseUsing handler for " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public static void handleFinishUsing(ItemStack stack, Level level, LivingEntity entity, IItemBuilder builder) {
        if (!level.isClientSide() && entity instanceof Player player) {
            NeoForgeInteractableItem itemWrapper = wrapItem(player, stack);
            if (builder.getOnFinishUsingHandler() != null) {
                try {
                    builder.getOnFinishUsingHandler().accept(new NeoForgePlayerWrapper(player), itemWrapper);
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onFinishUsing handler for " + builder.getId() + ": " + e.getMessage());
                }
            }
            if (builder.getOnConsumeHandler() != null) {
                try {
                    builder.getOnConsumeHandler().accept(new NeoForgePlayerWrapper(player), itemWrapper);
                } catch (Exception e) {
                    LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onConsume handler for " + builder.getId() + ": " + e.getMessage());
                }
            }
        }
    }

    public static void handleHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker, IItemBuilder builder) {
        if (!attacker.level().isClientSide() && builder.getOnHurtEnemyHandler() != null && attacker instanceof Player player) {
            try {
                builder.getOnHurtEnemyHandler().accept(
                        new NeoForgeEntityWrapper(target),
                        new NeoForgePlayerWrapper(player),
                        wrapItem(player, stack));
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onHurtEnemy handler for " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public static void handleMineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity entity, IItemBuilder builder) {
        if (!level.isClientSide() && builder.getOnMineBlockHandler() != null && entity instanceof Player player) {
            try {
                builder.getOnMineBlockHandler().accept(
                        new NeoForgePlayerWrapper(player),
                        wrapItem(player, stack),
                        pos.getX(), pos.getY(), pos.getZ());
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onMineBlock handler for " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public static void handleSwing(Player player, ItemStack stack) {
        if (player == null || stack == null || stack.isEmpty() || player.level().isClientSide()) return;
        IItemBuilder builder = BUILDER_BY_ITEM.get(stack.getItem());
        if (builder != null && builder.getOnSwingHandler() != null) {
            try {
                builder.getOnSwingHandler().accept(new NeoForgePlayerWrapper(player), wrapItem(player, stack));
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed onSwing handler for " + builder.getId() + ": " + e.getMessage());
            }
        }
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

                Tier tier = resolveTier(builder);
                float dmg = builder.getAttackDamage();
                float spd = builder.getAttackSpeed() != 0 ? builder.getAttackSpeed() : DEFAULT_ATTACK_SPEED;
                if (dmg > 0) {
                    float baseBonus = tier.getAttackDamageBonus();
                    props.attributes(SwordItem.createAttributes(tier, Math.max(-baseBonus, dmg - baseBonus), spd));
                }

                String type = builder.getType() != null ? builder.getType().toUpperCase() : "SIMPLE";

                Item item;
                if (builder.getGeoModel() != null) {
                    item = new com.luatweaker.platform.gecko.LuaGeoItem(props, builder, builder.getGeoModel());
                } else {
                    item = switch (type) {
                        case "SWORD" -> new CustomSwordItem(tier, props, builder);
                        case "PICKAXE" -> new CustomPickaxeItem(tier, props, builder);
                        case "AXE" -> new CustomAxeItem(tier, props, builder);
                        case "SHOVEL" -> new CustomShovelItem(tier, props, builder);
                        case "HOE" -> new CustomHoeItem(tier, props, builder);
                        case "HELMET" -> new CustomArmorItem(
                                resolveArmorMaterial(builder), ArmorItem.Type.HELMET, props, builder);
                        case "CHESTPLATE" -> new CustomArmorItem(
                                resolveArmorMaterial(builder), ArmorItem.Type.CHESTPLATE, props, builder);
                        case "LEGGINGS" -> new CustomArmorItem(
                                resolveArmorMaterial(builder), ArmorItem.Type.LEGGINGS, props, builder);
                        case "BOOTS" -> new CustomArmorItem(
                                resolveArmorMaterial(builder), ArmorItem.Type.BOOTS, props, builder);
                        default -> new CustomGenericItem(props, builder);
                    };
                }

                event.register(Registries.ITEM, rl, () -> item);
                createdItems.put(rl, item);
                BUILDER_BY_ITEM.put(item, builder);
            } catch (Exception e) {
                LuaTweakerLog.get().error(LogStage.SYSTEM, "Failed to register item " + builder.getId() + ": " + e.getMessage());
            }
        }
    }

    public interface RebindableItem {
        void rebind(IItemBuilder newBuilder);
        IItemBuilder getBuilder();
    }

    public static int rebindAll(IContentService contentService, Function<String, ResourceLocation> idParser) {
        if (contentService == null || idParser == null) {
            return 0;
        }
        Map<ResourceLocation, IItemBuilder> newBuilders = new java.util.HashMap<>();
        for (IItemBuilder b : contentService.getRegisteredItems()) {
            newBuilders.put(idParser.apply(b.getId()), b);
        }
        int rebound = 0;
        for (Map.Entry<Item, IItemBuilder> entry : new java.util.ArrayList<>(BUILDER_BY_ITEM.entrySet())) {
            Item item = entry.getKey();
            IItemBuilder oldBuilder = entry.getValue();
            ResourceLocation rl = idParser.apply(oldBuilder.getId());
            IItemBuilder newBuilder = newBuilders.get(rl);
            if (newBuilder != null) {
                BUILDER_BY_ITEM.put(item, newBuilder);
                if (item instanceof RebindableItem rebindable) {
                    rebindable.rebind(newBuilder);
                }
                rebound++;
            }
        }
        return rebound;
    }

    public static class CustomSwordItem extends SwordItem implements RebindableItem {
        private volatile IItemBuilder builder;
        public CustomSwordItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = builder;
        }
        @Override public void rebind(IItemBuilder newBuilder) { this.builder = newBuilder; }
        @Override public IItemBuilder getBuilder() { return this.builder; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return resolveUseAnim(builder.getUseAnimation()); }
        @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return resolveUseDuration(builder); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> r = handleItemUse(level, player, hand, builder);
            return r != null ? r : super.use(level, player, hand);
        }
        @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            handleUseTick(level, entity, stack, count, builder);
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            handleReleaseUsing(stack, level, entity, timeLeft, builder);
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            handleFinishUsing(stack, level, entity, builder);
            return super.finishUsingItem(stack, level, entity);
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            handleHurtEnemy(stack, target, attacker, builder);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity entity) {
            handleMineBlock(stack, level, state, pos, entity, builder);
            return super.mineBlock(stack, level, state, pos, entity);
        }
        @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult r = handleUseOnBlock(context, builder);
            return r != null ? r : super.useOn(context);
        }
    }

    public static class CustomPickaxeItem extends PickaxeItem implements RebindableItem {
        private volatile IItemBuilder builder;
        public CustomPickaxeItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = builder;
        }
        @Override public void rebind(IItemBuilder newBuilder) { this.builder = newBuilder; }
        @Override public IItemBuilder getBuilder() { return this.builder; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return resolveUseAnim(builder.getUseAnimation()); }
        @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return resolveUseDuration(builder); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> r = handleItemUse(level, player, hand, builder);
            return r != null ? r : super.use(level, player, hand);
        }
        @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            handleUseTick(level, entity, stack, count, builder);
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            handleReleaseUsing(stack, level, entity, timeLeft, builder);
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            handleFinishUsing(stack, level, entity, builder);
            return super.finishUsingItem(stack, level, entity);
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            handleHurtEnemy(stack, target, attacker, builder);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity entity) {
            handleMineBlock(stack, level, state, pos, entity, builder);
            return super.mineBlock(stack, level, state, pos, entity);
        }
        @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult r = handleUseOnBlock(context, builder);
            return r != null ? r : super.useOn(context);
        }
    }

    public static class CustomAxeItem extends AxeItem implements RebindableItem {
        private volatile IItemBuilder builder;
        public CustomAxeItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = builder;
        }
        @Override public void rebind(IItemBuilder newBuilder) { this.builder = newBuilder; }
        @Override public IItemBuilder getBuilder() { return this.builder; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return resolveUseAnim(builder.getUseAnimation()); }
        @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return resolveUseDuration(builder); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> r = handleItemUse(level, player, hand, builder);
            return r != null ? r : super.use(level, player, hand);
        }
        @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            handleUseTick(level, entity, stack, count, builder);
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            handleReleaseUsing(stack, level, entity, timeLeft, builder);
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            handleFinishUsing(stack, level, entity, builder);
            return super.finishUsingItem(stack, level, entity);
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            handleHurtEnemy(stack, target, attacker, builder);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity entity) {
            handleMineBlock(stack, level, state, pos, entity, builder);
            return super.mineBlock(stack, level, state, pos, entity);
        }
        @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult r = handleUseOnBlock(context, builder);
            return r != null ? r : super.useOn(context);
        }
    }

    public static class CustomShovelItem extends ShovelItem implements RebindableItem {
        private volatile IItemBuilder builder;
        public CustomShovelItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = builder;
        }
        @Override public void rebind(IItemBuilder newBuilder) { this.builder = newBuilder; }
        @Override public IItemBuilder getBuilder() { return this.builder; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return resolveUseAnim(builder.getUseAnimation()); }
        @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return resolveUseDuration(builder); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> r = handleItemUse(level, player, hand, builder);
            return r != null ? r : super.use(level, player, hand);
        }
        @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            handleUseTick(level, entity, stack, count, builder);
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            handleReleaseUsing(stack, level, entity, timeLeft, builder);
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            handleFinishUsing(stack, level, entity, builder);
            return super.finishUsingItem(stack, level, entity);
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            handleHurtEnemy(stack, target, attacker, builder);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity entity) {
            handleMineBlock(stack, level, state, pos, entity, builder);
            return super.mineBlock(stack, level, state, pos, entity);
        }
        @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult r = handleUseOnBlock(context, builder);
            return r != null ? r : super.useOn(context);
        }
    }

    public static class CustomHoeItem extends HoeItem implements RebindableItem {
        private volatile IItemBuilder builder;
        public CustomHoeItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = builder;
        }
        @Override public void rebind(IItemBuilder newBuilder) { this.builder = newBuilder; }
        @Override public IItemBuilder getBuilder() { return this.builder; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return resolveUseAnim(builder.getUseAnimation()); }
        @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return resolveUseDuration(builder); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> r = handleItemUse(level, player, hand, builder);
            return r != null ? r : super.use(level, player, hand);
        }
        @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            handleUseTick(level, entity, stack, count, builder);
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            handleReleaseUsing(stack, level, entity, timeLeft, builder);
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            handleFinishUsing(stack, level, entity, builder);
            return super.finishUsingItem(stack, level, entity);
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            handleHurtEnemy(stack, target, attacker, builder);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity entity) {
            handleMineBlock(stack, level, state, pos, entity, builder);
            return super.mineBlock(stack, level, state, pos, entity);
        }
        @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult r = handleUseOnBlock(context, builder);
            return r != null ? r : super.useOn(context);
        }
    }

    public static class CustomGenericItem extends Item implements RebindableItem {
        private volatile IItemBuilder builder;
        public CustomGenericItem(Properties props, IItemBuilder builder) {
            super(props);
            this.builder = builder;
        }
        @Override public void rebind(IItemBuilder newBuilder) { this.builder = newBuilder; }
        @Override public IItemBuilder getBuilder() { return this.builder; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return resolveUseAnim(builder.getUseAnimation()); }
        @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return resolveUseDuration(builder); }
        @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> r = handleItemUse(level, player, hand, builder);
            return r != null ? r : super.use(level, player, hand);
        }
        @Override public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            handleUseTick(level, entity, stack, count, builder);
        }
        @Override public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            handleReleaseUsing(stack, level, entity, timeLeft, builder);
        }
        @Override public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            handleFinishUsing(stack, level, entity, builder);
            return super.finishUsingItem(stack, level, entity);
        }
        @Override public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            handleHurtEnemy(stack, target, attacker, builder);
            return super.hurtEnemy(stack, target, attacker);
        }
        @Override public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity entity) {
            handleMineBlock(stack, level, state, pos, entity, builder);
            return super.mineBlock(stack, level, state, pos, entity);
        }
        @Override public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult r = handleUseOnBlock(context, builder);
            return r != null ? r : super.useOn(context);
        }
    }

    public static class CustomArmorItem extends ArmorItem implements RebindableItem {
        private volatile IItemBuilder builder;

        public CustomArmorItem(net.minecraft.core.Holder<ArmorMaterial> material, Type type, Properties properties, IItemBuilder builder) {
            super(material, type, properties);
            this.builder = builder;
        }

        @Override public void rebind(IItemBuilder newBuilder) { this.builder = newBuilder; }
        @Override public IItemBuilder getBuilder() { return this.builder; }
        @Override public UseAnim getUseAnimation(ItemStack stack) { return resolveUseAnim(builder.getUseAnimation()); }
        @Override public int getUseDuration(ItemStack stack, LivingEntity entity) { return resolveUseDuration(builder); }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player, net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> r = handleItemUse(level, player, hand, builder);
            return r != null ? r : super.use(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            handleUseTick(level, entity, stack, count, builder);
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            handleReleaseUsing(stack, level, entity, timeLeft, builder);
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            handleFinishUsing(stack, level, entity, builder);
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            handleHurtEnemy(stack, target, attacker, builder);
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level, net.minecraft.world.level.block.state.BlockState state, net.minecraft.core.BlockPos pos, LivingEntity entity) {
            handleMineBlock(stack, level, state, pos, entity, builder);
            return super.mineBlock(stack, level, state, pos, entity);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult result = handleUseOnBlock(context, builder);
            return result != null ? result : super.useOn(context);
        }

        @Override
        public ResourceLocation getArmorTexture(ItemStack stack, net.minecraft.world.entity.Entity entity, net.minecraft.world.entity.EquipmentSlot slot, ArmorMaterial.Layer layer, boolean inner) {
            String customTex = builder.getArmorTexture();
            if (customTex == null || customTex.isEmpty()) {
                customTex = builder.getArmorMaterial();
            }
            if (customTex != null && !customTex.isEmpty()) {
                String domain = DEFAULT_NAMESPACE;
                String name = customTex;
                if (customTex.contains(":")) {
                    String[] parts = customTex.split(":", 2);
                    domain = parts[0];
                    name = parts[1];
                }
                String layerSuffix = (slot == net.minecraft.world.entity.EquipmentSlot.LEGS || getType() == Type.LEGGINGS) ? "layer_2" : "layer_1";
                if (!name.startsWith("textures/")) {
                    name = ARMOR_TEXTURE_DIR + name + "_" + layerSuffix + ".png";
                }
                return ResourceLocation.fromNamespaceAndPath(domain, name);
            }
            return super.getArmorTexture(stack, entity, slot, layer, inner);
        }
    }
}
