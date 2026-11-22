package com.luatweaker.common.content;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.util.CallbackFirewall;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Locale;
import java.util.Map;

/**
 * Writes item models ({@code assets/<ns>/models/item/*.json}) and entity
 * spawn-egg models. Physical files shipped by the modder always win over
 * generated fallbacks.
 */
final class ContentItemAssets {

    private static final String MODEL_HANDHELD = "minecraft:item/handheld";
    private static final String MODEL_GENERATED = "minecraft:item/generated";

    private static final Map<String, String> TOOL_PARENT_MODELS = Map.of(
            "SWORD", MODEL_HANDHELD,
            "PICKAXE", MODEL_HANDHELD,
            "AXE", MODEL_HANDHELD,
            "SHOVEL", MODEL_HANDHELD,
            "HOE", MODEL_HANDHELD,
            "HELMET", MODEL_GENERATED,
            "CHESTPLATE", MODEL_GENERATED,
            "LEGGINGS", MODEL_GENERATED,
            "BOOTS", MODEL_GENERATED);

    private static final Map<String, String> TOOL_FALLBACK_TEXTURES = Map.of(
            "SWORD", "minecraft:item/iron_sword",
            "PICKAXE", "minecraft:item/iron_pickaxe",
            "AXE", "minecraft:item/iron_axe",
            "SHOVEL", "minecraft:item/iron_shovel",
            "HOE", "minecraft:item/iron_hoe",
            "HELMET", "minecraft:item/iron_helmet",
            "CHESTPLATE", "minecraft:item/iron_chestplate",
            "LEGGINGS", "minecraft:item/iron_leggings",
            "BOOTS", "minecraft:item/iron_boots");

    private static final String DEFAULT_PARENT_MODEL = MODEL_GENERATED;
    private static final String DEFAULT_FALLBACK_TEXTURE = "minecraft:item/amethyst_shard";

    private final File luaDir;
    private final IDatapackService datapackService;

    private ContentItemAssets(@NotNull File luaDir, @NotNull IDatapackService datapackService) {
        this.luaDir = luaDir;
        this.datapackService = datapackService;
    }

    static void rebuild(@NotNull File luaDir, @NotNull IContentService contentService,
                        @NotNull IDatapackService datapackService) {
        ContentItemAssets assets = new ContentItemAssets(luaDir, datapackService);
        for (IItemBuilder builder : contentService.getRegisteredItems()) {
            CallbackFirewall.run(() -> assets.injectItemModel(builder), LogStage.SYSTEM,
                    () -> "[ContentItemAssets] Failed item model for '" + builder.getId() + "'");
        }
        for (IEntityBuilder builder : contentService.getRegisteredEntities()) {
            CallbackFirewall.run(() -> assets.injectSpawnEggModel(builder), LogStage.SYSTEM,
                    () -> "[ContentItemAssets] Failed spawn-egg model for '" + builder.getId() + "'");
        }
    }

    private void injectItemModel(@NotNull IItemBuilder builder) {
        String[] parts = ContentAssetPaths.parseId(builder.getId());
        String namespace = parts[0];
        String id = parts[1];
        String virtualKey = ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_ITEM_DIR + id + ContentAssetPaths.JSON_EXTENSION;

        if (builder.getGeoModel() != null) {
            String texture = builder.getTexture() != null
                    ? builder.getTexture()
                    : namespace + ContentAssetPaths.ITEM_MODEL_PREFIX + id;
            datapackService.addData(virtualKey, geoItemModelJson(texture));
            return;
        }
        if (ContentAssetPaths.isPhysicalAssetPresent(luaDir, virtualKey)) {
            return;
        }
        datapackService.addData(virtualKey, itemModelJson(namespace, id, builder));
    }

    private static String geoItemModelJson(@NotNull String texture) {
        return "{\n  \"parent\": \"builtin/entity\",\n  \"textures\": {\n    \"particle\": \"" + texture
                + ContentAssetPaths.JSON_TAIL;
    }

    private String itemModelJson(@NotNull String namespace, @NotNull String id,
                                 @NotNull IItemBuilder builder) {
        String model = builder.getModel();
        if (model != null && !model.isBlank()) {
            return ContentAssetPaths.JSON_PARENT_HEAD
                    + explicitItemModel(model, namespace, id, builder.getType())
                    + ContentAssetPaths.JSON_QUOTE_TAIL;
        }
        String parent = parentModel(builder.getType());
        String texture = builder.getTexture() != null ? builder.getTexture() : defaultItemTexture(namespace, id);
        if (!ContentAssetPaths.isTextureAvailable(luaDir, texture)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ContentItemAssets] Item texture '" + texture + "' for item '" + builder.getId()
                            + "' not found on disk, using safe fallback");
            texture = fallbackItemTexture(builder.getType());
        }
        return ContentAssetPaths.JSON_PARENT_HEAD + parent + "\",\n  \"textures\": {\n    \"layer0\": \"" + texture
                + ContentAssetPaths.JSON_TAIL;
    }

    private static String explicitItemModel(@NotNull String model, @NotNull String namespace,
                                            @NotNull String id, String type) {
        boolean pointsAtItself = model.equals(namespace + ContentAssetPaths.ITEM_MODEL_PREFIX + id)
                || model.equals(id)
                || model.endsWith("/" + id);
        return pointsAtItself ? parentModel(type) : model;
    }

    private static String parentModel(String type) {
        if (type == null) {
            return DEFAULT_PARENT_MODEL;
        }
        return TOOL_PARENT_MODELS.getOrDefault(type.toUpperCase(Locale.ROOT), DEFAULT_PARENT_MODEL);
    }

    private static String defaultItemTexture(@NotNull String namespace, @NotNull String id) {
        return id.contains("apple") ? "minecraft:item/apple" : namespace + ContentAssetPaths.ITEM_MODEL_PREFIX + id;
    }

    private static String fallbackItemTexture(String type) {
        if (type == null) {
            return DEFAULT_FALLBACK_TEXTURE;
        }
        return TOOL_FALLBACK_TEXTURES.getOrDefault(type.toUpperCase(Locale.ROOT), DEFAULT_FALLBACK_TEXTURE);
    }

    private void injectSpawnEggModel(@NotNull IEntityBuilder builder) {
        if (!builder.hasSpawnEgg()) {
            return;
        }
        String[] parts = ContentAssetPaths.parseId(builder.getId());
        String virtualKey = ContentAssetPaths.ASSETS_PREFIX + parts[0]
                + ContentAssetPaths.MODELS_ITEM_DIR + parts[1] + "_spawn_egg.json";
        if (ContentAssetPaths.isPhysicalAssetPresent(luaDir, virtualKey)) {
            return;
        }
        datapackService.addData(virtualKey, spawnEggModelJson(builder.getSpawnEggTexture()));
    }

    private static String spawnEggModelJson(String texture) {
        if (texture != null && !texture.isBlank()) {
            return "{\n  \"parent\": \"" + MODEL_GENERATED + "\",\n  \"textures\": {\n    \"layer0\": \"" + texture
                    + ContentAssetPaths.JSON_TAIL;
        }
        return "{\n  \"parent\": \"minecraft:item/template_spawn_egg\"\n}";
    }
}
