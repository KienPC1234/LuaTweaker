package com.luatweaker.platform.content.registrar;

import com.luatweaker.api.content.IArmorMaterialBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Builds NeoForge {@link ArmorMaterial} holders from Lua armor-material declarations. */
final class ArmorMaterialFactory {
    /** Default namespace for armor textures when the Lua builder omits one. */
    private static final String DEFAULT_NAMESPACE = "luatweaker";

    private final IContentService contentService;
    private final Map<String, Holder<ArmorMaterial>> cache = new ConcurrentHashMap<>();

    ArmorMaterialFactory(IContentService contentService) {
        this.contentService = contentService;
    }

    /**
     * Resolves the armor material declared by {@code item:armorMaterial("ruby")}. Falls back to
     * diamond (with a loud warning) when the Lua material was never registered, instead of
     * silently registering diamond stats under a custom name.
     */
    @NotNull
    Holder<ArmorMaterial> resolve(@NotNull IItemBuilder builder) {
        String requested = builder.getArmorMaterial();
        if (requested == null || requested.isBlank()) {
            return ArmorMaterials.DIAMOND;
        }
        Holder<ArmorMaterial> cached = cache.get(requested);
        if (cached != null) {
            return cached;
        }
        IArmorMaterialBuilder definition = findDefinition(requested);
        if (definition == null) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Item '" + builder.getId() + "' requests armor material '" + requested
                            + "' which startup:createArmorMaterial never defined; falling back to diamond");
            return ArmorMaterials.DIAMOND;
        }
        Holder<ArmorMaterial> material = build(definition);
        cache.put(requested, material);
        return material;
    }

    @Nullable
    private IArmorMaterialBuilder findDefinition(String requested) {
        IArmorMaterialBuilder definition = contentService.getArmorMaterial(requested);
        if (definition == null && !requested.contains(":")) {
            definition = contentService.getArmorMaterial(DEFAULT_NAMESPACE + ":" + requested);
        }
        return definition;
    }

    @NotNull
    private static Holder<ArmorMaterial> build(@NotNull IArmorMaterialBuilder definition) {
        var defense = new EnumMap<ArmorItem.Type, Integer>(ArmorItem.Type.class);
        defense.put(ArmorItem.Type.HELMET, definition.getDefense("helmet"));
        defense.put(ArmorItem.Type.CHESTPLATE, definition.getDefense("chestplate"));
        defense.put(ArmorItem.Type.LEGGINGS, definition.getDefense("leggings"));
        defense.put(ArmorItem.Type.BOOTS, definition.getDefense("boots"));
        ResourceLocation layerId = layerId(definition);
        var material = new ArmorMaterial(
                defense,
                definition.getEnchantability(),
                equipSound(definition.getEquipSound()),
                () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(layerId)),
                definition.getToughness(),
                definition.getKnockbackResistance());
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Registered custom armor material '" + definition.getId() + "' (layer " + layerId
                        + ", toughness " + definition.getToughness() + ")");
        return Holder.direct(material);
    }

    @NotNull
    private static ResourceLocation layerId(IArmorMaterialBuilder definition) {
        ResourceLocation parsed = ResourceLocation.tryParse(
                definition.getLayer() != null ? definition.getLayer() : DEFAULT_NAMESPACE + ":" + definition.getId());
        return parsed != null
                ? parsed
                : ResourceLocation.fromNamespaceAndPath(DEFAULT_NAMESPACE,
                        definition.getId().toLowerCase(Locale.ROOT));
    }

    @NotNull
    private static Holder<SoundEvent> equipSound(String soundId) {
        ResourceLocation id = ResourceLocation.tryParse(soundId);
        if (id == null) {
            return SoundEvents.ARMOR_EQUIP_DIAMOND;
        }
        return BuiltInRegistries.SOUND_EVENT.getHolder(id)
                .map(holder -> (Holder<SoundEvent>) holder)
                .orElse(SoundEvents.ARMOR_EQUIP_DIAMOND);
    }
}
