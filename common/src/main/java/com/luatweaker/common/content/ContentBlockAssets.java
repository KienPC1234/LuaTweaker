package com.luatweaker.common.content;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.util.CallbackFirewall;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Writes blockstates, block models and block-item models
 * ({@code assets/<ns>/{blockstates,models/block,models/item}/*.json}).
 * Simple cubes, machine variants (via {@link MachineAssetGenerator}) and
 * stairs/slab/wall shapes (via {@link ContentShapeAssets}) each get their vanilla-shaped JSON.
 */
final class ContentBlockAssets {

    private static final String SHAPE_STAIRS = "STAIRS";
    private static final String SHAPE_SLAB = "SLAB";
    private static final String SHAPE_WALL = "WALL";

    private final File luaDir;
    private final IDatapackService datapackService;

    private ContentBlockAssets(@NotNull File luaDir, @NotNull IDatapackService datapackService) {
        this.luaDir = luaDir;
        this.datapackService = datapackService;
    }

    static void rebuild(@NotNull File luaDir, @NotNull IContentService contentService,
                        @NotNull IDatapackService datapackService) {
        ContentBlockAssets assets = new ContentBlockAssets(luaDir, datapackService);
        for (IBlockBuilder builder : contentService.getRegisteredBlocks()) {
            CallbackFirewall.run(() -> assets.injectBlockAsset(builder), LogStage.SYSTEM,
                    () -> "[ContentBlockAssets] Failed block assets for '" + builder.getId() + "'");
        }
    }

    private void injectBlockAsset(@NotNull IBlockBuilder builder) {
        if (isStatefulBlock(builder)) {
            injectStatefulBlockAssets(builder);
            return;
        }
        String[] parts = ContentAssetPaths.parseId(builder.getId());
        String namespace = parts[0];
        String id = parts[1];
        String shape = builder.getEffectiveShape();
        if (SHAPE_STAIRS.equals(shape)) {
            ContentShapeAssets.injectStairsAssets(datapackService, namespace, id, builder.getBaseBlockId());
        } else if (SHAPE_SLAB.equals(shape)) {
            ContentShapeAssets.injectSlabAssets(datapackService, namespace, id, builder.getBaseBlockId());
        } else if (SHAPE_WALL.equals(shape)) {
            ContentShapeAssets.injectWallAssets(datapackService, namespace, id, builder.getBaseBlockId());
        } else {
            injectRegularBlockAssets(builder, namespace, id);
        }
    }

    private static boolean isStatefulBlock(@NotNull IBlockBuilder builder) {
        boolean hasFacing = builder.getFacing() != null && !"NONE".equalsIgnoreCase(builder.getFacing());
        return builder.getBooleanState() != null || builder.isConnectionState() || hasFacing;
    }

    private void injectStatefulBlockAssets(@NotNull IBlockBuilder builder) {
        String[] parts = ContentAssetPaths.parseId(builder.getId());
        String blockstateKey = ContentAssetPaths.ASSETS_PREFIX + parts[0]
                + ContentAssetPaths.BLOCKSTATES_DIR + parts[1] + ContentAssetPaths.JSON_EXTENSION;
        if (ContentAssetPaths.isPhysicalAssetPresent(luaDir, blockstateKey)) {
            return;
        }
        MachineAssetGenerator.generate(datapackService, parts[0], parts[1], builder.getBooleanState(),
                builder.isConnectionState(), builder.getFacing(), builder.getTexture(), builder.getModel());
    }

    private void injectRegularBlockAssets(@NotNull IBlockBuilder builder, @NotNull String namespace,
                                          @NotNull String id) {
        injectBlockstateFile(builder, namespace, id);
        injectBlockModelFile(builder, namespace, id);
        injectBlockItemModel(builder, namespace, id);
    }

    private void injectBlockstateFile(@NotNull IBlockBuilder builder, @NotNull String namespace,
                                      @NotNull String id) {
        String blockstateKey = ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.BLOCKSTATES_DIR + id + ContentAssetPaths.JSON_EXTENSION;
        if (ContentAssetPaths.isPhysicalAssetPresent(luaDir, blockstateKey)) {
            return;
        }
        String blockstateJson = "{\n  \"variants\": {\n    \"\": { \"model\": \""
                + blockTargetModel(builder, namespace, id) + "\" }\n  }\n}";
        datapackService.addData(blockstateKey, blockstateJson);
    }

    private void injectBlockModelFile(@NotNull IBlockBuilder builder, @NotNull String namespace,
                                      @NotNull String id) {
        if (builder.getModel() != null) {
            return;
        }
        String modelKey = ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_BLOCK_DIR + id + ContentAssetPaths.JSON_EXTENSION;
        if (ContentAssetPaths.isPhysicalAssetPresent(luaDir, modelKey)) {
            return;
        }
        String texture = builder.getTexture() != null
                ? builder.getTexture()
                : (namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id);
        if (!ContentAssetPaths.isTextureAvailable(luaDir, texture)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[ContentBlockAssets] Block texture '" + texture + "' for block '" + builder.getId()
                            + "' not found on disk, using safe fallback");
            texture = "minecraft:block/stone";
        }
        String modelJson = "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \"" + texture
                + ContentAssetPaths.JSON_TAIL;
        datapackService.addData(modelKey, modelJson);
    }

    private void injectBlockItemModel(@NotNull IBlockBuilder builder, @NotNull String namespace,
                                      @NotNull String id) {
        String itemKey = ContentAssetPaths.ASSETS_PREFIX + namespace
                + ContentAssetPaths.MODELS_ITEM_DIR + id + ContentAssetPaths.JSON_EXTENSION;
        if (ContentAssetPaths.isPhysicalAssetPresent(luaDir, itemKey)) {
            return;
        }
        datapackService.addData(itemKey, ContentAssetPaths.JSON_PARENT_HEAD
                + blockTargetModel(builder, namespace, id) + ContentAssetPaths.JSON_QUOTE_TAIL);
    }

    private static String blockTargetModel(@NotNull IBlockBuilder builder, @NotNull String namespace,
                                           @NotNull String id) {
        return builder.getModel() != null ? builder.getModel() : (namespace + ContentAssetPaths.BLOCK_MODEL_PREFIX + id);
    }
}
