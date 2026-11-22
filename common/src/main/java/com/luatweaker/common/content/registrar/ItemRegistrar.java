package com.luatweaker.common.content.registrar;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.util.CallbackFirewall;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Registers Lua-declared items and rebinds their builders on {@code /lt reload}.
 *
 * <p>Item classes keep only identity + builder plumbing; every Lua callback is dispatched by
 * {@link ItemHooks}, every field resolution by {@link ItemResolvers}, armor materials by
 * {@link ArmorMaterialFactory}.
 */
public final class ItemRegistrar {
    private static final String TYPE_SIMPLE = "SIMPLE";
    private static final String TYPE_SWORD = "SWORD";
    private static final String TYPE_PICKAXE = "PICKAXE";
    private static final String TYPE_AXE = "AXE";
    private static final String TYPE_SHOVEL = "SHOVEL";
    private static final String TYPE_HOE = "HOE";
    private static final float NO_ATTACK_SPEED = 0.0f;

    /**
     * Identity map (Item does not override equals/hashCode). ConcurrentHashMap keeps the same
     * identity semantics while allowing the reload thread to read it during registration.
     */
    public static final Map<Item, IItemBuilder> BUILDER_BY_ITEM = new ConcurrentHashMap<>();

    private final IContentService contentService;
    private final Map<ResourceLocation, Item> createdItems;
    private final Function<String, ResourceLocation> locationParser;
    private final ArmorMaterialFactory armorMaterials;

    public ItemRegistrar(IContentService contentService, Map<ResourceLocation, Item> createdItems,
                         Function<String, ResourceLocation> locationParser) {
        this.contentService = contentService;
        this.createdItems = createdItems;
        this.locationParser = locationParser;
        this.armorMaterials = new ArmorMaterialFactory(contentService);
    }

    public void registerItems() {
        dev.architectury.registry.registries.Registrar<Item> registrar =
                dev.architectury.registry.registries.RegistrarManager.get("luatweaker").get(Registries.ITEM);
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            CallbackFirewall.run(() -> registerOne(registrar, builder), LogStage.SYSTEM,
                    () -> "Failed to register item " + builder.getId());
        }
    }

    private void registerOne(dev.architectury.registry.registries.Registrar<Item> registrar, IItemBuilder builder) {
        ResourceLocation id = locationParser.apply(builder.getId());
        // Object creation touches self-registering vanilla holders
        // (DataComponents, SoundEvents, ...), so it must run inside the
        // deferred supplier when the item registry is unfrozen - never here
        // at mod-construction time.
        registrar.register(id, () -> {
            Item item = createItem(builder, buildProperties(builder));
            createdItems.put(id, item);
            BUILDER_BY_ITEM.put(item, builder);
            if (builder.getBurnTime() > 0) {
                dev.architectury.registry.fuel.FuelRegistry.register(builder.getBurnTime(), item);
            }
            LuaTweakerLog.get().info(LogStage.SYSTEM, "Registered Custom Item: " + id);
            return item;
        });
    }

    private static Item.Properties buildProperties(IItemBuilder builder) {
        var props = new Item.Properties();
        if (builder.getMaxStackSize() > 0) {
            props.stacksTo(builder.getMaxStackSize());
        }
        if (builder.getDurability() > 0) {
            props.durability(builder.getDurability());
        }
        if (builder.getFoodHunger() > 0) {
            var foodBuilder = new net.minecraft.world.food.FoodProperties.Builder()
                    .nutrition(builder.getFoodHunger())
                    .saturationModifier(builder.getFoodSaturation());
            if (builder.isAlwaysEdible()) {
                foodBuilder.alwaysEdible();
            }
            props.food(foodBuilder.build());
        }
        props.rarity(ItemResolvers.resolveRarity(builder));
        Tier tier = ItemResolvers.resolveTier(builder);
        float damage = builder.getAttackDamage();
        float speed = builder.getAttackSpeed() == NO_ATTACK_SPEED
                ? ItemResolvers.DEFAULT_ATTACK_SPEED
                : builder.getAttackSpeed();
        if (damage > 0) {
            float baseBonus = tier.getAttackDamageBonus();
            props.attributes(SwordItem.createAttributes(tier, Math.round(Math.max(-baseBonus, damage - baseBonus)), speed));
        }
        return props;
    }

    private Item createItem(IItemBuilder builder, Item.Properties props) {
        if (builder.getDefense() != 0 || builder.getToughness() != 0 || builder.getKnockbackResistance() != 0) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Item '" + builder.getId() + "' sets defense/toughness/knockbackResistance, which have no effect "
                    + "on their own: define armor through startup:createArmorMaterial and link it with armorMaterial().");
        }
        if (builder.getGeoModel() != null) {
            return new com.luatweaker.common.integration.geckolib.LuaGeoItem(props, builder, builder.getGeoModel());
        }
        String type = builder.getType() != null ? builder.getType().toUpperCase(Locale.ROOT) : TYPE_SIMPLE;
        ArmorItem.Type armorType = armorType(type);
        if (armorType != null) {
            return new CustomArmorItem(armorMaterials.resolve(builder), armorType, props, builder);
        }
        return createToolOrGenericItem(type, builder, props);
    }

    private Item createToolOrGenericItem(String type, IItemBuilder builder, Item.Properties props) {
        return switch (type) {
            case TYPE_SWORD -> new CustomSwordItem(ItemResolvers.resolveTier(builder), props, builder);
            case TYPE_PICKAXE -> new CustomPickaxeItem(ItemResolvers.resolveTier(builder), props, builder);
            case TYPE_AXE -> new CustomAxeItem(ItemResolvers.resolveTier(builder), props, builder);
            case TYPE_SHOVEL -> new CustomShovelItem(ItemResolvers.resolveTier(builder), props, builder);
            case TYPE_HOE -> new CustomHoeItem(ItemResolvers.resolveTier(builder), props, builder);
            default -> new CustomGenericItem(props, builder);
        };
    }

    private static ArmorItem.Type armorType(String type) {
        return switch (type) {
            case "HELMET" -> ArmorItem.Type.HELMET;
            case "CHESTPLATE" -> ArmorItem.Type.CHESTPLATE;
            case "LEGGINGS" -> ArmorItem.Type.LEGGINGS;
            case "BOOTS" -> ArmorItem.Type.BOOTS;
            default -> null;
        };
    }

    public static void handleSwing(Player player, ItemStack stack) {
        ItemHooks.handleSwing(player, stack);
    }

    public interface RebindableItem {
        void rebind(IItemBuilder newBuilder);

        IItemBuilder getBuilder();
    }

    public static int rebindAll(IContentService contentService, Function<String, ResourceLocation> idParser) {
        if (contentService == null || idParser == null) {
            return 0;
        }
        var newBuilders = new HashMap<ResourceLocation, IItemBuilder>();
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            newBuilders.put(idParser.apply(builder.getId()), builder);
        }
        int rebound = 0;
        for (Map.Entry<Item, IItemBuilder> entry : new java.util.ArrayList<>(BUILDER_BY_ITEM.entrySet())) {
            Item item = entry.getKey();
            IItemBuilder newBuilder = newBuilders.get(idParser.apply(entry.getValue().getId()));
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
        private final AtomicReference<IItemBuilder> builder;

        public CustomSwordItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = new AtomicReference<>(builder);
        }

        @Override
        public void rebind(IItemBuilder newBuilder) {
            builder.set(newBuilder);
        }

        @Override
        public IItemBuilder getBuilder() {
            return builder.get();
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return ItemResolvers.resolveUseAnim(builder.get());
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return ItemResolvers.resolveUseDuration(builder.get());
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return builder.get().isGlow();
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            ItemResolvers.appendBuilderTooltips(lines, builder.get());
            super.appendHoverText(stack, context, lines, flag);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                      net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> result = ItemHooks.handleItemUse(level, player, hand, builder.get());
            return result != null ? result : super.use(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            ItemHooks.handleUseTick(level, entity, stack, count, builder.get());
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            ItemHooks.handleReleaseUsing(stack, level, entity, timeLeft, builder.get());
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemHooks.handleFinishUsing(stack, level, entity, builder.get());
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            ItemHooks.handleHurtEnemy(stack, target, attacker, builder.get());
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.core.BlockPos pos, LivingEntity entity) {
            ItemHooks.handleMineBlock(stack, level, state, pos, entity, builder.get());
            return super.mineBlock(stack, level, state, pos, entity);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult result = ItemHooks.handleUseOnBlock(context, builder.get());
            return result != null ? result : super.useOn(context);
        }
    }

    public static class CustomPickaxeItem extends PickaxeItem implements RebindableItem {
        private final AtomicReference<IItemBuilder> builder;

        public CustomPickaxeItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = new AtomicReference<>(builder);
        }

        @Override
        public void rebind(IItemBuilder newBuilder) {
            builder.set(newBuilder);
        }

        @Override
        public IItemBuilder getBuilder() {
            return builder.get();
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return ItemResolvers.resolveUseAnim(builder.get());
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return ItemResolvers.resolveUseDuration(builder.get());
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return builder.get().isGlow();
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            ItemResolvers.appendBuilderTooltips(lines, builder.get());
            super.appendHoverText(stack, context, lines, flag);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                      net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> result = ItemHooks.handleItemUse(level, player, hand, builder.get());
            return result != null ? result : super.use(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            ItemHooks.handleUseTick(level, entity, stack, count, builder.get());
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            ItemHooks.handleReleaseUsing(stack, level, entity, timeLeft, builder.get());
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemHooks.handleFinishUsing(stack, level, entity, builder.get());
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            ItemHooks.handleHurtEnemy(stack, target, attacker, builder.get());
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.core.BlockPos pos, LivingEntity entity) {
            ItemHooks.handleMineBlock(stack, level, state, pos, entity, builder.get());
            return super.mineBlock(stack, level, state, pos, entity);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult result = ItemHooks.handleUseOnBlock(context, builder.get());
            return result != null ? result : super.useOn(context);
        }
    }

    public static class CustomAxeItem extends AxeItem implements RebindableItem {
        private final AtomicReference<IItemBuilder> builder;

        public CustomAxeItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = new AtomicReference<>(builder);
        }

        @Override
        public void rebind(IItemBuilder newBuilder) {
            builder.set(newBuilder);
        }

        @Override
        public IItemBuilder getBuilder() {
            return builder.get();
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return ItemResolvers.resolveUseAnim(builder.get());
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return ItemResolvers.resolveUseDuration(builder.get());
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return builder.get().isGlow();
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            ItemResolvers.appendBuilderTooltips(lines, builder.get());
            super.appendHoverText(stack, context, lines, flag);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                      net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> result = ItemHooks.handleItemUse(level, player, hand, builder.get());
            return result != null ? result : super.use(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            ItemHooks.handleUseTick(level, entity, stack, count, builder.get());
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            ItemHooks.handleReleaseUsing(stack, level, entity, timeLeft, builder.get());
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemHooks.handleFinishUsing(stack, level, entity, builder.get());
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            ItemHooks.handleHurtEnemy(stack, target, attacker, builder.get());
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.core.BlockPos pos, LivingEntity entity) {
            ItemHooks.handleMineBlock(stack, level, state, pos, entity, builder.get());
            return super.mineBlock(stack, level, state, pos, entity);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult result = ItemHooks.handleUseOnBlock(context, builder.get());
            return result != null ? result : super.useOn(context);
        }
    }

    public static class CustomShovelItem extends ShovelItem implements RebindableItem {
        private final AtomicReference<IItemBuilder> builder;

        public CustomShovelItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = new AtomicReference<>(builder);
        }

        @Override
        public void rebind(IItemBuilder newBuilder) {
            builder.set(newBuilder);
        }

        @Override
        public IItemBuilder getBuilder() {
            return builder.get();
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return ItemResolvers.resolveUseAnim(builder.get());
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return ItemResolvers.resolveUseDuration(builder.get());
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return builder.get().isGlow();
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            ItemResolvers.appendBuilderTooltips(lines, builder.get());
            super.appendHoverText(stack, context, lines, flag);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                      net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> result = ItemHooks.handleItemUse(level, player, hand, builder.get());
            return result != null ? result : super.use(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            ItemHooks.handleUseTick(level, entity, stack, count, builder.get());
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            ItemHooks.handleReleaseUsing(stack, level, entity, timeLeft, builder.get());
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemHooks.handleFinishUsing(stack, level, entity, builder.get());
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            ItemHooks.handleHurtEnemy(stack, target, attacker, builder.get());
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.core.BlockPos pos, LivingEntity entity) {
            ItemHooks.handleMineBlock(stack, level, state, pos, entity, builder.get());
            return super.mineBlock(stack, level, state, pos, entity);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult result = ItemHooks.handleUseOnBlock(context, builder.get());
            return result != null ? result : super.useOn(context);
        }
    }

    public static class CustomHoeItem extends HoeItem implements RebindableItem {
        private final AtomicReference<IItemBuilder> builder;

        public CustomHoeItem(Tier tier, Properties props, IItemBuilder builder) {
            super(tier, props);
            this.builder = new AtomicReference<>(builder);
        }

        @Override
        public void rebind(IItemBuilder newBuilder) {
            builder.set(newBuilder);
        }

        @Override
        public IItemBuilder getBuilder() {
            return builder.get();
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return ItemResolvers.resolveUseAnim(builder.get());
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return ItemResolvers.resolveUseDuration(builder.get());
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return builder.get().isGlow();
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            ItemResolvers.appendBuilderTooltips(lines, builder.get());
            super.appendHoverText(stack, context, lines, flag);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                      net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> result = ItemHooks.handleItemUse(level, player, hand, builder.get());
            return result != null ? result : super.use(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            ItemHooks.handleUseTick(level, entity, stack, count, builder.get());
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            ItemHooks.handleReleaseUsing(stack, level, entity, timeLeft, builder.get());
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemHooks.handleFinishUsing(stack, level, entity, builder.get());
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            ItemHooks.handleHurtEnemy(stack, target, attacker, builder.get());
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.core.BlockPos pos, LivingEntity entity) {
            ItemHooks.handleMineBlock(stack, level, state, pos, entity, builder.get());
            return super.mineBlock(stack, level, state, pos, entity);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult result = ItemHooks.handleUseOnBlock(context, builder.get());
            return result != null ? result : super.useOn(context);
        }
    }

    public static class CustomGenericItem extends Item implements RebindableItem {
        private final AtomicReference<IItemBuilder> builder;

        public CustomGenericItem(Properties props, IItemBuilder builder) {
            super(props);
            this.builder = new AtomicReference<>(builder);
        }

        @Override
        public int getEnchantmentValue() {
            return ItemResolvers.resolveItemEnchantability(builder.get());
        }

        @Override
        public void rebind(IItemBuilder newBuilder) {
            builder.set(newBuilder);
        }

        @Override
        public IItemBuilder getBuilder() {
            return builder.get();
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return ItemResolvers.resolveUseAnim(builder.get());
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return ItemResolvers.resolveUseDuration(builder.get());
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return builder.get().isGlow();
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            ItemResolvers.appendBuilderTooltips(lines, builder.get());
            super.appendHoverText(stack, context, lines, flag);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                      net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> result = ItemHooks.handleItemUse(level, player, hand, builder.get());
            return result != null ? result : super.use(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            ItemHooks.handleUseTick(level, entity, stack, count, builder.get());
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            ItemHooks.handleReleaseUsing(stack, level, entity, timeLeft, builder.get());
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemHooks.handleFinishUsing(stack, level, entity, builder.get());
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            ItemHooks.handleHurtEnemy(stack, target, attacker, builder.get());
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.core.BlockPos pos, LivingEntity entity) {
            ItemHooks.handleMineBlock(stack, level, state, pos, entity, builder.get());
            return super.mineBlock(stack, level, state, pos, entity);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult result = ItemHooks.handleUseOnBlock(context, builder.get());
            return result != null ? result : super.useOn(context);
        }
    }

    public static class CustomArmorItem extends ArmorItem implements RebindableItem {
        private final AtomicReference<IItemBuilder> builder;

        public CustomArmorItem(@NotNull net.minecraft.core.Holder<ArmorMaterial> material, Type type,
                               Properties properties, IItemBuilder builder) {
            super(material, type, properties);
            this.builder = new AtomicReference<>(builder);
        }

        @Override
        public void rebind(IItemBuilder newBuilder) {
            builder.set(newBuilder);
        }

        @Override
        public IItemBuilder getBuilder() {
            return builder.get();
        }

        @Override
        public UseAnim getUseAnimation(ItemStack stack) {
            return ItemResolvers.resolveUseAnim(builder.get());
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return ItemResolvers.resolveUseDuration(builder.get());
        }

        @Override
        public boolean isFoil(ItemStack stack) {
            return builder.get().isGlow();
        }

        @Override
        public void appendHoverText(ItemStack stack, Item.TooltipContext context,
                                    List<Component> lines, TooltipFlag flag) {
            ItemResolvers.appendBuilderTooltips(lines, builder.get());
            super.appendHoverText(stack, context, lines, flag);
        }

        @Override
        public InteractionResultHolder<ItemStack> use(Level level, Player player,
                                                      net.minecraft.world.InteractionHand hand) {
            InteractionResultHolder<ItemStack> result = ItemHooks.handleItemUse(level, player, hand, builder.get());
            return result != null ? result : super.use(level, player, hand);
        }

        @Override
        public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
            ItemHooks.handleUseTick(level, entity, stack, count, builder.get());
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
            ItemHooks.handleReleaseUsing(stack, level, entity, timeLeft, builder.get());
        }

        @Override
        public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
            ItemHooks.handleFinishUsing(stack, level, entity, builder.get());
            return super.finishUsingItem(stack, level, entity);
        }

        @Override
        public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
            ItemHooks.handleHurtEnemy(stack, target, attacker, builder.get());
            return super.hurtEnemy(stack, target, attacker);
        }

        @Override
        public boolean mineBlock(ItemStack stack, Level level,
                                 net.minecraft.world.level.block.state.BlockState state,
                                 net.minecraft.core.BlockPos pos, LivingEntity entity) {
            ItemHooks.handleMineBlock(stack, level, state, pos, entity, builder.get());
            return super.mineBlock(stack, level, state, pos, entity);
        }

        @Override
        public InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
            InteractionResult result = ItemHooks.handleUseOnBlock(context, builder.get());
            return result != null ? result : super.useOn(context);
        }
    }
}

