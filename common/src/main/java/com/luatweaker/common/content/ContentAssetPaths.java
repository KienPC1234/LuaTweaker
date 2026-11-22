package com.luatweaker.common.content;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Shared constants and physical-asset guards for content virtual-file
 * generation. A virtual entry is never written when the modder already shipped
 * the same path on disk (under {@code lua/} or any
 * {@code luamods/<mod_id>/} folder).
 */
final class ContentAssetPaths {

    static final Gson GSON = new Gson();

    static final String ASSETS_PREFIX = "assets/";
    static final String MODELS_ITEM_DIR = "/models/item/";
    static final String JSON_EXTENSION = ".json";
    static final String JSON_TAIL = "\"\n  }\n}";
    static final String JSON_PARENT_HEAD = "{\n  \"parent\": \"";
    static final String JSON_QUOTE_TAIL = "\"\n}";
    static final String BLOCKSTATES_DIR = "/blockstates/";
    static final String BLOCK_MODEL_PREFIX = ":block/";
    static final String ITEM_MODEL_PREFIX = ":item/";
    static final String MODELS_BLOCK_DIR = "/models/block/";
    static final String JSON_TOP_ENTRY = "\",\n    \"top\": \"";
    static final String JSON_SIDE_ENTRY = "\",\n    \"side\": \"";
    static final String JSON_MODEL_KEY = "model";
    static final String DEFAULT_NAMESPACE = "luatweaker";

    private ContentAssetPaths() {
    }

    static String[] parseId(String id) {
        if (id != null && id.contains(":")) {
            return id.split(":", 2);
        }
        return new String[]{DEFAULT_NAMESPACE, id != null ? id : "unknown"};
    }

    /**
     * Texture/model path of the block a shape is made of. An explicit base
     * block id is used verbatim ({@code minecraft:stone -> minecraft:block/stone});
     * otherwise the historic {@code <id>_block} naming is kept.
     */
    static String baseTexture(@NotNull String namespace, @NotNull String id, @NotNull String suffix,
                              String baseBlockId) {
        if (baseBlockId != null && !baseBlockId.isBlank()) {
            String[] parts = parseId(baseBlockId);
            return parts[0] + BLOCK_MODEL_PREFIX + parts[1];
        }
        return namespace + BLOCK_MODEL_PREFIX + id.replace(suffix, "_block");
    }

    static boolean isPhysicalAssetPresent(@NotNull File luaDir, @NotNull String virtualKey) {
        if (new File(luaDir, virtualKey).exists()) {
            return true;
        }
        File luamodsDir = "luamods".equalsIgnoreCase(luaDir.getName()) ? luaDir : new File(luaDir, "luamods");
        if (!luamodsDir.exists() || !luamodsDir.isDirectory()) {
            return false;
        }
        File[] modFolders = luamodsDir.listFiles(File::isDirectory);
        if (modFolders == null) {
            return false;
        }
        for (File modFolder : modFolders) {
            if (modHasAsset(modFolder, virtualKey)) {
                return true;
            }
        }
        return false;
    }

    private static boolean modHasAsset(@NotNull File modFolder, @NotNull String virtualKey) {
        return new File(modFolder, virtualKey).exists();
    }

    static boolean isTextureAvailable(@NotNull File luaDir, String texture) {
        if (texture == null || texture.isBlank()) {
            return false;
        }
        if (texture.startsWith("minecraft:")) {
            return true;
        }
        String[] parts = parseId(texture);
        String path = parts[1];
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        return isPhysicalAssetPresent(luaDir, ASSETS_PREFIX + parts[0] + "/textures/" + path + ".png");
    }
}
