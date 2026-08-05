package com.luatweaker.platform.recipe;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;

import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class InterceptionHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(InterceptionHelper.class);

    /** Pending villager trades to apply via VillagerTradesEvent */
    private static final List<NeoForgeRecipeManager.TradeData> PENDING_TRADES = new CopyOnWriteArrayList<>();

    /** Pending brewing recipes to apply via BrewingRecipeEvent / RecipeAddedEvent */
    private static final List<NeoForgeRecipeManager.BrewingData> PENDING_BREWING = new CopyOnWriteArrayList<>();

    /** Pending anvil operations (stored for inspection; anvil has no vanilla recipe type) */
    private static final List<NeoForgeRecipeManager.AnvilData> PENDING_ANVIL = new CopyOnWriteArrayList<>();

    public static List<NeoForgeRecipeManager.TradeData> getPendingTrades() { return Collections.unmodifiableList(PENDING_TRADES); }
    public static List<NeoForgeRecipeManager.BrewingData> getPendingBrewing() { return Collections.unmodifiableList(PENDING_BREWING); }
    public static List<NeoForgeRecipeManager.AnvilData> getPendingAnvil() { return Collections.unmodifiableList(PENDING_ANVIL); }

    public static void clearPending() {
        PENDING_TRADES.clear();
        PENDING_BREWING.clear();
        PENDING_ANVIL.clear();
    }

    public static void populatePendingEvents(List<NeoForgeRecipeManager.RecipeModification> modifications) {
        clearPending();
        for (NeoForgeRecipeManager.RecipeModification mod : modifications) {
            switch (mod.type()) {
                case ADD_ANVIL   -> schedulePendingAnvil((NeoForgeRecipeManager.AnvilData) mod.data());
                case ADD_BREWING -> schedulePendingBrewing((NeoForgeRecipeManager.BrewingData) mod.data());
                case ADD_TRADE   -> schedulePendingTrade((NeoForgeRecipeManager.TradeData) mod.data());
                default -> {}
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void applyModifications(RecipeManager recipeManager, List<NeoForgeRecipeManager.RecipeModification> modifications) {
        try {
            com.luatweaker.api.log.LuaTweakerLog.get().info(com.luatweaker.api.log.LogStage.RECIPE_APPLY, "Inspecting RecipeManager fields:");
            for (Field f : RecipeManager.class.getDeclaredFields()) {
                f.setAccessible(true);
                Object val = f.get(recipeManager);
                com.luatweaker.api.log.LuaTweakerLog.get().info(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "Field: " + f.getName() + " | Type: " + f.getType().getName() + " | ValClass: " + (val != null ? val.getClass().getName() : "null")
                );
            }

            Field byTypeField = findRecipesByTypeField(recipeManager);
            Field byNameField = findByNameMapField(recipeManager);

            if (byTypeField == null || byNameField == null) {
                com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "Cannot find RecipeManager fields: byType=" + byTypeField + " byName=" + byNameField
                );
                return;
            }
            if (byTypeField.equals(byNameField)) {
                com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "byType and byName fields are the same field, aborting."
                );
                return;
            }

            byTypeField.setAccessible(true);
            byNameField.setAccessible(true);

            Object byTypeRaw = byTypeField.get(recipeManager);
            Object byNameRaw = byNameField.get(recipeManager);

            // Extract byName map dynamically
            Map<ResourceLocation, RecipeHolder<?>> byName = new HashMap<>();
            if (byNameRaw instanceof Map<?, ?> rawByName) {
                for (Map.Entry<?, ?> entry : rawByName.entrySet()) {
                    Object rawKey = entry.getKey();
                    Object rawVal = entry.getValue();
                    if (rawVal instanceof RecipeHolder<?> holder) {
                        ResourceLocation id;
                        if (rawKey instanceof ResourceLocation rl) {
                            id = rl;
                        } else {
                            id = holder.id();
                        }
                        byName.put(id, holder);
                    }
                }
            }

            // Extract byType map dynamically without assuming concrete generic keys
            Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> baseRecipes = new HashMap<>();
            if (byTypeRaw instanceof Multimap<?, ?> rawMulti) {
                for (Map.Entry<?, ?> entry : rawMulti.entries()) {
                    Object rawKey = entry.getKey();
                    Object rawVal = entry.getValue();
                    if (rawVal instanceof RecipeHolder<?> holder) {
                        RecipeType<?> type = null;
                        if (rawKey instanceof RecipeType<?> rt) {
                            type = rt;
                        } else if (rawKey instanceof ResourceLocation rl) {
                            type = BuiltInRegistries.RECIPE_TYPE.get(rl);
                        }
                        if (type != null) {
                            baseRecipes.computeIfAbsent(type, k -> new HashMap<>()).put(holder.id(), holder);
                        }
                    }
                }
            } else if (byTypeRaw instanceof Map<?, ?> rawMap) {
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    Object rawKey = entry.getKey();
                    Object rawVal = entry.getValue();
                    RecipeType<?> type = null;
                    if (rawKey instanceof RecipeType<?> rt) {
                        type = rt;
                    } else if (rawKey instanceof ResourceLocation rl) {
                        type = BuiltInRegistries.RECIPE_TYPE.get(rl);
                    }
                    if (type != null) {
                        Map<ResourceLocation, RecipeHolder<?>> innerMap = baseRecipes.computeIfAbsent(type, k -> new HashMap<>());
                        if (rawVal instanceof Map<?, ?> valMap) {
                            for (Object v : valMap.values()) {
                                if (v instanceof RecipeHolder<?> holder) {
                                    innerMap.put(holder.id(), holder);
                                }
                            }
                        } else if (rawVal instanceof Collection<?> valCol) {
                            for (Object v : valCol) {
                                if (v instanceof RecipeHolder<?> holder) {
                                    innerMap.put(holder.id(), holder);
                                }
                            }
                        }
                    }
                }
            } else {
                com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "Unexpected byType type: " + (byTypeRaw != null ? byTypeRaw.getClass().getName() : "null")
                );
            }

            Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> newRecipes = new HashMap<>();
            for (Map.Entry<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> entry : baseRecipes.entrySet()) {
                newRecipes.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
            HolderLookup.Provider registries = findRegistries(recipeManager);
            Map<ResourceLocation, RecipeHolder<?>> newByName = new HashMap<>(byName);

            for (NeoForgeRecipeManager.RecipeModification mod : modifications) {
                switch (mod.type()) {
                    case REMOVE_BY_OUTPUT   -> removeByOutput(newRecipes, newByName, (String) mod.data(), registries);
                    case REMOVE_BY_INPUT    -> removeByInput(newRecipes, newByName, (String) mod.data());
                    case REMOVE_BY_ID       -> removeById(newRecipes, newByName, (String) mod.data());
                    case REMOVE_BY_MOD      -> removeByMod(newRecipes, newByName, (String) mod.data());
                    case REMOVE_BY_TAG      -> removeByTag(newRecipes, newByName, (String) mod.data(), registries);
                    case REMOVE_ALL         -> { newRecipes.clear(); newByName.clear(); }
                    case ADD_SHAPELESS      -> addShapeless(newRecipes, newByName, (NeoForgeRecipeManager.ShapelessData) mod.data());
                    case ADD_SHAPED         -> addShaped(newRecipes, newByName, (NeoForgeRecipeManager.ShapedData) mod.data());
                    case REPLACE_INPUT      -> replaceInput(newRecipes, newByName, (NeoForgeRecipeManager.ReplacementData) mod.data(), registries);
                    case REPLACE_OUTPUT     -> replaceOutput(newRecipes, newByName, (NeoForgeRecipeManager.ReplacementData) mod.data(), registries);
                    case ADD_SMELTING       -> addSmelting(newRecipes, newByName, (NeoForgeRecipeManager.CookingData) mod.data());
                    case ADD_BLASTING       -> addBlasting(newRecipes, newByName, (NeoForgeRecipeManager.CookingData) mod.data());
                    case ADD_SMOKING        -> addSmoking(newRecipes, newByName, (NeoForgeRecipeManager.CookingData) mod.data());
                    case ADD_CAMPFIRE       -> addCampfire(newRecipes, newByName, (NeoForgeRecipeManager.CookingData) mod.data());
                    case ADD_STONECUTTING   -> addStonecutting(newRecipes, newByName, (NeoForgeRecipeManager.StonecuttingData) mod.data());
                    case ADD_SMITHING       -> addSmithing(newRecipes, newByName, (NeoForgeRecipeManager.SmithingData) mod.data());
                    case ADD_ANVIL          -> schedulePendingAnvil((NeoForgeRecipeManager.AnvilData) mod.data());
                    case ADD_BREWING        -> schedulePendingBrewing((NeoForgeRecipeManager.BrewingData) mod.data());
                    case ADD_TRADE          -> schedulePendingTrade((NeoForgeRecipeManager.TradeData) mod.data());
                }
            }

            // Write back in original field format
            if (Multimap.class.isAssignableFrom(byTypeField.getType())) {
                ArrayListMultimap<RecipeType<?>, RecipeHolder<?>> newMulti = ArrayListMultimap.create();
                for (Map.Entry<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> entry : newRecipes.entrySet()) {
                    newMulti.putAll(entry.getKey(), entry.getValue().values());
                }
                byTypeField.set(recipeManager, newMulti);
            } else {
                byTypeField.set(recipeManager, newRecipes);
            }
            byNameField.set(recipeManager, newByName);

            com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                "Applied " + modifications.size() + " recipe modifications successfully to RecipeManager."
            );
        } catch (Exception e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                "Failed to reflectively intercept RecipeManager: " + e.getClass().getName() + ": " + e.getMessage()
            );
            // Print full stack trace for debugging
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pw.flush();
            String separator = System.lineSeparator();
            // escape regex special chars in separator for split()
            String escaped = separator.replace("\\", "\\\\").replace(".", "\\.").replace("|", "\\|");
            for (String line : sw.toString().split(escaped)) {
                if (!line.isBlank()) {
                    com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                        "  " + line
                    );
                }
            }
        }
    }

    private static Field findRecipesByTypeField(RecipeManager recipeManager) throws NoSuchFieldException {
        // Level 1: Type inspection of live instance — prefer Multimap, then Map-of-Maps
        for (Field f : RecipeManager.class.getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object val = f.get(recipeManager);
                if (val instanceof Multimap) {
                    return f;
                }
            } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
        }
        for (Field f : RecipeManager.class.getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object val = f.get(recipeManager);
                if (val instanceof Map<?, ?> map) {
                    for (Object v : map.values()) {
                        if (v instanceof Map<?, ?>) {
                            return f;
                        }
                    }
                }
            } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
        }
        // Level 2: Known field names
        for (String name : new String[]{"byType", "recipes", "f_44007_"}) {
            try {
                Field f = RecipeManager.class.getDeclaredField(name);
                f.setAccessible(true);
                if (Multimap.class.isAssignableFrom(f.getType())) return f;
            } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
        }
        for (String name : new String[]{"byType", "recipes", "f_44007_"}) {
            try {
                Field f = RecipeManager.class.getDeclaredField(name);
                f.setAccessible(true);
                if (Map.class.isAssignableFrom(f.getType())) return f;
            } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
        }
        // Level 3: Find a Map/Multimap field different from byName
        Field byNameField = null;
        try {
            byNameField = findByNameMapField(recipeManager);
        } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }

        for (Field f : RecipeManager.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType()) || Multimap.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                if (byNameField == null || !f.equals(byNameField)) {
                    return f;
                }
            }
        }
        throw new NoSuchFieldException("recipes by-type field in RecipeManager");
    }

    private static Field findByNameMapField(RecipeManager recipeManager) throws NoSuchFieldException {
        // Level 1: Type inspection of live instance — entries must have ResourceLocation keys and RecipeHolder values
        for (Field f : RecipeManager.class.getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object val = f.get(recipeManager);
                if (val instanceof Map<?, ?> map) {
                    int checked = 0;
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() instanceof ResourceLocation && entry.getValue() instanceof RecipeHolder<?>) {
                            return f;
                        }
                        if (++checked > 50) break;
                    }
                }
            } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
        }
        // Level 2: Known field names in NeoForge/Mojang mappings
        for (String name : new String[]{"byName", "bySingleName", "f_44008_"}) {
            try {
                Field f = RecipeManager.class.getDeclaredField(name);
                f.setAccessible(true);
                if (Map.class.isAssignableFrom(f.getType())) return f;
            } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
        }
        // Level 3: Last Map field (byName is typically the last Map field in RecipeManager)
        Field lastMap = null;
        for (Field f : RecipeManager.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                lastMap = f;
            }
        }
        if (lastMap != null) return lastMap;
        throw new NoSuchFieldException("byName Map field in RecipeManager");
    }

    private static HolderLookup.Provider findRegistries(RecipeManager recipeManager) {
        for (Field f : RecipeManager.class.getDeclaredFields()) {
            if (HolderLookup.Provider.class.isAssignableFrom(f.getType())) {
                f.setAccessible(true);
                try {
                    Object val = f.get(recipeManager);
                    if (val instanceof HolderLookup.Provider provider) {
                        return provider;
                    }
                } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
            }
        }
        return null;
    }

    private static ItemStack getResultItemSafely(Recipe<?> recipe, HolderLookup.Provider registries) {
        if (recipe == null) return ItemStack.EMPTY;
        try {
            if (registries != null) {
                return recipe.getResultItem(registries);
            }
        } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
        try {
            return recipe.getResultItem(null);
        } catch (Exception e) { LOGGER.debug("Ignored exception: {}", e.getMessage()); }
        return ItemStack.EMPTY;
    }

    private static void removeByOutput(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, String outputId, HolderLookup.Provider registries) {
        ResourceLocation target = ResourceLocation.parse(outputId);
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (RecipeHolder<?> holder : byName.values()) {
            ItemStack result = getResultItemSafely(holder.value(), registries);
            if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).equals(target)) {
                toRemove.add(holder.id());
            }
        }
        for (ResourceLocation id : toRemove) {
            removeById(recipes, byName, id.toString());
        }
    }

    private static void removeByInput(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, String inputId) {
        Ingredient target = parseIngredient(inputId);
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (RecipeHolder<?> holder : byName.values()) {
            for (Ingredient ing : holder.value().getIngredients()) {
                if (Arrays.stream(ing.getItems()).anyMatch(stack -> Arrays.stream(target.getItems()).anyMatch(t -> t.getItem() == stack.getItem()))) {
                    toRemove.add(holder.id());
                    break;
                }
            }
        }
        for (ResourceLocation id : toRemove) {
            removeById(recipes, byName, id.toString());
        }
    }

    private static void removeById(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, String idStr) {
        ResourceLocation id = ResourceLocation.parse(idStr);
        RecipeHolder<?> removed = byName.remove(id);
        if (removed != null) {
            Map<ResourceLocation, RecipeHolder<?>> typeMap = recipes.get(removed.value().getType());
            if (typeMap != null) {
                typeMap.remove(id);
            }
        }
    }

    // ─── removeByTag ────────────────────────────────────────────────────────────
    private static void removeByTag(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, String tagStr, HolderLookup.Provider registries) {
        String normalized = tagStr.startsWith("#") ? tagStr.substring(1) : tagStr;
        TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(normalized));
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (RecipeHolder<?> holder : byName.values()) {
            // Check output
            ItemStack result = getResultItemSafely(holder.value(), registries);
            if (!result.isEmpty() && result.is(tagKey)) {
                toRemove.add(holder.id());
                continue;
            }
            // Check inputs
            for (Ingredient ing : holder.value().getIngredients()) {
                if (Arrays.stream(ing.getItems()).anyMatch(stack -> stack.is(tagKey))) {
                    toRemove.add(holder.id());
                    break;
                }
            }
        }
        for (ResourceLocation id : toRemove) {
            removeById(recipes, byName, id.toString());
        }
    }

    // ─── Pending schedulers (Anvil / Brewing / Trade) ───────────────────────────
    private static void schedulePendingAnvil(NeoForgeRecipeManager.AnvilData data) {
        PENDING_ANVIL.add(data);
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.RECIPE_APPLY,
            "[Anvil] Scheduled anvil recipe: " + data.recipeId() + " -> " + data.output().itemId()
        );
    }

    private static void schedulePendingBrewing(NeoForgeRecipeManager.BrewingData data) {
        PENDING_BREWING.add(data);
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.RECIPE_APPLY,
            "[Brewing] Scheduled brewing recipe: " + data.recipeId() + " (" + data.inputPotion() + " -> " + data.outputPotion() + ")"
        );
    }

    private static void schedulePendingTrade(NeoForgeRecipeManager.TradeData data) {
        PENDING_TRADES.add(data);
        com.luatweaker.api.log.LuaTweakerLog.get().info(
            com.luatweaker.api.log.LogStage.RECIPE_APPLY,
            "[Trade] Scheduled villager trade for " + data.profession() + " lvl " + data.level()
        );
    }

    /**
     * Call this from a @SubscribeEvent VillagerTradesEvent handler.
     * Only processes trades that were scheduled during the current Lua reload cycle.
     */
    public static void applyPendingTrades(VillagerTradesEvent event) {
        for (NeoForgeRecipeManager.TradeData data : PENDING_TRADES) {
            ResourceLocation profLoc = ResourceLocation.parse(data.profession());
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(profLoc);
            if (profession == null) {
                LOGGER.warn("[LuaTweaker] Unknown villager profession: {}", data.profession());
                continue;
            }
            if (!event.getType().equals(profession)) continue;

            var levelMap = event.getTrades();
            if (!levelMap.containsKey(data.level())) {
                LOGGER.warn("[LuaTweaker] Trade level {} out of range for profession {}", data.level(), data.profession());
                continue;
            }

            Item sell = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.sell().itemId()));
            ItemStack sellStack = new ItemStack(sell, data.sell().count());
            Item buy1Item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.buy1().itemId()));
            ItemCost buy1Cost = new ItemCost(buy1Item, data.buy1().count());
            ItemCost buy2Cost = null;
            if (data.buy2() != null) {
                Item buy2Item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.buy2().itemId()));
                buy2Cost = new ItemCost(buy2Item, data.buy2().count());
            }
            final ItemCost finalBuy2 = buy2Cost;

            levelMap.get(data.level()).add((trader, random) -> new MerchantOffer(
                buy1Cost,
                Optional.ofNullable(finalBuy2),
                sellStack,
                data.maxUses(),
                data.xp(),
                0.05f
            ));
        }
    }

    /**
     * Call this from a @SubscribeEvent RegisterBrewingRecipesEvent handler (on the mod event bus).
     * Uses PotionBrewing.Builder.addMix() — the correct NeoForge 1.21.1 API.
     */
    public static void applyPendingBrewing(RegisterBrewingRecipesEvent event) {
        net.minecraft.world.item.alchemy.PotionBrewing.Builder builder = event.getBuilder();
        for (NeoForgeRecipeManager.BrewingData data : PENDING_BREWING) {
            try {
                ResourceLocation inputLoc = ResourceLocation.parse(data.inputPotion());
                ResourceLocation outputLoc = ResourceLocation.parse(data.outputPotion());

                var inputKey = net.minecraft.resources.ResourceKey.create(Registries.POTION, inputLoc);
                var outputKey = net.minecraft.resources.ResourceKey.create(Registries.POTION, outputLoc);

                var inputHolder = BuiltInRegistries.POTION.getHolder(inputKey).orElse(null);
                var outputHolder = BuiltInRegistries.POTION.getHolder(outputKey).orElse(null);

                if (inputHolder == null || outputHolder == null) {
                    LOGGER.warn("[LuaTweaker] Unknown potion in brewing recipe '{}': in={}, out={}",
                        data.recipeId(), data.inputPotion(), data.outputPotion());
                    continue;
                }

                // addMix takes (Holder<Potion>, Item, Holder<Potion>) — extract Item from descriptor
                String desc = data.ingredient().descriptor();
                String itemId = desc.startsWith("#") ? desc.substring(1) : desc;
                Item catalystItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));

                // addMix registers the recipe for all container potion types (potion, splash, lingering)
                builder.addMix(inputHolder, catalystItem, outputHolder);

                com.luatweaker.api.log.LuaTweakerLog.get().info(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "[Brewing] Registered: " + data.inputPotion() + " + " +
                        data.ingredient().descriptor() + " \u2192 " + data.outputPotion()
                );
            } catch (Exception e) {
                LOGGER.error("[LuaTweaker] Failed to register brewing recipe '{}': {}",
                    data.recipeId(), e.getMessage());
            }
        }
    }

    /**
     * Call this from a @SubscribeEvent AnvilUpdateEvent handler.
     * If the items in the anvil match a pending anvil recipe, set the output and cost.
     */
    public static void applyPendingAnvil(AnvilUpdateEvent event) {
        for (NeoForgeRecipeManager.AnvilData data : PENDING_ANVIL) {
            Ingredient left = parseIngredient(data.leftInput().descriptor());
            Ingredient right = parseIngredient(data.rightInput().descriptor());

            boolean leftMatch = left.test(event.getLeft());
            boolean rightMatch = right.test(event.getRight());

            if (leftMatch && rightMatch) {
                ItemStack outputStack = createItemStack(data.output());

                if (outputStack.getItem() == event.getLeft().getItem() && (data.output().customName() == null || data.output().customName().isEmpty())) {
                    outputStack.applyComponents(event.getLeft().getComponentsPatch());
                }

                event.setOutput(outputStack);
                event.setCost(data.expCost());
                event.setMaterialCost(1);

                com.luatweaker.api.log.LuaTweakerLog.get().info(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "[Anvil] Applied recipe '" + data.recipeId() + "' -> " +
                        data.output().count() + "x " + data.output().itemId()
                );
                return; // First matching recipe wins
            }
        }
    }

    // ─── Existing remove helpers ─────────────────────────────────────────────────

    private static void removeByMod(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, String modId) {
        List<ResourceLocation> toRemove = new ArrayList<>();
        for (RecipeHolder<?> holder : byName.values()) {
            if (holder.id().getNamespace().equalsIgnoreCase(modId)) {
                toRemove.add(holder.id());
            }
        }
        for (ResourceLocation id : toRemove) {
            removeById(recipes, byName, id.toString());
        }
    }

    private static net.minecraft.network.chat.Component parseColoredText(String text) {
        if (text == null || text.isEmpty()) return net.minecraft.network.chat.Component.empty();
        String formatted = text.replace("Â§", "§").replaceAll("&([0-9a-fA-FK-ORk-or])", "§$1");
        
        net.minecraft.network.chat.MutableComponent root = null;
        String[] parts = formatted.split("§");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            net.minecraft.ChatFormatting style = net.minecraft.ChatFormatting.getByCode(part.charAt(0));
            String content = (style != null && part.length() > 1) ? part.substring(1) : part;
            net.minecraft.network.chat.MutableComponent comp = net.minecraft.network.chat.Component.literal(content);
            if (style != null) {
                comp.withStyle(style);
            }
            if (root == null) {
                root = comp;
            } else {
                root.append(comp);
            }
        }
        return root != null ? root : net.minecraft.network.chat.Component.literal(formatted);
    }

    public static net.minecraft.core.RegistryAccess getRegistryAccess() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null && client.level != null) {
                return client.level.registryAccess();
            }
        }
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            return server.registryAccess();
        }
        return null;
    }

    private static ItemStack createItemStack(ItemCount data) {
        if (data == null || data.itemId() == null) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.itemId()));
        if (item == null || item == Items.AIR) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RECIPE_APPLY, "Invalid or unregistered item ID: '" + data.itemId() + "'");
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, data.count()));

        if (data.damage() > 0 && stack.isDamageableItem()) {
            stack.setDamageValue(data.damage());
        }

        if (data.customName() != null && !data.customName().isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, parseColoredText(data.customName()));
        }

        if (data.lore() != null && !data.lore().isEmpty()) {
            List<net.minecraft.network.chat.Component> loreComponents = data.lore().stream()
                .map(InterceptionHelper::parseColoredText)
                .map(c -> (net.minecraft.network.chat.Component) c)
                .toList();
            stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(loreComponents));
        }

        if (data.enchantments() != null && !data.enchantments().isEmpty()) {
            net.minecraft.core.RegistryAccess registryAccess = getRegistryAccess();
            if (registryAccess != null) {
                var enchRegistry = registryAccess.registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
                net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(stack, mutable -> {
                    for (var entry : data.enchantments().entrySet()) {
                        ResourceLocation enchLoc = ResourceLocation.parse(entry.getKey());
                        var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, enchLoc);
                        var holderOpt = enchRegistry.getHolder(key);
                        holderOpt.ifPresent(holder -> mutable.set(holder, entry.getValue()));
                    }
                });
            }
        }

        if (data.nbtJson() != null && !data.nbtJson().isEmpty()) {
            try {
                net.minecraft.nbt.CompoundTag compound = net.minecraft.nbt.TagParser.parseTag(data.nbtJson());
                net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.merge(compound));
            } catch (Exception e) {
                com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RECIPE_APPLY, "Failed to parse NBT JSON for " + data.itemId() + ": " + data.nbtJson() + " (" + e.getMessage() + ")");
            }
        }

        return stack;
    }

    private static void addShapeless(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.ShapelessData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RECIPE_APPLY, "Skipping shapeless recipe '" + id + "' because output item is invalid or empty!");
            return;
        }

        NonNullList<Ingredient> ingredients = NonNullList.create();
        for (IngredientWrapper wrap : data.ingredients()) {
            ingredients.add(parseIngredient(wrap.descriptor()));
        }

        ShapelessRecipe recipe = new ShapelessRecipe("", CraftingBookCategory.MISC, outputStack, ingredients);
        RecipeHolder<ShapelessRecipe> holder = new RecipeHolder<>(id, recipe);

        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.CRAFTING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addShaped(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.ShapedData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RECIPE_APPLY, "Skipping shaped recipe '" + id + "' because output item is invalid or empty!");
            return;
        }

        int height = data.pattern().size();
        int width = data.pattern().get(0).length();

        Map<Character, Ingredient> keyMap = new HashMap<>();
        for (Map.Entry<String, IngredientWrapper> entry : data.keys().entrySet()) {
            if (!entry.getKey().isEmpty()) {
                keyMap.put(entry.getKey().charAt(0), parseIngredient(entry.getValue().descriptor()));
            }
        }

        NonNullList<Ingredient> ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int r = 0; r < height; r++) {
            String row = data.pattern().get(r);
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch != ' ') {
                    Ingredient ing = keyMap.get(ch);
                    if (ing != null) {
                        ingredients.set(r * width + c, ing);
                    }
                }
            }
        }

        ShapedRecipePattern pattern = ShapedRecipePattern.of(keyMap, data.pattern());
        ShapedRecipe recipe = new ShapedRecipe("", CraftingBookCategory.MISC, pattern, outputStack);
        RecipeHolder<ShapedRecipe> holder = new RecipeHolder<>(id, recipe);

        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.CRAFTING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addSmelting(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.CookingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) return;
        SmeltingRecipe recipe = new SmeltingRecipe("", CookingBookCategory.MISC, parseIngredient(data.input().descriptor()), outputStack, data.xp(), data.cookTime());
        RecipeHolder<SmeltingRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.SMELTING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addBlasting(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.CookingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) return;
        BlastingRecipe recipe = new BlastingRecipe("", CookingBookCategory.MISC, parseIngredient(data.input().descriptor()), outputStack, data.xp(), data.cookTime());
        RecipeHolder<BlastingRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.BLASTING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addSmoking(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.CookingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) return;
        SmokingRecipe recipe = new SmokingRecipe("", CookingBookCategory.MISC, parseIngredient(data.input().descriptor()), outputStack, data.xp(), data.cookTime());
        RecipeHolder<SmokingRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.SMOKING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addCampfire(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.CookingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) return;
        CampfireCookingRecipe recipe = new CampfireCookingRecipe("", CookingBookCategory.MISC, parseIngredient(data.input().descriptor()), outputStack, data.xp(), data.cookTime());
        RecipeHolder<CampfireCookingRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.CAMPFIRE_COOKING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addStonecutting(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.StonecuttingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) return;
        StonecutterRecipe recipe = new StonecutterRecipe("", parseIngredient(data.input().descriptor()), outputStack);
        RecipeHolder<StonecutterRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.STONECUTTING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addSmithing(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.SmithingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack resultStack = createItemStack(data.result());
        if (resultStack.isEmpty()) return;
        SmithingTransformRecipe recipe = new SmithingTransformRecipe(
            parseIngredient(data.template().descriptor()),
            parseIngredient(data.base().descriptor()),
            parseIngredient(data.addition().descriptor()),
            resultStack
        );
        RecipeHolder<SmithingTransformRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);

        recipes.computeIfAbsent(RecipeType.SMITHING, k -> new HashMap<>()).put(id, holder);
    }

    private static void replaceInput(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.ReplacementData data, HolderLookup.Provider registries) {
        Ingredient target = parseIngredient(data.target());
        Ingredient replacement = parseIngredient(data.replacement());

        for (Map.Entry<ResourceLocation, RecipeHolder<?>> entry : new ArrayList<>(byName.entrySet())) {
            RecipeHolder<?> holder = entry.getValue();
            Recipe<?> rawRecipe = holder.value();

            Recipe<?> newRecipe = createInputReplacedRecipe(rawRecipe, target, replacement, registries);
            if (newRecipe != null) {
                RecipeHolder<?> intercepted = new RecipeHolder<>(holder.id(), newRecipe);
                byName.put(entry.getKey(), intercepted);
                Map<ResourceLocation, RecipeHolder<?>> typeMap = recipes.get(rawRecipe.getType());
                if (typeMap != null) {
                    typeMap.put(entry.getKey(), intercepted);
                }
            }
        }
    }

    private static void replaceOutput(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.ReplacementData data, HolderLookup.Provider registries) {
        ResourceLocation targetLoc = ResourceLocation.parse(data.target());
        Item replacementItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.replacement()));

        for (Map.Entry<ResourceLocation, RecipeHolder<?>> entry : new ArrayList<>(byName.entrySet())) {
            RecipeHolder<?> holder = entry.getValue();
            Recipe<?> rawRecipe = holder.value();

            ItemStack result = getResultItemSafely(rawRecipe, registries);
            if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).equals(targetLoc)) {
                ItemStack newResult = new ItemStack(replacementItem, result.getCount());
                Recipe<?> newRecipe = createOutputReplacedRecipe(rawRecipe, newResult, registries);
                if (newRecipe != null) {
                    RecipeHolder<?> intercepted = new RecipeHolder<>(holder.id(), newRecipe);
                    byName.put(entry.getKey(), intercepted);
                    Map<ResourceLocation, RecipeHolder<?>> typeMap = recipes.get(rawRecipe.getType());
                    if (typeMap != null) {
                        typeMap.put(entry.getKey(), intercepted);
                    }
                }
            }
        }
    }

    private static Recipe<?> createInputReplacedRecipe(Recipe<?> rawRecipe, Ingredient target, Ingredient replacement, HolderLookup.Provider registries) {
        NonNullList<Ingredient> ingredients = rawRecipe.getIngredients();
        boolean hasMatch = false;
        NonNullList<Ingredient> newIngredients = NonNullList.create();
        for (Ingredient ing : ingredients) {
            if (Arrays.stream(ing.getItems()).anyMatch(stack -> Arrays.stream(target.getItems()).anyMatch(t -> t.getItem() == stack.getItem()))) {
                newIngredients.add(replacement);
                hasMatch = true;
            } else {
                newIngredients.add(ing);
            }
        }
        if (!hasMatch) return null;

        ItemStack resultStack = getResultItemSafely(rawRecipe, registries);

        if (rawRecipe instanceof ShapelessRecipe shapeless) {
            return new ShapelessRecipe(shapeless.getGroup(), shapeless.category(), resultStack, newIngredients);
        } else if (rawRecipe instanceof ShapedRecipe shaped) {
            ShapedRecipePattern newPattern = new ShapedRecipePattern(shaped.getWidth(), shaped.getHeight(), newIngredients, Optional.empty());
            return new ShapedRecipe(shaped.getGroup(), shaped.category(), newPattern, resultStack);
        } else if (rawRecipe instanceof SmeltingRecipe smelting) {
            return new SmeltingRecipe(smelting.getGroup(), smelting.category(), newIngredients.get(0), resultStack, smelting.getExperience(), smelting.getCookingTime());
        } else if (rawRecipe instanceof BlastingRecipe blasting) {
            return new BlastingRecipe(blasting.getGroup(), blasting.category(), newIngredients.get(0), resultStack, blasting.getExperience(), blasting.getCookingTime());
        } else if (rawRecipe instanceof SmokingRecipe smoking) {
            return new SmokingRecipe(smoking.getGroup(), smoking.category(), newIngredients.get(0), resultStack, smoking.getExperience(), smoking.getCookingTime());
        } else if (rawRecipe instanceof CampfireCookingRecipe campfire) {
            return new CampfireCookingRecipe(campfire.getGroup(), campfire.category(), newIngredients.get(0), resultStack, campfire.getExperience(), campfire.getCookingTime());
        } else if (rawRecipe instanceof StonecutterRecipe stonecut) {
            return new StonecutterRecipe(stonecut.getGroup(), newIngredients.get(0), resultStack);
        }
        return null;
    }

    private static Recipe<?> createOutputReplacedRecipe(Recipe<?> rawRecipe, ItemStack newOutput, HolderLookup.Provider registries) {
        if (rawRecipe instanceof ShapelessRecipe shapeless) {
            return new ShapelessRecipe(shapeless.getGroup(), shapeless.category(), newOutput, shapeless.getIngredients());
        } else if (rawRecipe instanceof ShapedRecipe shaped) {
            ShapedRecipePattern pattern = new ShapedRecipePattern(shaped.getWidth(), shaped.getHeight(), shaped.getIngredients(), Optional.empty());
            return new ShapedRecipe(shaped.getGroup(), shaped.category(), pattern, newOutput);
        } else if (rawRecipe instanceof SmeltingRecipe smelting) {
            return new SmeltingRecipe(smelting.getGroup(), smelting.category(), smelting.getIngredients().get(0), newOutput, smelting.getExperience(), smelting.getCookingTime());
        } else if (rawRecipe instanceof BlastingRecipe blasting) {
            return new BlastingRecipe(blasting.getGroup(), blasting.category(), blasting.getIngredients().get(0), newOutput, blasting.getExperience(), blasting.getCookingTime());
        } else if (rawRecipe instanceof SmokingRecipe smoking) {
            return new SmokingRecipe(smoking.getGroup(), smoking.category(), smoking.getIngredients().get(0), newOutput, smoking.getExperience(), smoking.getCookingTime());
        } else if (rawRecipe instanceof CampfireCookingRecipe campfire) {
            return new CampfireCookingRecipe(campfire.getGroup(), campfire.category(), campfire.getIngredients().get(0), newOutput, campfire.getExperience(), campfire.getCookingTime());
        } else if (rawRecipe instanceof StonecutterRecipe stonecut) {
            return new StonecutterRecipe(stonecut.getGroup(), stonecut.getIngredients().get(0), newOutput);
        }
        return null;
    }

    private static Ingredient parseIngredient(String desc) {
        String normalized = IngredientWrapper.normalizeDescriptor(desc);
        if (normalized.startsWith("#")) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(normalized.substring(1)));
            return Ingredient.of(tagKey);
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(normalized));
        return Ingredient.of(item);
    }
}
