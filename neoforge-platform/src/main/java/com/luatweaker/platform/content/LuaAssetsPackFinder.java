package com.luatweaker.platform.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.luatweaker.api.content.*;
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

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

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
        if (!assetsDir.exists()) assetsDir.mkdirs();

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

    // -----------------------------------------------------------------------
    // Mining / tool tags → inject into virtualFiles (RAM)
    // -----------------------------------------------------------------------

    private void injectBlockTagsToVirtualPack() {
        if (contentService == null || datapackService == null) return;

        java.util.Map<String, JsonArray> tagMap = new java.util.LinkedHashMap<>();

        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            String[] parts = parseId(b.getId());
            String fullId = parts[0] + ":" + parts[1];

            if (b.getMineableWith() != null) {
                String tool = b.getMineableWith().toLowerCase();
                String tagPath = "tags/block/mineable/" + tool + ".json";
                tagMap.computeIfAbsent(tagPath, k -> new JsonArray()).add(fullId);
            }

            if (b.getMiningLevel() > 0) {
                String levelTag = switch (b.getMiningLevel()) {
                    case 1 -> "tags/block/needs_stone_tool.json";
                    case 2 -> "tags/block/needs_iron_tool.json";
                    case 3 -> "tags/block/needs_diamond_tool.json";
                    case 4 -> "tags/block/needs_netherite_tool.json";
                    default -> "tags/block/needs_stone_tool.json";
                };
                tagMap.computeIfAbsent(levelTag, k -> new JsonArray()).add(fullId);
            }

            if (parts[1].endsWith("_wall")) {
                tagMap.computeIfAbsent("tags/block/walls.json", k -> new JsonArray()).add(fullId);
            }
            if (parts[1].endsWith("_stairs")) {
                tagMap.computeIfAbsent("tags/block/stairs.json", k -> new JsonArray()).add(fullId);
            }
            if (parts[1].endsWith("_slab")) {
                tagMap.computeIfAbsent("tags/block/slabs.json", k -> new JsonArray()).add(fullId);
            }
        }

        for (java.util.Map.Entry<String, JsonArray> entry : tagMap.entrySet()) {
            String virtualKey = "data/minecraft/" + entry.getKey();
            String existing = datapackService.getVirtualFiles().get(virtualKey);
            JsonObject json = new JsonObject();
            json.addProperty("replace", false);
            JsonArray values = existing != null
                    ? com.google.gson.JsonParser.parseString(existing).getAsJsonObject().getAsJsonArray("values")
                    : new JsonArray();
            for (var elem : entry.getValue()) {
                boolean found = false;
                for (var v : values) if (v.getAsString().equals(elem.getAsString())) { found = true; break; }
                if (!found) values.add(elem);
            }
            json.add("values", values);
            datapackService.addData(virtualKey, GSON.toJson(json));
        }
    }

    // -----------------------------------------------------------------------
    // Asset generation (In-Memory RAM — Zero Write to Disk)
    // -----------------------------------------------------------------------

    private void injectVirtualAssetFiles() {
        if (contentService == null || datapackService == null) return;

        // 1. Item models
        for (IItemBuilder b : contentService.getRegisteredItems()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];
            String virtualKey = "assets/" + ns + "/models/item/" + id + ".json";

            // If user provided physical file on disk under lua/assets/ or luamods/<mod_id>/assets/, don't override it
            if (isPhysicalAssetPresent(virtualKey)) {
                continue;
            }

            String json;
            if (b.getModel() != null && !b.getModel().isBlank()) {
                String targetModel = b.getModel();
                // Prevent circular reference if model points to itself (e.g. "luatweaker:item/magic_staff")
                if (targetModel.equals(ns + ":item/" + id) || targetModel.equals(id) || targetModel.endsWith("/" + id)) {
                    targetModel = isToolType(b.getType()) ? "minecraft:item/handheld" : "minecraft:item/generated";
                }
                json = "{\n  \"parent\": \"" + targetModel + "\"\n}";
            } else {
                String parent = isToolType(b.getType()) ? "minecraft:item/handheld" : "minecraft:item/generated";
                String defaultTex = (id.contains("apple") ? "minecraft:item/apple" : (ns + ":item/" + id));
                String tex = b.getTexture() != null ? b.getTexture() : defaultTex;
                json = "{\n  \"parent\": \"" + parent + "\",\n  \"textures\": {\n    \"layer0\": \"" + tex + "\"\n  }\n}";
            }
            datapackService.addData(virtualKey, json);
        }

        // 2. Entity Spawn Egg Item Models
        for (IEntityBuilder e : contentService.getRegisteredEntities()) {
            String[] parts = parseId(e.getId());
            String ns = parts[0], id = parts[1];
            if (e.hasSpawnEgg()) {
                String virtualKey = "assets/" + ns + "/models/item/" + id + "_spawn_egg.json";
                if (isPhysicalAssetPresent(virtualKey)) continue;
                String eggJson;
                if (e.getSpawnEggTexture() != null && !e.getSpawnEggTexture().isBlank()) {
                    eggJson = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"" + e.getSpawnEggTexture() + "\"\n  }\n}";
                } else {
                    eggJson = "{\n  \"parent\": \"minecraft:item/template_spawn_egg\"\n}";
                }
                datapackService.addData(virtualKey, eggJson);
            }
        }

        // 3. Block models and blockstates
        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];

            if (id.endsWith("_stairs")) {
                injectStairsAssets(ns, id);
            } else if (id.endsWith("_slab")) {
                injectSlabAssets(ns, id);
            } else if (id.endsWith("_wall")) {
                injectWallAssets(ns, id);
            } else {
                // Regular block state
                String bsKey = "assets/" + ns + "/blockstates/" + id + ".json";
                if (!isPhysicalAssetPresent(bsKey)) {
                    String targetModel = b.getModel() != null ? b.getModel() : (ns + ":block/" + id);
                    String bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + targetModel + "\" }\n  }\n}";
                    datapackService.addData(bsKey, bsJson);
                }

                if (b.getModel() == null) {
                    String bmKey = "assets/" + ns + "/models/block/" + id + ".json";
                    if (!isPhysicalAssetPresent(bmKey)) {
                        String tex = b.getTexture() != null ? b.getTexture() : (ns + ":block/" + id);
                        String bmJson = "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \"" + tex + "\"\n  }\n}";
                        datapackService.addData(bmKey, bmJson);
                    }
                }

                String imKey = "assets/" + ns + "/models/item/" + id + ".json";
                if (!isPhysicalAssetPresent(imKey)) {
                    String targetModel = b.getModel() != null ? b.getModel() : (ns + ":block/" + id);
                    String imJson = "{\n  \"parent\": \"" + targetModel + "\"\n}";
                    datapackService.addData(imKey, imJson);
                }
            }
        }

        // 4. Fluids & Language
        injectFluidAssets();
        injectLanguageFiles();
    }

    private void injectFluidAssets() {
        for (IFluidBuilder b : contentService.getRegisteredFluids()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];
            String blockId = id + "_block";
            String bucketId = id + "_bucket";
            String altBucketId = id.replace("_fluid", "") + "_bucket";

            String stillTex = b.getStillTexture() != null
                    ? b.getStillTexture()
                    : "minecraft:block/water_still";

            // Auto-inject .mcmeta animation metadata for fluid textures so vertical sprite strips animate smoothly without stripes
            String animMcmeta = "{\n  \"animation\": {\n    \"frametime\": 2\n  }\n}";
            if (b.getStillTexture() != null) {
                String[] sParts = parseId(b.getStillTexture());
                String path = sParts[1].replace("block/", "");
                datapackService.addData("assets/" + sParts[0] + "/textures/block/" + path + ".png.mcmeta", animMcmeta);
            }
            if (b.getFlowingTexture() != null) {
                String[] fParts = parseId(b.getFlowingTexture());
                String path = fParts[1].replace("block/", "");
                datapackService.addData("assets/" + fParts[0] + "/textures/block/" + path + ".png.mcmeta", animMcmeta);
            }

            // Fluid Blockstates (both <id>.json and <id>_block.json for safety)
            String bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + ns + ":block/" + id + "\" }\n  }\n}";
            datapackService.addData("assets/" + ns + "/blockstates/" + id + ".json", bsJson);
            datapackService.addData("assets/" + ns + "/blockstates/" + blockId + ".json", bsJson);

            // Fluid Block Model
            String bmJson = "{\n  \"textures\": {\n    \"particle\": \"" + stillTex + "\"\n  }\n}";
            datapackService.addData("assets/" + ns + "/models/block/" + id + ".json", bmJson);
            datapackService.addData("assets/" + ns + "/models/block/" + blockId + ".json", bmJson);

            // Fluid Bucket Item Models using official NeoForge fluid_container loader
            String bucketJson = "{\n"
                    + "  \"loader\": \"neoforge:fluid_container\",\n"
                    + "  \"parent\": \"neoforge:item/bucket\",\n"
                    + "  \"fluid\": \"" + ns + ":" + id + "\"\n"
                    + "}";
            datapackService.addData("assets/" + ns + "/models/item/" + bucketId + ".json", bucketJson);
            datapackService.addData("assets/" + ns + "/models/item/" + altBucketId + ".json", bucketJson);
        }
    }

    private void injectSlabAssets(String ns, String id) {
        String base = ns + ":block/" + id.replace("_slab", "_block");

        String bsJson = "{\n" +
                "  \"variants\": {\n" +
                "    \"type=bottom\": { \"model\": \"" + ns + ":block/" + id + "\" },\n" +
                "    \"type=double\": { \"model\": \"" + base + "\" },\n" +
                "    \"type=top\": { \"model\": \"" + ns + ":block/" + id + "_top\" }\n" +
                "  }\n" +
                "}";
        datapackService.addData("assets/" + ns + "/blockstates/" + id + ".json", bsJson);

        String bJson = "{\n  \"parent\": \"minecraft:block/slab\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/block/" + id + ".json", bJson);

        String tJson = "{\n  \"parent\": \"minecraft:block/slab_top\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/block/" + id + "_top.json", tJson);

        String iJson = "{\n  \"parent\": \"" + ns + ":block/" + id + "\"\n}";
        datapackService.addData("assets/" + ns + "/models/item/" + id + ".json", iJson);
    }

    private void injectStairsAssets(String ns, String id) {
        String base = ns + ":block/" + id.replace("_stairs", "_block");
        String mStraight = ns + ":block/" + id;
        String mInner = ns + ":block/" + id + "_inner";
        String mOuter = ns + ":block/" + id + "_outer";

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
        datapackService.addData("assets/" + ns + "/blockstates/" + id + ".json", GSON.toJson(bsObj));

        String straightJson = "{\n  \"parent\": \"minecraft:block/stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/block/" + id + ".json", straightJson);

        String innerJson = "{\n  \"parent\": \"minecraft:block/inner_stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/block/" + id + "_inner.json", innerJson);

        String outerJson = "{\n  \"parent\": \"minecraft:block/outer_stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/block/" + id + "_outer.json", outerJson);

        String itemJson = "{\n  \"parent\": \"" + ns + ":block/" + id + "\"\n}";
        datapackService.addData("assets/" + ns + "/models/item/" + id + ".json", itemJson);
    }

    private void addStairVar(JsonObject variants, String key, String model, int x, int y) {
        JsonObject v = new JsonObject();
        v.addProperty("model", model);
        if (x > 0 || y > 0) {
            v.addProperty("uvlock", true);
        }
        if (x > 0) v.addProperty("x", x);
        if (y > 0) v.addProperty("y", y);
        variants.add(key, v);
    }

    private void injectWallAssets(String ns, String id) {
        String base = ns + ":block/" + id.replace("_wall", "_block");

        JsonArray multipart = new JsonArray();

        // Post
        JsonObject postCase = new JsonObject();
        JsonObject postWhen = new JsonObject();
        postWhen.addProperty("up", true);
        postCase.add("when", postWhen);
        JsonObject postApply = new JsonObject();
        postApply.addProperty("model", ns + ":block/" + id + "_post");
        postCase.add("apply", postApply);
        multipart.add(postCase);

        String[] sides = {"north", "east", "south", "west"};
        int[] yRots = {0, 90, 180, 270};

        for (int i = 0; i < 4; i++) {
            String side = sides[i];
            int y = yRots[i];

            // Low
            JsonObject lowCase = new JsonObject();
            JsonObject lowWhen = new JsonObject();
            lowWhen.addProperty(side, "low");
            lowCase.add("when", lowWhen);
            JsonObject lowApply = new JsonObject();
            lowApply.addProperty("model", ns + ":block/" + id + "_side");
            if (y > 0) lowApply.addProperty("y", y);
            lowApply.addProperty("uvlock", true);
            lowCase.add("apply", lowApply);
            multipart.add(lowCase);

            // Tall
            JsonObject tallCase = new JsonObject();
            JsonObject tallWhen = new JsonObject();
            tallWhen.addProperty(side, "tall");
            tallCase.add("when", tallWhen);
            JsonObject tallApply = new JsonObject();
            tallApply.addProperty("model", ns + ":block/" + id + "_side_tall");
            if (y > 0) tallApply.addProperty("y", y);
            tallApply.addProperty("uvlock", true);
            tallCase.add("apply", tallApply);
            multipart.add(tallCase);
        }

        JsonObject bsObj = new JsonObject();
        bsObj.add("multipart", multipart);
        datapackService.addData("assets/" + ns + "/blockstates/" + id + ".json", GSON.toJson(bsObj));

        String postJson = "{\n  \"parent\": \"minecraft:block/template_wall_post\",\n  \"textures\": {\n    \"wall\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/block/" + id + "_post.json", postJson);

        String sideJson = "{\n  \"parent\": \"minecraft:block/template_wall_side\",\n  \"textures\": {\n    \"wall\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/block/" + id + "_side.json", sideJson);

        String tallJson = "{\n  \"parent\": \"minecraft:block/template_wall_side_tall\",\n  \"textures\": {\n    \"wall\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/block/" + id + "_side_tall.json", tallJson);

        String itemJson = "{\n  \"parent\": \"minecraft:block/wall_inventory\",\n  \"textures\": {\n    \"wall\": \"" + base + "\"\n  }\n}";
        datapackService.addData("assets/" + ns + "/models/item/" + id + ".json", itemJson);
    }

    private void injectLanguageFiles() {
        if (contentService == null || datapackService == null) return;
        JsonObject langJson = new JsonObject();

        for (IItemBuilder b : contentService.getRegisteredItems()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];
            String name = b.getDisplayName() != null ? stripEmojis(b.getDisplayName()) : capitalize(id);
            langJson.addProperty("item." + ns + "." + id, name);
        }

        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];
            String name = capitalize(id);
            langJson.addProperty("block." + ns + "." + id, name);
            langJson.addProperty("item." + ns + "." + id, name);
        }

        for (ICreativeTabBuilder t : contentService.getRegisteredTabs()) {
            String[] parts = parseId(t.getId());
            String ns = parts[0], id = parts[1];
            String name = t.getTitle() != null ? stripEmojis(t.getTitle()) : capitalize(id);
            langJson.addProperty("itemGroup." + ns + "." + id, name);
        }

        for (IFluidBuilder b : contentService.getRegisteredFluids()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];
            String name = b.getDisplayName() != null ? stripEmojis(b.getDisplayName()) : capitalize(id.replace("_fluid", "")) + " Liquid";
            String bucketName = name.toLowerCase().contains("bucket") ? name : name + " Bucket";
            langJson.addProperty("fluid." + ns + "." + id, name);
            langJson.addProperty("fluid_type." + ns + "." + id, name);
            langJson.addProperty("item." + ns + "." + id + "_bucket", bucketName);
            langJson.addProperty("item." + ns + "." + id.replace("_fluid", "") + "_bucket", bucketName);
            langJson.addProperty("block." + ns + "." + id + "_block", name + " Block");
        }

        for (IEntityBuilder e : contentService.getRegisteredEntities()) {
            String[] parts = parseId(e.getId());
            String ns = parts[0], id = parts[1];
            String name = capitalize(id);
            langJson.addProperty("entity." + ns + "." + id, name);
            if (e.hasSpawnEgg()) {
                langJson.addProperty("item." + ns + "." + id + "_spawn_egg", name + " Spawn Egg");
            }
        }

        datapackService.addData("assets/luatweaker/lang/en_us.json", GSON.toJson(langJson));
    }

    private String stripEmojis(String text) {
        if (text == null) return "";
        return text.replaceAll("[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]", "").trim();
    }

    private String capitalize(String str) {
        if (str == null || str.isBlank()) return "";
        String[] words = str.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private boolean isToolType(String type) {
        if (type == null) return false;
        return switch (type.toUpperCase()) {
            case "SWORD", "PICKAXE", "AXE", "SHOVEL", "HOE" -> true;
            default -> false;
        };
    }

    private String[] parseId(String id) {
        if (id != null && id.contains(":")) return id.split(":", 2);
        return new String[]{"luatweaker", id != null ? id : "unknown"};
    }

    private boolean isPhysicalAssetPresent(String virtualKey) {
        File direct = new File(luaDir, virtualKey);
        if (direct.exists()) return true;

        File luamodsDir = "luamods".equalsIgnoreCase(luaDir.getName()) ? luaDir : new File(luaDir, "luamods");
        if (luamodsDir.exists() && luamodsDir.isDirectory()) {
            File[] mods = luamodsDir.listFiles(File::isDirectory);
            if (mods != null) {
                for (File modFolder : mods) {
                    File modAssetFile = new File(modFolder, virtualKey);
                    if (modAssetFile.exists()) return true;
                }
            }
        }
        return false;
    }
}
