package com.luatweaker.platform.content;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
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
import java.io.FileWriter;
import java.util.Optional;

/**
 * Registers two virtual packs with Minecraft:
 *
 * <ul>
 *   <li><b>CLIENT_RESOURCES</b> – uses {@link PathPackResources} for assets (textures,
 *       models, blockstates) because rendering needs physical files. Missing asset JSON
 *       files are auto-generated on disk here.</li>
 *   <li><b>SERVER_DATA</b> – uses {@link LuaTweakerVirtualPackResources} (100% in-memory)
 *       to serve loot tables, recipes, advancements, and tags from Lua scripts without
 *       ever writing to disk, exactly like KubeJS.</li>
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
            // Generate any missing JSON model/blockstate files on disk
            generateMissingAssetFiles(assetsDir);

            // Use LuaTweakerVirtualPackResources for CLIENT_RESOURCES as well:
            //   - Provides a dynamic in-memory pack.mcmeta (no physical file required)
            //   - Serves all physical files from lua/assets/ (textures, models, blockstates, sounds)
            //   - Supports OGG files at lua/assets/<ns>/sounds/*.ogg automatically
            PackLocationInfo locationInfo = new PackLocationInfo(
                    "luatweaker_user_assets",
                    Component.literal("LuaTweaker User Assets"),
                    PackSource.BUILT_IN,
                    Optional.empty()
            );

            Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
                @Override
                public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo id) {
                    return new LuaTweakerVirtualPackResources(id, PackType.CLIENT_RESOURCES, luaDir, null);
                }

                @Override
                public net.minecraft.server.packs.PackResources openFull(PackLocationInfo id, Pack.Metadata meta) {
                    return new LuaTweakerVirtualPackResources(id, PackType.CLIENT_RESOURCES, luaDir, null);
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
                        "[LuaTweaker] Mounted Virtual ResourcePack for client assets (in-memory mcmeta + physical files)");
            }

        } else if (event.getPackType() == PackType.SERVER_DATA) {
            // Also auto-inject mining tags (mineable/pickaxe, needs_iron_tool...) into virtualFiles
            // so no physical tag files are needed on disk.
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
                        "[LuaTweaker] Mounted KubeJS-style Virtual DataPack (in-memory, no disk flush)");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Mining / tool tags → inject into virtualFiles (no disk write needed)
    // -----------------------------------------------------------------------

    private void injectBlockTagsToVirtualPack() {
        if (contentService == null || datapackService == null) return;

        // Collect all block entries per tag path
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
            // Merge with existing virtual entries for this tag if any
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
            datapackService.addData(virtualKey.substring("data/".length()), GSON.toJson(json));
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[VirtualPack] Injected tag: " + virtualKey + " → " + values);
        }
    }

    // -----------------------------------------------------------------------
    // Asset generation (client only — textures & models need physical files)
    // -----------------------------------------------------------------------

    private void generateMissingAssetFiles(File assetsDir) {
        if (contentService == null) return;

        // Item models
        for (IItemBuilder b : contentService.getRegisteredItems()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];

            File itemModelFile = new File(assetsDir, ns + "/models/item/" + id + ".json");
            if (!itemModelFile.exists()) {
                itemModelFile.getParentFile().mkdirs();
                String json;
                if (b.getModel() != null) {
                    json = "{\n  \"parent\": \"" + b.getModel() + "\"\n}";
                } else {
                    String parent = isToolType(b.getType()) ? "minecraft:item/handheld" : "minecraft:item/generated";
                    String defaultTex = (id.contains("apple") ? "minecraft:item/apple" : (ns + ":item/" + id));
                    String tex = b.getTexture() != null ? b.getTexture() : defaultTex;
                    json = "{\n  \"parent\": \"" + parent + "\",\n  \"textures\": {\n    \"layer0\": \"" + tex + "\"\n  }\n}";

                }
                writeFile(itemModelFile, json);
                LuaTweakerLog.get().info(LogStage.SYSTEM, "Auto-generated Item Model JSON: " + itemModelFile.getName());
            }
        }

        // Block models and blockstates
        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];

            if (id.endsWith("_stairs")) {
                generateStairsAssets(assetsDir, ns, id);
            } else if (id.endsWith("_slab")) {
                generateSlabAssets(assetsDir, ns, id);
            } else if (id.endsWith("_wall")) {
                generateWallAssets(assetsDir, ns, id);
            } else {
                // Regular block
                File blockstateFile = new File(assetsDir, ns + "/blockstates/" + id + ".json");
                if (!blockstateFile.exists()) {
                    blockstateFile.getParentFile().mkdirs();
                    String targetModel = b.getModel() != null ? b.getModel() : (ns + ":block/" + id);
                    String json = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + targetModel + "\" }\n  }\n}";
                    writeFile(blockstateFile, json);
                }

                if (b.getModel() == null) {
                    File blockModelFile = new File(assetsDir, ns + "/models/block/" + id + ".json");
                    if (!blockModelFile.exists()) {
                        blockModelFile.getParentFile().mkdirs();
                        String tex = b.getTexture() != null ? b.getTexture() : (ns + ":block/" + id);
                        String json = "{\n  \"parent\": \"minecraft:block/cube_all\",\n  \"textures\": {\n    \"all\": \"" + tex + "\"\n  }\n}";
                        writeFile(blockModelFile, json);
                    }
                }

                File itemModelFile = new File(assetsDir, ns + "/models/item/" + id + ".json");
                if (!itemModelFile.exists()) {
                    itemModelFile.getParentFile().mkdirs();
                    String targetModel = b.getModel() != null ? b.getModel() : (ns + ":block/" + id);
                    String json = "{\n  \"parent\": \"" + targetModel + "\"\n}";
                    writeFile(itemModelFile, json);
                }
            }
        }

        generateFluidAssets(assetsDir);
        generateLanguageFiles(assetsDir);
    }

    private void generateFluidAssets(File assetsDir) {
        for (com.luatweaker.api.content.IFluidBuilder b : contentService.getRegisteredFluids()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];
            String blockId = id + "_block";
            String bucketId = id + "_bucket";
            String altBucketId = id.replace("_fluid", "") + "_bucket";

            // Blockstate for liquid block
            File bsFile = new File(assetsDir, ns + "/blockstates/" + blockId + ".json");
            String bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"minecraft:block/water\" }\n  }\n}";
            writeFile(bsFile, bsJson);

            // Bucket item models
            String bucketJson = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"" + ns + ":item/" + bucketId + "\"\n  }\n}";
            writeFile(new File(assetsDir, ns + "/models/item/" + bucketId + ".json"), bucketJson);

            String altBucketJson = "{\n  \"parent\": \"minecraft:item/generated\",\n  \"textures\": {\n    \"layer0\": \"" + ns + ":item/" + altBucketId + "\"\n  }\n}";
            writeFile(new File(assetsDir, ns + "/models/item/" + altBucketId + ".json"), altBucketJson);
        }
    }


    private void generateSlabAssets(File assetsDir, String ns, String id) {
        String base = ns + ":block/" + id.replace("_slab", "_block");

        // Blockstate
        File blockstateFile = new File(assetsDir, ns + "/blockstates/" + id + ".json");
        String bsJson = "{\n" +
                "  \"variants\": {\n" +
                "    \"type=bottom\": { \"model\": \"" + ns + ":block/" + id + "\" },\n" +
                "    \"type=double\": { \"model\": \"" + base + "\" },\n" +
                "    \"type=top\": { \"model\": \"" + ns + ":block/" + id + "_top\" }\n" +
                "  }\n" +
                "}";
        writeFile(blockstateFile, bsJson);

        // Models
        File bottomModel = new File(assetsDir, ns + "/models/block/" + id + ".json");
        String bJson = "{\n  \"parent\": \"minecraft:block/slab\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        writeFile(bottomModel, bJson);

        File topModel = new File(assetsDir, ns + "/models/block/" + id + "_top.json");
        String tJson = "{\n  \"parent\": \"minecraft:block/slab_top\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        writeFile(topModel, tJson);

        File itemModel = new File(assetsDir, ns + "/models/item/" + id + ".json");
        String iJson = "{\n  \"parent\": \"" + ns + ":block/" + id + "\"\n}";
        writeFile(itemModel, iJson);
    }

    private void generateStairsAssets(File assetsDir, String ns, String id) {
        String base = ns + ":block/" + id.replace("_stairs", "_block");

        // Blockstate
        File templateBs = new File("C:/Users/kien/Downloads/1.21.1-Template/assets/minecraft/blockstates/sandstone_stairs.json");
        String bsJson;
        if (templateBs.exists()) {
            try {
                bsJson = java.nio.file.Files.readString(templateBs.toPath())
                        .replace("minecraft:block/sandstone_stairs_inner", ns + ":block/" + id + "_inner")
                        .replace("minecraft:block/sandstone_stairs_outer", ns + ":block/" + id + "_outer")
                        .replace("minecraft:block/sandstone_stairs", ns + ":block/" + id);
            } catch (Exception e) {
                bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + ns + ":block/" + id + "\" }\n  }\n}";
            }
        } else {
            bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + ns + ":block/" + id + "\" }\n  }\n}";
        }
        writeFile(new File(assetsDir, ns + "/blockstates/" + id + ".json"), bsJson);

        // Models
        String straightJson = "{\n  \"parent\": \"minecraft:block/stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        writeFile(new File(assetsDir, ns + "/models/block/" + id + ".json"), straightJson);

        String innerJson = "{\n  \"parent\": \"minecraft:block/inner_stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        writeFile(new File(assetsDir, ns + "/models/block/" + id + "_inner.json"), innerJson);

        String outerJson = "{\n  \"parent\": \"minecraft:block/outer_stairs\",\n  \"textures\": {\n    \"bottom\": \"" + base + "\",\n    \"top\": \"" + base + "\",\n    \"side\": \"" + base + "\"\n  }\n}";
        writeFile(new File(assetsDir, ns + "/models/block/" + id + "_outer.json"), outerJson);

        String itemJson = "{\n  \"parent\": \"" + ns + ":block/" + id + "\"\n}";
        writeFile(new File(assetsDir, ns + "/models/item/" + id + ".json"), itemJson);
    }

    private void generateWallAssets(File assetsDir, String ns, String id) {
        String base = ns + ":block/" + id.replace("_wall", "_block");

        // Blockstate
        File templateBs = new File("C:/Users/kien/Downloads/1.21.1-Template/assets/minecraft/blockstates/sandstone_wall.json");
        String bsJson;
        if (templateBs.exists()) {
            try {
                bsJson = java.nio.file.Files.readString(templateBs.toPath())
                        .replace("minecraft:block/sandstone_wall_post", ns + ":block/" + id + "_post")
                        .replace("minecraft:block/sandstone_wall_side_tall", ns + ":block/" + id + "_side_tall")
                        .replace("minecraft:block/sandstone_wall_side", ns + ":block/" + id + "_side");
            } catch (Exception e) {
                bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + ns + ":block/" + id + "_post\" }\n  }\n}";
            }
        } else {
            bsJson = "{\n  \"variants\": {\n    \"\": { \"model\": \"" + ns + ":block/" + id + "_post\" }\n  }\n}";
        }
        writeFile(new File(assetsDir, ns + "/blockstates/" + id + ".json"), bsJson);

        // Models
        String postJson = "{\n  \"parent\": \"minecraft:block/template_wall_post\",\n  \"textures\": {\n    \"wall\": \"" + base + "\"\n  }\n}";
        writeFile(new File(assetsDir, ns + "/models/block/" + id + "_post.json"), postJson);

        String sideJson = "{\n  \"parent\": \"minecraft:block/template_wall_side\",\n  \"textures\": {\n    \"wall\": \"" + base + "\"\n  }\n}";
        writeFile(new File(assetsDir, ns + "/models/block/" + id + "_side.json"), sideJson);

        String tallJson = "{\n  \"parent\": \"minecraft:block/template_wall_side_tall\",\n  \"textures\": {\n    \"wall\": \"" + base + "\"\n  }\n}";
        writeFile(new File(assetsDir, ns + "/models/block/" + id + "_side_tall.json"), tallJson);

        String itemJson = "{\n  \"parent\": \"minecraft:block/wall_inventory\",\n  \"textures\": {\n    \"wall\": \"" + base + "\"\n  }\n}";
        writeFile(new File(assetsDir, ns + "/models/item/" + id + ".json"), itemJson);
    }



    private void generateLanguageFiles(File assetsDir) {
        if (contentService == null) return;
        File langFile = new File(assetsDir, "luatweaker/lang/en_us.json");
        JsonObject langJson = new JsonObject();
        if (langFile.exists()) {
            try (java.io.FileReader reader = new java.io.FileReader(langFile)) {
                JsonElement el = com.google.gson.JsonParser.parseReader(reader);
                if (el != null && el.isJsonObject()) {
                    langJson = el.getAsJsonObject();
                }
            } catch (Exception ignored) {}
        }

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


        for (com.luatweaker.api.content.ICreativeTabBuilder t : contentService.getRegisteredTabs()) {
            String[] parts = parseId(t.getId());
            String ns = parts[0], id = parts[1];
            String name = t.getTitle() != null ? stripEmojis(t.getTitle()) : capitalize(id);
            langJson.addProperty("itemGroup." + ns + "." + id, name);
        }

        for (com.luatweaker.api.content.IFluidBuilder b : contentService.getRegisteredFluids()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0], id = parts[1];
            String name = capitalize(id.replace("_fluid", "")) + " Liquid";
            langJson.addProperty("fluid." + ns + "." + id, name);
            langJson.addProperty("item." + ns + "." + id + "_bucket", name + " Bucket");
            langJson.addProperty("item." + ns + "." + id.replace("_fluid", "") + "_bucket", name + " Bucket");
            langJson.addProperty("block." + ns + "." + id + "_block", name + " Block");
        }


        langFile.getParentFile().mkdirs();
        writeFile(langFile, GSON.toJson(langJson));
        LuaTweakerLog.get().info(LogStage.SYSTEM, "Updated en_us.json language file: " + langFile.getName());
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


    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

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

    private void writeFile(File file, String content) {
        try (FileWriter writer = new FileWriter(file)) {
            writer.write(content);
        } catch (Exception e) {
            LuaTweakerLog.get().error(LogStage.SYSTEM,
                    "Failed to write asset file " + file.getAbsolutePath() + ": " + e.getMessage());
        }
    }
}
