package com.luatweaker.common.content;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.luatweaker.api.content.IDatapackService;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Generates assets for shaped block variants: stairs, slabs, and walls.
 */
final class ContentShapeAssets {

    private static final String FACING_KEY_PREFIX = "facing=";
    private static final String HALF_BOTTOM = ",half=bottom,shape=";
    private static final String HALF_TOP = ",half=top,shape=";

    private ContentShapeAssets() {
    }

    static void injectSlabAssets(@NotNull IDatapackService datapackService,
                                 @NotNull String namespace, @NotNull String id, String baseBlockId) {
        String base = baseTexture(namespace, id, "_slab", baseBlockId);
        String blockstateJson = "{\n"
                + "  \"variants\": {\n"
                + "    \"type=bottom\": { \"model\": \"" + namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id + "\" },\n"
                + "    \"type=double\": { \"model\": \"" + base + "\" },\n"
                + "    \"type=top\": { \"model\": \"" + namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id + "_top\" }\n"
                + "  }\n"
                + "}";
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.BLOCKSTATES_DIR + id + ContentAssetPaths.JSON_EXTENSION, blockstateJson);

        String bottomJson = "{\n  \"parent\": \"minecraft:block/slab\",\n  \"textures\": {\n    \"bottom\": \"" + base
                + ContentAssetPaths.JSON_TOP_ENTRY + base + ContentAssetPaths.JSON_SIDE_ENTRY + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + ContentAssetPaths.JSON_EXTENSION, bottomJson);

        String topJson = "{\n  \"parent\": \"minecraft:block/slab_top\",\n  \"textures\": {\n    \"bottom\": \"" + base
                + ContentAssetPaths.JSON_TOP_ENTRY + base + ContentAssetPaths.JSON_SIDE_ENTRY + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + "_top.json", topJson);

        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_ITEM_DIR + id + ContentAssetPaths.JSON_EXTENSION,
                ContentAssetPaths.JSON_PARENT_HEAD + namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id
                        + ContentAssetPaths.JSON_QUOTE_TAIL);
    }

    static void injectStairsAssets(@NotNull IDatapackService datapackService,
                                   @NotNull String namespace, @NotNull String id, String baseBlockId) {
        String base = baseTexture(namespace, id, "_stairs", baseBlockId);
        String straight = namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id;
        String inner = namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id + "_inner";
        String outer = namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id + "_outer";

        JsonObject variants = new JsonObject();
        addStairVariants(variants, "east", straight, inner, outer);
        addStairVariants(variants, "north", straight, inner, outer);
        addStairVariants(variants, "south", straight, inner, outer);
        addStairVariants(variants, "west", straight, inner, outer);

        JsonObject blockstate = new JsonObject();
        blockstate.add("variants", variants);
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.BLOCKSTATES_DIR + id + ContentAssetPaths.JSON_EXTENSION,
                ContentAssetPaths.GSON.toJson(blockstate));

        String straightJson = "{\n  \"parent\": \"minecraft:block/stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base
                + ContentAssetPaths.JSON_TOP_ENTRY + base + ContentAssetPaths.JSON_SIDE_ENTRY + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + ContentAssetPaths.JSON_EXTENSION, straightJson);

        String innerJson = "{\n  \"parent\": \"minecraft:block/inner_stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base
                + ContentAssetPaths.JSON_TOP_ENTRY + base + ContentAssetPaths.JSON_SIDE_ENTRY + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + "_inner.json", innerJson);

        String outerJson = "{\n  \"parent\": \"minecraft:block/outer_stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base
                + ContentAssetPaths.JSON_TOP_ENTRY + base + ContentAssetPaths.JSON_SIDE_ENTRY + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + "_outer.json", outerJson);

        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_ITEM_DIR + id + ContentAssetPaths.JSON_EXTENSION,
                ContentAssetPaths.JSON_PARENT_HEAD + straight + ContentAssetPaths.JSON_QUOTE_TAIL);
    }

    private static void addStairVariants(@NotNull JsonObject variants, @NotNull String facing,
                                         @NotNull String straight, @NotNull String inner, @NotNull String outer) {
        int baseRotation = switch (facing.toLowerCase(Locale.ROOT)) {
            case "east" -> 0;
            case "north" -> 270;
            case "south" -> 90;
            default -> 180;
        };
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_BOTTOM + "inner_left", inner, 0, (baseRotation + 270) % 360);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_BOTTOM + "inner_right", inner, 0, baseRotation);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_BOTTOM + "outer_left", outer, 0, (baseRotation + 270) % 360);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_BOTTOM + "outer_right", outer, 0, baseRotation);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_BOTTOM + "straight", straight, 0, baseRotation);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_TOP + "inner_left", inner, 180, baseRotation);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_TOP + "inner_right", inner, 180, (baseRotation + 90) % 360);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_TOP + "outer_left", outer, 180, baseRotation);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_TOP + "outer_right", outer, 180, (baseRotation + 90) % 360);
        addStairVariant(variants, FACING_KEY_PREFIX + facing + HALF_TOP + "straight", straight, 180, baseRotation);
    }

    private static void addStairVariant(@NotNull JsonObject variants, @NotNull String key,
                                        @NotNull String model, int x, int y) {
        JsonObject variant = new JsonObject();
        variant.addProperty(ContentAssetPaths.JSON_MODEL_KEY, model);
        if (x > 0 || y > 0) {
            variant.addProperty("uvlock", true);
        }
        if (x > 0) {
            variant.addProperty("x", x);
        }
        if (y > 0) {
            variant.addProperty("y", y);
        }
        variants.add(key, variant);
    }

    static void injectWallAssets(@NotNull IDatapackService datapackService,
                                 @NotNull String namespace, @NotNull String id, String baseBlockId) {
        String base = baseTexture(namespace, id, "_wall", baseBlockId);

        JsonArray multipart = new JsonArray();
        JsonObject postWhen = new JsonObject();
        postWhen.addProperty("up", true);
        JsonObject postCase = new JsonObject();
        postCase.add("when", postWhen);
        JsonObject postApply = new JsonObject();
        postApply.addProperty(ContentAssetPaths.JSON_MODEL_KEY,
                namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id + "_post");
        postCase.add("apply", postApply);
        multipart.add(postCase);

        String[] sides = {"north", "east", "south", "west"};
        int[] rotations = {0, 90, 180, 270};
        for (int i = 0; i < sides.length; i++) {
            addWallCase(multipart, sides[i], "low",
                    namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id + "_side", rotations[i]);
            addWallCase(multipart, sides[i], "tall",
                    namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id + "_side_tall", rotations[i]);
        }

        JsonObject blockstate = new JsonObject();
        blockstate.add("multipart", multipart);
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.BLOCKSTATES_DIR + id + ContentAssetPaths.JSON_EXTENSION,
                ContentAssetPaths.GSON.toJson(blockstate));

        String postJson = "{\n  \"parent\": \"minecraft:block/template_wall_post\",\n  \"textures\": {\n    \"wall\": \"" + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + "_post.json", postJson);

        String sideJson = "{\n  \"parent\": \"minecraft:block/template_wall_side\",\n  \"textures\": {\n    \"wall\": \"" + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + "_side.json", sideJson);

        String tallJson = "{\n  \"parent\": \"minecraft:block/template_wall_side_tall\",\n  \"textures\": {\n    \"wall\": \"" + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + "_side_tall.json", tallJson);

        String itemJson = "{\n  \"parent\": \"minecraft:block/wall_inventory\",\n  \"textures\": {\n    \"wall\": \"" + base
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_ITEM_DIR + id + ContentAssetPaths.JSON_EXTENSION, itemJson);
    }

    private static void addWallCase(@NotNull JsonArray multipart, @NotNull String side,
                                    @NotNull String state, @NotNull String model, int rotation) {
        JsonObject when = new JsonObject();
        when.addProperty(side, state);
        JsonObject apply = new JsonObject();
        apply.addProperty(ContentAssetPaths.JSON_MODEL_KEY, model);
        if (rotation > 0) {
            apply.addProperty("y", rotation);
        }
        apply.addProperty("uvlock", true);
        JsonObject wallCase = new JsonObject();
        wallCase.add("when", when);
        wallCase.add("apply", apply);
        multipart.add(wallCase);
    }

    private static String baseTexture(@NotNull String namespace, @NotNull String id, @NotNull String suffix,
                                      String baseBlockId) {
        if (baseBlockId != null && !baseBlockId.isBlank()) {
            String[] parts = ContentAssetPaths.parseId(baseBlockId);
            return parts[0] + ContentAssetPaths.BLOCK_MODEL_PREFIX + parts[1];
        }
        return namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id.replace(suffix, "_block");
    }
}
