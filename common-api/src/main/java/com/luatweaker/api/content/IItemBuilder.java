package com.luatweaker.api.content;

import com.luatweaker.api.annotation.LuaDoc;
import java.util.function.BiConsumer;

@LuaDoc(description = "Builder interface for defining custom Item, Tool, Weapon, and Armor properties.")
public interface IItemBuilder {
    @LuaDoc(description = "Sets the item type ('GENERIC', 'SWORD', 'PICKAXE', 'AXE', 'SHOVEL', 'HOE', 'HELMET', 'CHESTPLATE', 'LEGGINGS', 'BOOTS').", params = {"type: string"}, returnType = "IItemBuilder")
    IItemBuilder type(String type);

    @LuaDoc(description = "Sets the maximum stack size.", params = {"size: integer"}, returnType = "IItemBuilder")
    IItemBuilder maxStackSize(int size);

    @LuaDoc(description = "Sets the rarity ('COMMON', 'UNCOMMON', 'RARE', 'EPIC').", params = {"rarity: string"}, returnType = "IItemBuilder")
    IItemBuilder rarity(String rarity);

    @LuaDoc(description = "Sets the maximum durability / damage of tools and armor.", params = {"durability: integer"}, returnType = "IItemBuilder")
    IItemBuilder durability(int durability);

    @LuaDoc(description = "Sets the tool mining tier level (1='STONE', 2='IRON', 3='DIAMOND', 4='NETHERITE').", params = {"level: string|integer"}, returnType = "IItemBuilder")
    IItemBuilder miningLevel(int level);
    IItemBuilder miningLevel(String levelName);

    @LuaDoc(description = "Sets the tool mining speed / efficiency multiplier (e.g. 6.0 for iron, 8.0 for diamond).", params = {"speed: number"}, returnType = "IItemBuilder")
    IItemBuilder miningSpeed(float speed);

    @LuaDoc(description = "Sets the attack damage bonus for weapons and tools.", params = {"damage: number"}, returnType = "IItemBuilder")
    IItemBuilder attackDamage(float damage);

    @LuaDoc(description = "Sets the attack speed for weapons and tools (e.g. -2.4).", params = {"speed: number"}, returnType = "IItemBuilder")
    IItemBuilder attackSpeed(float speed);

    @LuaDoc(description = "Sets the armor defense / protection value.", params = {"defense: integer"}, returnType = "IItemBuilder")
    IItemBuilder defense(int defense);

    @LuaDoc(description = "Sets armor toughness value.", params = {"toughness: number"}, returnType = "IItemBuilder")
    IItemBuilder toughness(float toughness);

    @LuaDoc(description = "Sets knockback resistance bonus (0.0 to 1.0).", params = {"kbRes: number"}, returnType = "IItemBuilder")
    IItemBuilder knockbackResistance(float kbRes);

    @LuaDoc(description = "Sets enchantability value.", params = {"enchValue: integer"}, returnType = "IItemBuilder")
    IItemBuilder enchantability(int enchValue);

    @LuaDoc(description = "Sets furnace fuel burn time in ticks.", params = {"ticks: integer"}, returnType = "IItemBuilder")
    IItemBuilder burnTime(int ticks);

    @LuaDoc(description = "Sets display name.", params = {"name: string"}, returnType = "IItemBuilder")
    IItemBuilder displayName(String name);

    @LuaDoc(description = "Sets custom item model location (e.g. 'luatweaker:item/magic_staff').", params = {"modelPath: string"}, returnType = "IItemBuilder")
    IItemBuilder model(String modelPath);

    @LuaDoc(description = "Sets custom texture path.", params = {"texturePath: string"}, returnType = "IItemBuilder")
    IItemBuilder texture(String texturePath);

    @LuaDoc(description = "Adds a tag to this item (e.g. 'c:gems/ruby' or 'minecraft:beacon_payment_items').", params = {"tagId: string"}, returnType = "IItemBuilder")
    IItemBuilder tag(String tagId);

    @LuaDoc(description = "Sets the target creative mode tab for this item (e.g. 'luatweaker:magic_tab' or 'minecraft:combat').", params = {"tabId: string"}, returnType = "IItemBuilder")
    IItemBuilder creativeTab(String tabId);

    @LuaDoc(description = "Sets explicit custom armor material ID (e.g. 'ruby').", params = {"materialId: string"}, returnType = "IItemBuilder")
    IItemBuilder armorMaterial(String materialId);

    @LuaDoc(description = "Sets explicit custom armor 3D texture layer (e.g. 'luatweaker:ruby').", params = {"texturePath: string"}, returnType = "IItemBuilder")
    IItemBuilder armorTexture(String texturePath);

    @LuaDoc(description = "Registers a right-click action handler callback.", params = {"handler: function(player, itemStack)"}, returnType = "IItemBuilder")
    IItemBuilder onRightClick(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Registers a callback when a ranged projectile shot by this item hits an entity.", params = {"handler: function(targetEntity, shooterPlayer)"}, returnType = "IItemBuilder")
    IItemBuilder onHitEntity(BiConsumer<Object, Object> handler);



    @LuaDoc(description = "Configures food properties (hunger points, saturation modifier).", params = {"hunger: integer", "saturation: number"}, returnType = "IItemBuilder")
    IItemBuilder food(int hunger, float saturation);

    @LuaDoc(description = "Allows eating this food item even when full.", returnType = "IItemBuilder")
    IItemBuilder alwaysEdible();

    @LuaDoc(description = "Registers a callback executed when player finishes eating/consuming this item.", params = {"handler: function(player, itemStack)"}, returnType = "IItemBuilder")
    IItemBuilder onConsume(BiConsumer<Object, Object> handler);

    @LuaDoc(description = "Enables glowing / enchantment foil effect on this item.", params = {"enable: boolean"}, returnType = "IItemBuilder")
    IItemBuilder glow(boolean enable);

    @LuaDoc(description = "Adds a custom tooltip line under the item name.", params = {"text: string"}, returnType = "IItemBuilder")
    IItemBuilder tooltip(String text);


    String getId();
    String getType();
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
    BiConsumer<Object, Object> getOnHitEntityHandler();

    int getFoodHunger();
    float getFoodSaturation();
    boolean isAlwaysEdible();
    BiConsumer<Object, Object> getOnConsumeHandler();
    boolean isGlow();
    java.util.List<String> getTooltips();
}





