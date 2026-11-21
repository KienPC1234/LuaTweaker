package com.luatweaker.platform.content;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registers two 100% in-memory virtual resource packs with Minecraft:
 *
 * <ul>
 *   <li><b>CLIENT_RESOURCES</b> – uses {@link LuaTweakerVirtualPackResources} to serve blockstates,
 *       models, item models, entity models, and language entries directly from RAM (zero write to disk).</li>
 *   <li><b>SERVER_DATA</b> – uses {@link LuaTweakerVirtualPackResources} (100% in-memory)
 *       to serve loot tables, recipes, advancements, and tags from Lua scripts without ever writing
 *       to disk, exactly like KubeJS.</li>
 * </ul>
 */
public class LuaAssetsPackFinder {

    private static final String ASSETS_PREFIX = "assets/";
    private static final String MODELS_ITEM_DIR = "/models/item/";
    private static final String JSON_EXTENSION = ".json";
    private static final String JSON_TAIL = "\"\n  }\n}";
    private static final String JSON_PARENT_HEAD = "{\n  \"parent\": \"";
    private static final String JSON_QUOTE_TAIL = "\"\n}";
    private static final String BLOCKSTATES_DIR = "/blockstates/";
    private static final String BLOCK_MODEL_PREFIX = ":block/";
    private static final String ITEM_MODEL_PREFIX = ":item/";
    private static final String MODELS_BLOCK_DIR = "/models/block/";
    private static final String BUCKET_SUFFIX = "_bucket";
    private static final String JSON_TOP_ENTRY = "\",\n    \"top\": \"";
    private static final String JSON_SIDE_ENTRY = "\",\n    \"side\": \"";
    private static final String JSON_MODEL_KEY = "model";
    private static final String SHAPE_STAIRS = "STAIRS";
    private static final String SHAPE_SLAB = "SLAB";
    private static final String SHAPE_WALL = "WALL";


    private static final Gson GSON = new Gson();

    private final File luaDir;
    private final IDatapackService datapackService;
    private final IContentService contentService;

    public LuaAssetsPackFinder(File luaDir, IDatapackService datapackService, IContentService contentService) {
        this.luaDir = luaDir;
        this.datapackService = datapackService;
        this.contentService = contentService;
    }

    @SubscribeEvent
    public void onAddPackFinders(AddPackFindersEvent event) {
        File assetsDir = new File(luaDir, "assets");
        if (!assetsDir.exists()) { assetsDir.mkdirs(); }

        if (event.getPackType() == PackType.CLIENT_RESOURCES) {
            // Generate all asset JSON files directly in RAM (Zero Write to Disk)
            injectVirtualAssetFiles();
            PackLocationInfo locationInfo = new PackLocationInfo(
                    "luatweaker_user_assets",
                    Component.literal("LuaTweaker User Assets"),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );

            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override
                public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo id) {
                    return new LuaTweakerVirtualPackResources(id, PackType.CLIENT_RESOURCES, luaDir, datapackService);
                }

                @Override
                public net.minecraft.server.packs.PackResources openFull(PackLocationInfo id, Pack.Metadata meta) {
                    return new LuaTweakerVirtualPackResources(id, PackType.CLIENT_RESOURCES, luaDir, datapackService);
                }
            };

            Pack pack = Pack.readMetaAndCreate(
                    locationInfo,
                    supplier,
                    PackType.CLIENT_RESOURCES,
                    new PackSelectionConfig(true, Pack.Position.TOP, false)
            );

            if (pack != null) {
                event.addRepositorySource(consumer -> consumer.accept(pack));
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[LuaTweaker] Mounted Zero-Write-To-Disk Virtual ResourcePack for client assets (100% In-Memory RAM)");
            }

        } else if (event.getPackType() == PackType.SERVER_DATA) {
            // Auto-inject mining tags into virtualFiles (in-memory)
            injectBlockTagsToVirtualPack();

            PackLocationInfo locationInfo = new PackLocationInfo(
                    "luatweaker_user_datapack",
                    Component.literal("LuaTweaker Virtual DataPack"),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );

            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override
                public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo id) {
                    return new LuaTweakerVirtualPackResources(id, PackType.SERVER_DATA, luaDir, datapackService);
                }

                @Override
                public net.minecraft.server.packs.PackResources openFull(PackLocationInfo id, Pack.Metadata meta) {
                    return new LuaTweakerVirtualPackResources(id, PackType.SERVER_DATA, luaDir, datapackService);
                }
            };

            Pack pack = Pack.readMetaAndCreate(
                    locationInfo,
                    supplier,
                    PackType.SERVER_DATA,
                    new PackSelectionConfig(true, Pack.Position.TOP, false)
            );

            if (pack != null) {
                event.addRepositorySource(consumer -> consumer.accept(pack));
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[LuaTweaker] Mounted KubeJS-style Virtual DataPack (100% in-memory RAM, no disk flush)");
            }
        }
    }

    /**
     * Re-generates every content-driven virtual file (block tags, models, lang, ...).
     * Called after {@link IDatapackService#clear()} on reload, so content files are
     * rebuilt from the SAME content service instead of being lost until the next
     * pack scan.
     */
    public void rebuildVirtualFiles() {
        injectBlockTagsToVirtualPack();
        if (com.luatweaker.api.pal.Platform.isInitialized() && com.luatweaker.api.pal.Platform.getContent().isClient()) {
            injectVirtualAssetFiles();
        }
    }

    // -----------------------------------------------------------------------
    // Mining / tool tags → inject into virtualFiles (RAM)
    // -----------------------------------------------------------------------

    private void injectBlockTagsToVirtualPack() {
        if (contentService == null || datapackService == null) { return; }

        for (Map.Entry<String, List<String>> entry
                : ContentTagMapper.computeBlockTags(contentService).entrySet()) {
            injectBlockTag(entry);
        }
    }

    private void injectBlockTag(Map.Entry<String, List<String>> entry) {
        String virtualKey = "data/minecraft/" + entry.getKey();
        JsonObject json = new JsonObject();
        json.addProperty("replace", false);
        json.add("values", mergeTagValues(virtualKey, entry.getValue()));
        datapackService.addData(virtualKey, GSON.toJson(json));
    }

    /** Existing "values" of a tag already registered in memory, extended with the new entries. */
    private JsonArray mergeTagValues(String virtualKey, List<String> elements) {
        JsonArray values = existingTagValues(virtualKey);
        for (String elem : elements) {
            if (!tagValueContains(values, elem)) {
                values.add(elem);
            }
        }
        return values;
    }

    private JsonArray existingTagValues(String virtualKey) {
        String existing = datapackService.getVirtualFiles().get(virtualKey);
        if (existing == null) {
            return new JsonArray();
        }
        return GSON.fromJson(existing, JsonObject.class).getAsJsonArray("values");
    }

    private static boolean tagValueContains(JsonArray values, String element) {
        for (int i = 0; i < values.size(); i++) {
            if (values.get(i).getAsString().equals(element)) {
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Asset generation (In-Memory RAM — Zero Write to Disk)
    // -----------------------------------------------------------------------

    void injectVirtualAssetFiles() {
        if (contentService == null || datapackService == null) { return; }
        injectItemModelAssets();
        injectSpawnEggAssets();
        injectBlockAssets();
        injectFluidAssets();
        injectLanguageFiles();
    }

    // 1. Item models
    private void injectItemModelAssets() {
        for (IItemBuilder b : contentService.getRegisteredItems()) {
            injectItemModel(b);
        }
    }

    private void injectItemModel(IItemBuilder b) {
        String[] parts = parseId(b.getId());
        String ns = parts[0];
        String id = parts[1];
        String virtualKey = ASSETS_PREFIX + ns + MODELS_ITEM_DIR + id + JSON_EXTENSION;

        if (b.getGeoModel() != null) {
            datapackService.addData(virtualKey, geoItemModelJson(ns, id, b.getTexture()));
            return;
        }

        // If user provided physical file on disk under lua/assets/ or luamods/<mod_id>/assets/, don't override it
        if (isPhysicalAssetPresent(virtualKey)) {
            return;
        }

        datapackService.addData(virtualKey, itemModelJson(ns, id, b));
    }

    private static String geoItemModelJson(String ns, String id, String texture) {
        String tex = texture != null ? texture : ns + ITEM_MODEL_PREFIX + id;
        return "{\n  \"parent\": \"builtin/entity\",\n  \"textures\": {\n    \"particle\": \"" + tex + JSON_TAIL;
    }

    private String itemModelJson(String ns, String id, IItemBuilder b) {
        String model = b.getModel();
        if (model != null && !model.isBlank()) {
            return JSON_PARENT_HEAD + explicitItemModel(model, ns, id, b.getType()) + JSON_QUOTE_TAIL;
        }

        String parent = itemParentModel(b.getType());
        String tex = b.getTexture() != null ? b.getTexture() : defaultItemTexture(ns, id);
        if (!isTextureAvailable(tex)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[LuaAssetsPackFinder] Item texture '" + tex + "' for item '" + b.getId() + "' not found on disk, using safe fallback");
            tex = fallbackItemTexture(b.getType());
        }
        return JSON_PARENT_HEAD + parent + "\",\n  \"textures\": {\n    \"layer0\": \"" + tex + JSON_TAIL;
    }

    /** Explicit model, unless it points at the item itself (circular reference). */
    private String explicitItemModel(String model, String ns, String id, String type) {
        boolean pointsAtItself = model.equals(ns + ITEM_MODEL_PREFIX + id)
                || model.equals(id)
                || model.endsWith("/" + id);
        return pointsAtItself ? itemParentModel(type) : model;
    }

    private String itemParentModel(String type) {
        return isToolType(type) ? "minecraft:item/handheld" : "minecraft:item/generated";
    }

    private static String defaultItemTexture(String ns, String id) {
        return id.contains("apple") ? "minecraft:item/apple" : ns + ITEM_MODEL_PREFIX + id;
    }

    // 2. Entity Spawn Egg Item Models
    private void injectSpawnEggAssets() {
        for (IEntityBuilder e : contentService.getRegisteredEntities()) {
            injectSpawnEggModel(e);
        }
    }

    private void injectSpawnEggModel(IEntityBuilder e) {
        if (!e.hasSpawnEgg()) {
            return;
        }
        String[] parts = parseId(e.getId());
        String virtualKey = ASSETS_PREFIX + parts[0] + MODELS_ITEM_DIR + parts[1] + "_spawn_egg.json";
        if (isPhysicalAssetPresent(virtualKey)) {
            return;
        }
        datapackService.addData(virtualKey, spawnEggModelJson(e.getSpawnEggTexture()));
    }

    private static String spawnEggModelJson(String texture) {
        if (texture != null && !texture.isBlank()) {
            return "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"" + texture + JSON_TAIL;
        }
        return "{\n  \"parent\": \"minecraft:item/template_spawn_egg\"\n}";
    }

    // 3. Block models and blockstates
    private void injectBlockAssets() {
        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            injectBlockAsset(b);
        }
    }

    private void injectBlockAsset(IBlockBuilder b) {
        String[] parts = parseId(b.getId());
        String ns = parts[0];
        String id = parts[1];

        if (isStatefulBlock(b)) {
            injectStatefulBlockAssets(b, ns, id);
            return;
        }

        String shape = b.getEffectiveShape();
        if (SHAPE_STAIRS.equals(shape)) {
            injectStairsAssets(ns, id, b.getBaseBlockId());
        } else if (SHAPE_SLAB.equals(shape)) {
            injectSlabAssets(ns, id, b.getBaseBlockId());
        } else if (SHAPE_WALL.equals(shape)) {
            injectWallAssets(ns, id, b.getBaseBlockId());
        } else {
            injectRegularBlockAssets(b, ns, id);
        }
    }

    private static boolean isStatefulBlock(IBlockBuilder b) {
        boolean hasFacing = b.getFacing() != null && !"NONE".equalsIgnoreCase(b.getFacing());
        return b.getBooleanState() != null || b.isConnectionState() || hasFacing;
    }

    private void injectStatefulBlockAssets(IBlockBuilder b, String ns, String id) {
        // Variant assets (running/off, pipe connections, facing rotations) are generated by
        // MachineAssetGenerator - the generic blockstate below cannot express them.
        // A physical user blockstate takes over completely (no auto files).
        String bsKey = ASSETS_PREFIX + ns + BLOCKSTATES_DIR + id + JSON_EXTENSION;
        if (isPhysicalAssetPresent(bsKey)) {
            return;
        }
        MachineAssetGenerator.generate(datapackService, ns, id, b.getBooleanState(),
                b.isConnectionState(), b.getFacing(), b.getTexture(), b.getModel());
    }

    private void injectRegularBlockAssets(IBlockBuilder b, String ns, String id) {
        injectBlockstateFile(b, ns, id);
        injectBlockModelFile(b, ns, id);
        injectBlockItemModel(b, ns, id);
    }

    private void injectBlockstateFile(IBlockBuilder b, String ns, String id) {
        String bsKey = ASSETS_PREFIX + ns + BLOCKSTATES_DIR + id + JSON_EXTENSION;
        if (isPhysicalAssetPresent(bsKey)) {
            return;
        }
        String bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + blockTargetModel(b, ns, id) + "\" }\n  }\n}";
        datapackService.addData(bsKey, bsJson);
    }

    private void injectBlockModelFile(IBlockBuilder b, String ns, String id) {
        if (b.getModel() != null) {
            return;
        }
        String bmKey = ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + JSON_EXTENSION;
        if (isPhysicalAssetPresent(bmKey)) {
            return;
        }
        String tex = b.getTexture() != null ? b.getTexture() : (ns + BLOCK_MODEL_PREFIX + id);
        if (!isTextureAvailable(tex)) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[LuaAssetsPackFinder] Block texture '" + tex + "' for block '" + b.getId() + "' not found on disk, using safe fallback");
            tex = "minecraft:block/stone";
        }
        String bmJson = "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \"" + tex + JSON_TAIL;
        datapackService.addData(bmKey, bmJson);
    }

    private void injectBlockItemModel(IBlockBuilder b, String ns, String id) {
        String imKey = ASSETS_PREFIX + ns + MODELS_ITEM_DIR + id + JSON_EXTENSION;
        if (isPhysicalAssetPresent(imKey)) {
            return;
        }
        String imJson = JSON_PARENT_HEAD + blockTargetModel(b, ns, id) + JSON_QUOTE_TAIL;
        datapackService.addData(imKey, imJson);
    }

    private static String blockTargetModel(IBlockBuilder b, String ns, String id) {
        return b.getModel() != null ? b.getModel() : (ns + BLOCK_MODEL_PREFIX + id);
    }

    private void injectFluidAssets() {
        for (IFluidBuilder b : contentService.getRegisteredFluids()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0];
            String id = parts[1];
            String blockId = id + "_block";
            String bucketId = id + BUCKET_SUFFIX;
            String altBucketId = id.replace("_fluid", "") + BUCKET_SUFFIX;

            String stillTex = b.getStillTexture() != null
                    ? b.getStillTexture()
                    : "minecraft:block/water_still";

            // Auto-inject .mcmeta animation metadata for fluid textures so vertical sprite strips animate smoothly without stripes
            String animMcmeta = "{\n  \"animation\": {\n    \"frametime\": 2\n  }\n}";
            if (b.getStillTexture() != null) {
                String[] sParts = parseId(b.getStillTexture());
                String path = sParts[1].replace("block/", "");
                datapackService.addData(ASSETS_PREFIX + sParts[0] + "/textures/block/" + path + ".png.mcmeta", animMcmeta);
            }
            if (b.getFlowingTexture() != null) {
                String[] fParts = parseId(b.getFlowingTexture());
                String path = fParts[1].replace("block/", "");
                datapackService.addData(ASSETS_PREFIX + fParts[0] + "/textures/block/" + path + ".png.mcmeta", animMcmeta);
            }

            // Fluid Blockstates (both <id>.json and <id>_block.json for safety)
            String bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + ns + BLOCK_MODEL_PREFIX + id + "\" }\n  }\n}";
            datapackService.addData(ASSETS_PREFIX + ns + BLOCKSTATES_DIR + id + JSON_EXTENSION, bsJson);
            datapackService.addData(ASSETS_PREFIX + ns + BLOCKSTATES_DIR + blockId + JSON_EXTENSION, bsJson);

            // Fluid Block Model
            String bmJson = "{\n  \"textures\": {\n    \"particle\": \"" + stillTex + JSON_TAIL;
            datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + JSON_EXTENSION, bmJson);
            datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + blockId + JSON_EXTENSION, bmJson);

            // Fluid Bucket Item Models using official NeoForge fluid_container loader
            String bucketJson = "{\n"
                    + "  \"loader\": \"neoforge:fluid_container\",\n"
                    + "  \"parent\": \"neoforge:item/bucket\",\n"
                    + "  \"fluid\": \"" + ns + ":" + id + "\"\n"
                    + "}";
            datapackService.addData(ASSETS_PREFIX + ns + MODELS_ITEM_DIR + bucketId + JSON_EXTENSION, bucketJson);
            datapackService.addData(ASSETS_PREFIX + ns + MODELS_ITEM_DIR + altBucketId + JSON_EXTENSION, bucketJson);
        }
    }

    /**
     * Texture/model path of the block a shape is made of. An explicit base block id is used
     * verbatim ({@code minecraft:stone -> minecraft:block/stone}); otherwise the historic
     * {@code <id>_block} naming is kept.
     */
    private String baseTexture(String ns, String id, String suffix, String baseBlockId) {
        if (baseBlockId != null && !baseBlockId.isBlank()) {
            String[] parts = parseId(baseBlockId);
            return parts[0] + BLOCK_MODEL_PREFIX + parts[1];
        }
        return ns + BLOCK_MODEL_PREFIX + id.replace(suffix, "_block");
    }

    private void injectSlabAssets(String ns, String id, String baseBlockId) {
        String base = baseTexture(ns, id, "_slab", baseBlockId);

        String bsJson = "{\n" +
                "  \"variants\": {\n" +
                "    \"type=bottom\": { \"model\": \"" + ns + BLOCK_MODEL_PREFIX + id + "\" },\n" +
                "    \"type=double\": { \"model\": \"" + base + "\" },\n" +
                "    \"type=top\": { \"model\": \"" + ns + BLOCK_MODEL_PREFIX + id + "_top\" }\n" +
                "  }\n" +
                "}";
        datapackService.addData(ASSETS_PREFIX + ns + BLOCKSTATES_DIR + id + JSON_EXTENSION, bsJson);

        String bJson = "{\n  \"parent\": \"minecraft:block/slab\",\n  \"textures\": {\n    \"bottom\": \"" + base + JSON_TOP_ENTRY + base + JSON_SIDE_ENTRY + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + JSON_EXTENSION, bJson);

        String tJson = "{\n  \"parent\": \"minecraft:block/slab_top\",\n  \"textures\": {\n    \"bottom\": \"" + base + JSON_TOP_ENTRY + base + JSON_SIDE_ENTRY + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + "_top.json", tJson);

        String iJson = JSON_PARENT_HEAD + ns + BLOCK_MODEL_PREFIX + id + JSON_QUOTE_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_ITEM_DIR + id + JSON_EXTENSION, iJson);
    }

    private void injectStairsAssets(String ns, String id, String baseBlockId) {
        String base = baseTexture(ns, id, "_stairs", baseBlockId);
        String mStraight = ns + BLOCK_MODEL_PREFIX + id;
        String mInner = ns + BLOCK_MODEL_PREFIX + id + "_inner";
        String mOuter = ns + BLOCK_MODEL_PREFIX + id + "_outer";

        JsonObject variants = new JsonObject();

        // facing=east
        addStairVar(variants, "facing=east,half=bottom,shape=inner_left", mInner, 0, 270);
        addStairVar(variants, "facing=east,half=bottom,shape=inner_right", mInner, 0, 0);
        addStairVar(variants, "facing=east,half=bottom,shape=outer_left", mOuter, 0, 270);
        addStairVar(variants, "facing=east,half=bottom,shape=outer_right", mOuter, 0, 0);
        addStairVar(variants, "facing=east,half=bottom,shape=straight", mStraight, 0, 0);

        addStairVar(variants, "facing=east,half=top,shape=inner_left", mInner, 180, 0);
        addStairVar(variants, "facing=east,half=top,shape=inner_right", mInner, 180, 90);
        addStairVar(variants, "facing=east,half=top,shape=outer_left", mOuter, 180, 0);
        addStairVar(variants, "facing=east,half=top,shape=outer_right", mOuter, 180, 90);
        addStairVar(variants, "facing=east,half=top,shape=straight", mStraight, 180, 0);

        // facing=north
        addStairVar(variants, "facing=north,half=bottom,shape=inner_left", mInner, 0, 180);
        addStairVar(variants, "facing=north,half=bottom,shape=inner_right", mInner, 0, 270);
        addStairVar(variants, "facing=north,half=bottom,shape=outer_left", mOuter, 0, 180);
        addStairVar(variants, "facing=north,half=bottom,shape=outer_right", mOuter, 0, 270);
        addStairVar(variants, "facing=north,half=bottom,shape=straight", mStraight, 0, 270);

        addStairVar(variants, "facing=north,half=top,shape=inner_left", mInner, 180, 270);
        addStairVar(variants, "facing=north,half=top,shape=inner_right", mInner, 180, 0);
        addStairVar(variants, "facing=north,half=top,shape=outer_left", mOuter, 180, 270);
        addStairVar(variants, "facing=north,half=top,shape=outer_right", mOuter, 180, 0);
        addStairVar(variants, "facing=north,half=top,shape=straight", mStraight, 180, 270);

        // facing=south
        addStairVar(variants, "facing=south,half=bottom,shape=inner_left", mInner, 0, 0);
        addStairVar(variants, "facing=south,half=bottom,shape=inner_right", mInner, 0, 90);
        addStairVar(variants, "facing=south,half=bottom,shape=outer_left", mOuter, 0, 0);
        addStairVar(variants, "facing=south,half=bottom,shape=outer_right", mOuter, 0, 90);
        addStairVar(variants, "facing=south,half=bottom,shape=straight", mStraight, 0, 90);

        addStairVar(variants, "facing=south,half=top,shape=inner_left", mInner, 180, 90);
        addStairVar(variants, "facing=south,half=top,shape=inner_right", mInner, 180, 180);
        addStairVar(variants, "facing=south,half=top,shape=outer_left", mOuter, 180, 90);
        addStairVar(variants, "facing=south,half=top,shape=outer_right", mOuter, 180, 180);
        addStairVar(variants, "facing=south,half=top,shape=straight", mStraight, 180, 90);

        // facing=west
        addStairVar(variants, "facing=west,half=bottom,shape=inner_left", mInner, 0, 90);
        addStairVar(variants, "facing=west,half=bottom,shape=inner_right", mInner, 0, 180);
        addStairVar(variants, "facing=west,half=bottom,shape=outer_left", mOuter, 0, 90);
        addStairVar(variants, "facing=west,half=bottom,shape=outer_right", mOuter, 0, 180);
        addStairVar(variants, "facing=west,half=bottom,shape=straight", mStraight, 0, 180);

        addStairVar(variants, "facing=west,half=top,shape=inner_left", mInner, 180, 180);
        addStairVar(variants, "facing=west,half=top,shape=inner_right", mInner, 180, 270);
        addStairVar(variants, "facing=west,half=top,shape=outer_left", mOuter, 180, 180);
        addStairVar(variants, "facing=west,half=top,shape=outer_right", mOuter, 180, 270);
        addStairVar(variants, "facing=west,half=top,shape=straight", mStraight, 180, 180);

        JsonObject bsObj = new JsonObject();
        bsObj.add("variants", variants);
        datapackService.addData(ASSETS_PREFIX + ns + BLOCKSTATES_DIR + id + JSON_EXTENSION, GSON.toJson(bsObj));

        String straightJson = "{\n  \"parent\": \"minecraft:block/stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + JSON_TOP_ENTRY + base + JSON_SIDE_ENTRY + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + JSON_EXTENSION, straightJson);

        String innerJson = "{\n  \"parent\": \"minecraft:block/inner_stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + JSON_TOP_ENTRY + base + JSON_SIDE_ENTRY + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + "_inner.json", innerJson);

        String outerJson = "{\n  \"parent\": \"minecraft:block/outer_stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + JSON_TOP_ENTRY + base + JSON_SIDE_ENTRY + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + "_outer.json", outerJson);

        String itemJson = JSON_PARENT_HEAD + ns + BLOCK_MODEL_PREFIX + id + JSON_QUOTE_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_ITEM_DIR + id + JSON_EXTENSION, itemJson);
    }

    private void addStairVar(JsonObject variants, String key, String model, int x, int y) {
        JsonObject v = new JsonObject();
        v.addProperty(JSON_MODEL_KEY, model);
        if (x > 0 || y > 0) {
            v.addProperty("uvlock", true);
        }
        if (x > 0) { v.addProperty("x", x); }
        if (y > 0) { v.addProperty("y", y); }
        variants.add(key, v);
    }

    /**
     * One multipart case: {@code when { <side>: <state> }} applying the side model, rotated
     * around Y when needed.
     */
    private static void addWallCase(JsonArray multipart, String side, String state, String model, int y) {
        JsonObject when = new JsonObject();
        when.addProperty(side, state);

        JsonObject apply = new JsonObject();
        apply.addProperty(JSON_MODEL_KEY, model);
        if (y > 0) { apply.addProperty("y", y); }
        apply.addProperty("uvlock", true);

        JsonObject wallCase = new JsonObject();
        wallCase.add("when", when);
        wallCase.add("apply", apply);
        multipart.add(wallCase);
    }

    private void injectWallAssets(String ns, String id, String baseBlockId) {
        String base = baseTexture(ns, id, "_wall", baseBlockId);

        JsonArray multipart = new JsonArray();

        // Post
        JsonObject postCase = new JsonObject();
        JsonObject postWhen = new JsonObject();
        postWhen.addProperty("up", true);
        postCase.add("when", postWhen);
        JsonObject postApply = new JsonObject();
        postApply.addProperty(JSON_MODEL_KEY, ns + BLOCK_MODEL_PREFIX + id + "_post");
        postCase.add("apply", postApply);
        multipart.add(postCase);

        String[] sides = {"north", "east", "south", "west"};
        int[] yRots = {0, 90, 180, 270};

        for (int i = 0; i < 4; i++) {
            addWallCase(multipart, sides[i], "low", ns + BLOCK_MODEL_PREFIX + id + "_side", yRots[i]);
            addWallCase(multipart, sides[i], "tall", ns + BLOCK_MODEL_PREFIX + id + "_side_tall", yRots[i]);
        }

        JsonObject bsObj = new JsonObject();
        bsObj.add("multipart", multipart);
        datapackService.addData(ASSETS_PREFIX + ns + BLOCKSTATES_DIR + id + JSON_EXTENSION, GSON.toJson(bsObj));

        String postJson = "{\n  \"parent\": \"minecraft:block/template_wall_post\",\n  \"textures\": {\n    \"wall\": \"" + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + "_post.json", postJson);

        String sideJson = "{\n  \"parent\": \"minecraft:block/template_wall_side\",\n  \"textures\": {\n    \"wall\": \"" + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + "_side.json", sideJson);

        String tallJson = "{\n  \"parent\": \"minecraft:block/template_wall_side_tall\",\n  \"textures\": {\n    \"wall\": \"" + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_BLOCK_DIR + id + "_side_tall.json", tallJson);

        String itemJson = "{\n  \"parent\": \"minecraft:block/wall_inventory\",\n  \"textures\": {\n    \"wall\": \"" + base + JSON_TAIL;
        datapackService.addData(ASSETS_PREFIX + ns + MODELS_ITEM_DIR + id + JSON_EXTENSION, itemJson);
    }

    private void injectLanguageFiles() {
        if (contentService == null || datapackService == null) { return; }
        new LuaAssetsLangGenerator(contentService, datapackService, GSON).injectLanguageFiles();
    }

    private boolean isToolType(String type) {
        if (type == null) { return false; }
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE" -> true;
            default -> false;
        };
    }

    private String[] parseId(String id) {
        if (id != null && id.contains(":")) { return id.split(":", 2); }
        return new String[]{"luatweaker", id != null ? id : "unknown"};
    }

    private boolean isPhysicalAssetPresent(String virtualKey) {
        File direct = new File(luaDir, virtualKey);
        if (direct.exists()) { return true; }

        File luamodsDir = "luamods".equalsIgnoreCase(luaDir.getName()) ? luaDir : new File(luaDir, "luamods");
        if (luamodsDir.exists() && luamodsDir.isDirectory()) {
            File[] mods = luamodsDir.listFiles(File::isDirectory);
            if (mods != null) {
                for (File modFolder : mods) {
                    if (modHasAsset(modFolder, virtualKey)) { return true; }
                }
            }
        }
        return false;
    }

    private static boolean modHasAsset(File modFolder, String virtualKey) {
        File modAssetFile = new File(modFolder, virtualKey);
        return modAssetFile.exists();
    }

    private boolean isTextureAvailable(String tex) {
        if (tex == null || tex.isBlank()) { return false; }
        if (tex.startsWith("minecraft:")) { return true; }
        String[] parts = parseId(tex);
        String ns = parts[0];
        String path = parts[1];
        if (path.startsWith("textures/")) {
            path = path.substring("textures/".length());
        }
        String fileKey = ASSETS_PREFIX + ns + "/textures/" + path + ".png";
        return isPhysicalAssetPresent(fileKey);
    }

    private String fallbackItemTexture(String type) {
        if (type == null) { return "minecraft:item/amethyst_shard"; }
        return switch (type.toUpperCase(java.util.Locale.ROOT)) {
            case "SWORD" -> "minecraft:item/iron_sword";
            case "PICKAXE" -> "minecraft:item/iron_pickaxe";
            case "AXE" -> "minecraft:item/iron_axe";
            case "SHOVEL" -> "minecraft:item/iron_shovel";
            case "HOE" -> "minecraft:item/iron_hoe";
            case "HELMET" -> "minecraft:item/iron_helmet";
            case "CHESTPLATE" -> "minecraft:item/iron_chestplate";
            case "LEGGINGS" -> "minecraft:item/iron_leggings";
            case "BOOTS" -> "minecraft:item/iron_boots";
            default -> "minecraft:item/amethyst_shard";
        };
    }
}
