package com.luatweaker.common.content;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.luatweaker.api.content.IBlockBuilder;
import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.ICreativeTabBuilder;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.content.IEntityBuilder;
import com.luatweaker.api.content.IFluidBuilder;
import com.luatweaker.api.content.IItemBuilder;
import com.luatweaker.api.content.MobEffectDefinition;
import com.luatweaker.content.EffectRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Generates virtual language assets (en_us.json) for custom items, blocks, fluids, tabs, and effects.
 */
final class LuaAssetsLangGenerator {

    private static final String LANG_ITEM_PREFIX = "item.";
    private static final String BUCKET_SUFFIX = "_bucket";

    private final IContentService contentService;
    private final IDatapackService datapackService;
    private final Gson gson;

    LuaAssetsLangGenerator(@NotNull IContentService contentService,
                           @NotNull IDatapackService datapackService,
                           @NotNull Gson gson) {
        this.contentService = contentService;
        this.datapackService = datapackService;
        this.gson = gson;
    }

    void injectLanguageFiles() {
        JsonObject langJson = new JsonObject();
        addItemLangEntries(langJson);
        addBlockLangEntries(langJson);
        addTabLangEntries(langJson);
        addFluidLangEntries(langJson);
        addEntityLangEntries(langJson);
        addMobEffectLangEntries(langJson);
        datapackService.addData("assets/luatweaker/lang/en_us.json", gson.toJson(langJson));
    }

    private void addMobEffectLangEntries(JsonObject langJson) {
        for (MobEffectDefinition d : EffectRegistry.getMobEffects().values()) {
            String[] parts = parseId(d.id());
            String ns = parts[0];
            String id = parts[1];
            String name = d.displayName() != null && !d.displayName().isBlank()
                    ? stripEmojis(d.displayName()) : capitalize(id);
            langJson.addProperty("effect." + ns + "." + id, name);
        }
    }

    private void addItemLangEntries(JsonObject langJson) {
        for (IItemBuilder b : contentService.getRegisteredItems()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0];
            String id = parts[1];
            String name = b.getDisplayName() != null ? stripEmojis(b.getDisplayName()) : capitalize(id);
            langJson.addProperty(LANG_ITEM_PREFIX + ns + "." + id, name);
        }
    }

    private void addBlockLangEntries(JsonObject langJson) {
        for (IBlockBuilder b : contentService.getRegisteredBlocks()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0];
            String id = parts[1];
            String name = capitalize(id);
            langJson.addProperty("block." + ns + "." + id, name);
            langJson.addProperty(LANG_ITEM_PREFIX + ns + "." + id, name);
        }
    }

    private void addTabLangEntries(JsonObject langJson) {
        for (ICreativeTabBuilder t : contentService.getRegisteredTabs()) {
            String[] parts = parseId(t.getId());
            String ns = parts[0];
            String id = parts[1];
            String name = t.getTitle() != null ? stripEmojis(t.getTitle()) : capitalize(id);
            langJson.addProperty("itemGroup." + ns + "." + id, name);
        }
    }

    private void addFluidLangEntries(JsonObject langJson) {
        for (IFluidBuilder b : contentService.getRegisteredFluids()) {
            String[] parts = parseId(b.getId());
            String ns = parts[0];
            String id = parts[1];
            String name = b.getDisplayName() != null ? stripEmojis(b.getDisplayName()) : capitalize(id.replace("_fluid", "")) + " Liquid";
            String bucketName = name.toLowerCase(Locale.ROOT).contains("bucket") ? name : name + " Bucket";
            langJson.addProperty("fluid." + ns + "." + id, name);
            langJson.addProperty("fluid_type." + ns + "." + id, name);
            langJson.addProperty(LANG_ITEM_PREFIX + ns + "." + id + BUCKET_SUFFIX, bucketName);
            langJson.addProperty(LANG_ITEM_PREFIX + ns + "." + id.replace("_fluid", "") + BUCKET_SUFFIX, bucketName);
            langJson.addProperty("block." + ns + "." + id + "_block", name + " Block");
        }
    }

    private void addEntityLangEntries(JsonObject langJson) {
        for (IEntityBuilder e : contentService.getRegisteredEntities()) {
            String[] parts = parseId(e.getId());
            String ns = parts[0];
            String id = parts[1];
            String name = capitalize(id);
            langJson.addProperty("entity." + ns + "." + id, name);
            if (e.hasSpawnEgg()) {
                langJson.addProperty(LANG_ITEM_PREFIX + ns + "." + id + "_spawn_egg", name + " Spawn Egg");
            }
        }
    }

    private static String stripEmojis(String text) {
        if (text == null) { return ""; }
        return text.replaceAll("[\uD83C-\uDBFF\uDC00-\uDFFF\u2600-\u27BF]", "").trim();
    }

    private static String capitalize(String str) {
        if (str == null || str.isBlank()) { return ""; }
        String[] words = str.replace("_", " ").split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static String[] parseId(String raw) {
        int colon = raw.indexOf(':');
        if (colon > 0 && colon < raw.length() - 1) {
            return new String[]{raw.substring(0, colon), raw.substring(colon + 1)};
        }
        return new String[]{"luatweaker", raw};
    }
}
