package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import com.luatweaker.api.doc.LuaDocFragments;
import com.luatweaker.integration.geo.GeoModelSpec;
import java.util.function.BiConsumer;

@LuaDoc(description = "Builder interface for defining custom Item, Tool, Weapon, and Armor properties.")
public interface IItemBuilder {
    @LuaDoc(description = "Sets the item type ('GENERIC', 'SWORD', 'PICKAXE', 'AXE', 'SHOVEL', 'HOE', 'HELMET', 'CHESTPLATE', 'LEGGINGS', 'BOOTS').", params = {"type: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder type(String type);

    @LuaDoc(description = "Sets the maximum stack size.", params = {"size: integer"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder maxStackSize(int size);

    @LuaDoc(description = "Sets the rarity ('COMMON', 'UNCOMMON', 'RARE', 'EPIC').", params = {"rarity: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder rarity(String rarity);

    @LuaDoc(description = "Sets the maximum durability / damage of tools and armor.", params = {"durability: integer"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder durability(int durability);

    @LuaDoc(description = "Sets the tool mining tier (0=WOOD, 1=STONE, 2=IRON, 3=DIAMOND, 4+=NETHERITE) or 'GOLD' for gold-tier stats (negative values throw).", params = {"level: integer"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder miningLevel(int level);
    @LuaDoc(description = "Sets the tool mining tier by name ('WOOD', 'STONE', 'IRON', 'DIAMOND', 'NETHERITE' or 'GOLD' for gold-tier stats; unknown names warn and fall back to STONE).", params = {"levelName: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder miningLevel(String levelName);

    @LuaDoc(description = "Sets the tool mining speed / efficiency multiplier (e.g. 6.0 for iron, 8.0 for diamond).", params = {"speed: number"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder miningSpeed(float speed);

    @LuaDoc(description = "Sets the attack damage bonus for weapons and tools.", params = {"damage: number"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder attackDamage(float damage);

    @LuaDoc(description = "Sets the attack speed for weapons and tools (e.g. -2.4). Only applies when attack damage is also set above 0.", params = {"speed: number"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder attackSpeed(float speed);

    @LuaDoc(description = "Sets the armor defense / protection value. Armor only: has no effect without a matching startup:createArmorMaterial definition (a warning is logged).", params = {"defense: integer"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder defense(int defense);

    @LuaDoc(description = "Sets armor toughness value. Armor only: has no effect without a matching startup:createArmorMaterial definition.", params = {"toughness: number"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder toughness(float toughness);

    @LuaDoc(description = "Sets knockback resistance bonus (0.0 to 1.0). Armor only: has no effect without a matching startup:createArmorMaterial definition.", params = {"kbRes: number"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder knockbackResistance(float kbRes);

    @LuaDoc(description = "Sets enchantability value (tools use their tier, armor uses its material; generic items fall back to vanilla 1 when unset).", params = {"enchValue: integer"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder enchantability(int enchValue);

    @LuaDoc(description = "Sets furnace fuel burn time in ticks.", params = {"ticks: integer"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder burnTime(int ticks);

    @LuaDoc(description = "Sets display name.", params = {"name: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder displayName(String name);

    @LuaDoc(description = "Sets custom item model location (e.g. 'luatweaker:item/magic_staff').", params = {"modelPath: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder model(String modelPath);

    @LuaDoc(description = "Sets custom texture path.", params = {"texturePath: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder texture(String texturePath);

    @LuaDoc(description = "Adds a tag to this item (e.g. 'c:gems/ruby' or 'minecraft:beacon_payment_items').", params = {"tagId: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder tag(String tagId);

    @LuaDoc(description = "Sets the target creative mode tab for this item (e.g. 'luatweaker:magic_tab' or 'minecraft:combat').", params = {"tabId: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder creativeTab(String tabId);

    @LuaDoc(description = "Sets explicit custom armor material ID (e.g. 'ruby').", params = {"materialId: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder armorMaterial(String materialId);

    @LuaDoc(description = "Sets explicit custom armor 3D texture layer (e.g. 'luatweaker:ruby').", params = {"texturePath: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder armorTexture(String texturePath);

    @LuaDoc(description = "Registers a right-click action handler callback.", params = {LuaDocFragments.Content.HANDLER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onRightClick(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Registers a callback when the player right-clicks this item ON a block. handler(player, hit) receives a table {X, Y, Z, Face} of the clicked face; return true to consume the click (block interaction is skipped), false to let the block handle it. This is how Lua-defined tools (wrenches, hammers, wands) are built.", params = {"handler: function(player, hit) -> boolean"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onUseOnBlock(java.util.function.BiFunction<Object, Object, Boolean> handler);

    @LuaDoc(description = "Registers a callback when a ranged projectile shot by this item hits an entity.", params = {"handler: function(targetEntity, shooterPlayer)"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onHitEntity(BiConsumer<Object, Object> handler);



    @LuaDoc(description = "Configures food properties (hunger points, saturation modifier).", params = {"hunger: integer", "saturation: number"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder food(int hunger, float saturation);

    @LuaDoc(description = "Allows eating this food item even when full.", returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder alwaysEdible();

    @LuaDoc(description = "Registers a callback executed when player finishes eating/consuming this item.", params = {LuaDocFragments.Content.HANDLER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onConsume(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Enables glowing / enchantment foil effect on this item.", params = {"enable: boolean"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder glow(boolean enable);

    @LuaDoc(description = "Adds a custom tooltip line under the item name.", params = {"text: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder tooltip(String text);

    @LuaDoc(description = "Sets the item use animation ('NONE', 'EAT', 'DRINK', 'BLOCK', 'BOW', 'SPEAR', 'CROSSBOW', 'SPYGLASS', 'TOOT_HORN', 'BRUSH').", params = {"animation: string"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder useAnimation(String animation);

    @LuaDoc(description = "Sets the maximum use/charge duration in ticks (e.g. 72000 for bow/shield, 30 for charged wand).", params = {"ticks: integer"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder useDuration(int ticks);

    @LuaDoc(description = "Registers a callback invoked every tick while holding right-click to use/charge this item.", params = {"handler: function(player, itemStack, ticksRemaining)"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onUseTick(ItemUseTickHandler handler);

    @LuaDoc(description = "Registers a callback invoked when the player releases right-click after charging.", params = {"handler: function(player, itemStack, timeLeft)"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onReleaseUsing(ItemReleaseHandler handler);

    @LuaDoc(description = "Registers a callback invoked when the full use duration completes without releasing.", params = {LuaDocFragments.Content.HANDLER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onFinishUsing(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Registers a callback invoked when attacking/hitting an entity with this item.", params = {"handler: function(targetEntity, attackerPlayer, itemStack)"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onHurtEnemy(ItemHurtHandler handler);

    @LuaDoc(description = "Registers a callback invoked when breaking a block with this item.", params = {"handler: function(player, itemStack, x, y, z)"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onMineBlock(ItemMineHandler handler);

    @LuaDoc(description = "Registers a callback invoked when player swings/left-clicks with this item.", params = {LuaDocFragments.Content.HANDLER_FUNCTION}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder onSwing(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Sets the GeckoLib skeletal model and animation specs for this item. Only the model path is required; texture/animation derive from it when omitted.", params = {"modelPath: string", "[texturePath: string]", "[animationPath: string]", "[defaultAnimation: string]"}, returnType = LuaDocFragments.Builder.I_ITEM_BUILDER)
    IItemBuilder geoModel(String modelPath, @org.jetbrains.annotations.Nullable String texturePath, @org.jetbrains.annotations.Nullable String animationPath, @org.jetbrains.annotations.Nullable String defaultAnimation);

    default IItemBuilder geoModel(String modelPath) {
        return geoModel(modelPath, null, null, null);
    }

    default IItemBuilder geoModel(String modelPath, String texturePath, String animationPath) {
        return geoModel(modelPath, texturePath, animationPath, null);
    }

    String getId();
    String getType();
    String getMiningLevelName();
    int getMaxStackSize();
    String getRarity();
    int getDurability();
    int getMiningLevel();
    float getMiningSpeed();
    float getAttackDamage();
    float getAttackSpeed();
    int getDefense();
    float getToughness();
    float getKnockbackResistance();
    int getEnchantability();
    int getBurnTime();
    String getDisplayName();
    String getModel();
    String getTexture();
    String getArmorMaterial();
    String getArmorTexture();
    java.util.List<String> getTags();
    String getCreativeTab();
    BiConsumer<Object, Object> getRightClickHandler();
    java.util.function.BiFunction<Object, Object, Boolean> getOnUseOnBlockHandler();
    BiConsumer<Object, Object> getOnHitEntityHandler();

    int getFoodHunger();
    float getFoodSaturation();
    boolean isAlwaysEdible();
    BiConsumer<Object, Object> getOnConsumeHandler();
    boolean isGlow();
    java.util.List<String> getTooltips();

    String getUseAnimation();
    int getUseDuration();
    ItemUseTickHandler getOnUseTickHandler();
    ItemReleaseHandler getOnReleaseUsingHandler();
    BiConsumer<Object, Object> getOnFinishUsingHandler();
    ItemHurtHandler getOnHurtEnemyHandler();
    ItemMineHandler getOnMineBlockHandler();
    BiConsumer<Object, Object> getOnSwingHandler();
    @org.jetbrains.annotations.Nullable
    GeoModelSpec getGeoModel();
}





