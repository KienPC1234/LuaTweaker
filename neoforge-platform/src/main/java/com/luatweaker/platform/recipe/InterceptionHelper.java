package com.luatweaker.platform.recipe;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import net.minecraft.core.HolderLookup;
import org.jetbrains.annotations.NotNull;
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
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InterceptionHelper {
    private static final int MAX_FIELDS_TO_SCAN = 50;
    private static final char PATTERN_EMPTY_SLOT = ' ';
    /** Shared debug message for reflective lookups that do not apply. */
    private static final String IGNORED_EXCEPTION = "Ignored exception: {}";
    /** Merchant offer price multiplier used by every Lua trade (vanilla default). */
    private static final float TRADE_PRICE_MULTIPLIER = 0.05f;

    private InterceptionHelper() {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(InterceptionHelper.class);

    /** Pending villager trades to apply via VillagerTradesEvent */
    private static final List<NeoForgeRecipeManager.TradeData> PENDING_TRADES = new CopyOnWriteArrayList<>();

    /** Pending brewing recipes to apply via BrewingRecipeEvent / RecipeAddedEvent */
    private static final List<NeoForgeRecipeManager.BrewingData> PENDING_BREWING = new CopyOnWriteArrayList<>();

    /** Pending anvil operations (stored for inspection; anvil has no vanilla recipe type) */
    private static final List<NeoForgeRecipeManager.AnvilData> PENDING_ANVIL = new CopyOnWriteArrayList<>();

    /**
     * Vanilla recipe state before any Lua modification, keyed by RecipeManager. A reload must
     * not inherit the previous reload's removals: every apply starts from this snapshot.
     */
    private static final Map<RecipeManager, RecipeSnapshot> RECIPE_SNAPSHOTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** The field instances we wrote last, so our own writes are not mistaken for a datapack reload. */
    private static final Map<RecipeManager, Object[]> LAST_WRITTEN_MAPS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** Guards the read-modify-write of {@link #RECIPE_SNAPSHOTS} and {@link #LAST_WRITTEN_MAPS}. */
    private static final java.util.concurrent.locks.ReentrantLock SNAPSHOT_LOCK =
            new java.util.concurrent.locks.ReentrantLock();

    private record RecipeSnapshot(Object byTypeIdentity, Object byNameIdentity,
                                  Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> byType,
                                  Map<ResourceLocation, RecipeHolder<?>> byName) {
    }

    public static List<NeoForgeRecipeManager.TradeData> getPendingTrades() {
        return Collections.unmodifiableList(PENDING_TRADES);
    }

    public static List<NeoForgeRecipeManager.BrewingData> getPendingBrewing() {
        return Collections.unmodifiableList(PENDING_BREWING);
    }

    public static List<NeoForgeRecipeManager.AnvilData> getPendingAnvil() {
        return Collections.unmodifiableList(PENDING_ANVIL);
    }

    public static void clearPending() {
        PENDING_TRADES.clear();
        PENDING_BREWING.clear();
        PENDING_ANVIL.clear();
    }

    public static void populatePendingEvents(List<NeoForgeRecipeManager.RecipeModification> modifications) {
        clearPending();
        for (NeoForgeRecipeManager.RecipeModification mod : modifications) {
            switch (mod.type()) {
                case ADD_ANVIL -> schedulePendingAnvil((NeoForgeRecipeManager.AnvilData) mod.data());
                case ADD_BREWING -> schedulePendingBrewing((NeoForgeRecipeManager.BrewingData) mod.data());
                case ADD_TRADE -> schedulePendingTrade((NeoForgeRecipeManager.TradeData) mod.data());
                default -> {
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static void applyModifications(RecipeManager recipeManager,
                                          List<NeoForgeRecipeManager.RecipeModification> modifications) {
        try {
            applyReflectively(recipeManager, modifications);
        } catch (ReflectiveOperationException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "Failed to reflectively intercept RecipeManager: "
                            + e.getClass().getName() + ": " + e.getMessage());
            logStack(e);
        }
    }

    private static void applyReflectively(RecipeManager recipeManager,
                                          List<NeoForgeRecipeManager.RecipeModification> modifications)
            throws ReflectiveOperationException {
        Field byTypeField = findRecipesByTypeField(recipeManager);
        Field byNameField = findByNameMapField(recipeManager);
        if (byTypeField == null || byNameField == null) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "Cannot find RecipeManager fields: byType=" + byTypeField + " byName=" + byNameField);
            return;
        }
        if (byTypeField.equals(byNameField)) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "byType and byName fields are the same field, aborting.");
            return;
        }
        byTypeField.trySetAccessible();
        byNameField.trySetAccessible();
        Object byTypeRaw = byTypeField.get(recipeManager);
        Object byNameRaw = byNameField.get(recipeManager);
        var byName = extractByName(byNameRaw);
        var baseRecipes = extractByType(byTypeRaw);
        RecipeSnapshot snapshot = captureSnapshot(recipeManager, byTypeRaw, byNameRaw, baseRecipes, byName);
        var newRecipes = copyByType(snapshot.byType());
        HolderLookup.Provider registries = findRegistries(recipeManager);
        var newByName = new HashMap<>(snapshot.byName());
        for (NeoForgeRecipeManager.RecipeModification mod : modifications) {
            // One malformed recipe must not discard every other mod's changes.
            com.luatweaker.platform.util.CallbackFirewall.run(
                    () -> dispatch(mod, newRecipes, newByName, registries),
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    () -> "Skipped recipe modification " + mod.type());
        }
        writeBack(recipeManager, byTypeField, byNameField, newRecipes, newByName);
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                "Applied " + modifications.size() + " recipe modifications successfully to RecipeManager.");
    }

    private static void dispatch(NeoForgeRecipeManager.RecipeModification mod,
                                 Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes,
                                 Map<ResourceLocation, RecipeHolder<?>> byName,
                                 HolderLookup.Provider registries) {
        switch (mod.type()) {
            case REMOVE_BY_OUTPUT, REMOVE_BY_INPUT, REMOVE_BY_ID, REMOVE_BY_MOD, REMOVE_BY_TAG, REMOVE_ALL ->
                    dispatchRemoval(mod, recipes, byName, registries);
            case REPLACE_INPUT, REPLACE_OUTPUT, ADD_SHAPELESS, ADD_SHAPED ->
                    dispatchCrafting(mod, recipes, byName, registries);
            case ADD_SMELTING, ADD_BLASTING, ADD_SMOKING, ADD_CAMPFIRE, ADD_STONECUTTING, ADD_SMITHING ->
                    dispatchCooking(mod, recipes, byName);
            case ADD_ANVIL, ADD_BREWING, ADD_TRADE -> dispatchEvent(mod);
            default -> LOGGER.warn("[LuaTweaker] Unhandled recipe modification type {}", mod.type());
        }
    }

    private static void dispatchRemoval(NeoForgeRecipeManager.RecipeModification mod,
                                        Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes,
                                        Map<ResourceLocation, RecipeHolder<?>> byName,
                                        HolderLookup.Provider registries) {
        switch (mod.type()) {
            case REMOVE_BY_OUTPUT -> removeByOutput(recipes, byName, (String) mod.data(), registries);
            case REMOVE_BY_INPUT -> removeByInput(recipes, byName, (String) mod.data());
            case REMOVE_BY_ID -> removeById(recipes, byName, (String) mod.data());
            case REMOVE_BY_MOD -> removeByMod(recipes, byName, (String) mod.data());
            case REMOVE_BY_TAG -> removeByTag(recipes, byName, (String) mod.data(), registries);
            case REMOVE_ALL -> {
                recipes.clear();
                byName.clear();
            }
            default -> LOGGER.warn("[LuaTweaker] Unhandled removal type {}", mod.type());
        }
    }

    private static void dispatchCrafting(NeoForgeRecipeManager.RecipeModification mod,
                                         Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes,
                                         Map<ResourceLocation, RecipeHolder<?>> byName,
                                         HolderLookup.Provider registries) {
        switch (mod.type()) {
            case ADD_SHAPELESS -> addShapeless(recipes, byName, (NeoForgeRecipeManager.ShapelessData) mod.data());
            case ADD_SHAPED -> addShaped(recipes, byName, (NeoForgeRecipeManager.ShapedData) mod.data());
            case REPLACE_INPUT -> replaceInput(recipes, byName,
                    (NeoForgeRecipeManager.ReplacementData) mod.data(), registries);
            case REPLACE_OUTPUT -> replaceOutput(recipes, byName,
                    (NeoForgeRecipeManager.ReplacementData) mod.data(), registries);
            default -> LOGGER.warn("[LuaTweaker] Unhandled crafting type {}", mod.type());
        }
    }

    private static void dispatchCooking(NeoForgeRecipeManager.RecipeModification mod,
                                        Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes,
                                        Map<ResourceLocation, RecipeHolder<?>> byName) {
        switch (mod.type()) {
            case ADD_SMELTING -> addSmelting(recipes, byName, (NeoForgeRecipeManager.CookingData) mod.data());
            case ADD_BLASTING -> addBlasting(recipes, byName, (NeoForgeRecipeManager.CookingData) mod.data());
            case ADD_SMOKING -> addSmoking(recipes, byName, (NeoForgeRecipeManager.CookingData) mod.data());
            case ADD_CAMPFIRE -> addCampfire(recipes, byName, (NeoForgeRecipeManager.CookingData) mod.data());
            case ADD_STONECUTTING -> addStonecutting(recipes, byName,
                    (NeoForgeRecipeManager.StonecuttingData) mod.data());
            case ADD_SMITHING -> addSmithing(recipes, byName, (NeoForgeRecipeManager.SmithingData) mod.data());
            default -> LOGGER.warn("[LuaTweaker] Unhandled cooking type {}", mod.type());
        }
    }

    private static void dispatchEvent(NeoForgeRecipeManager.RecipeModification mod) {
        switch (mod.type()) {
            case ADD_ANVIL -> schedulePendingAnvil((NeoForgeRecipeManager.AnvilData) mod.data());
            case ADD_BREWING -> schedulePendingBrewing((NeoForgeRecipeManager.BrewingData) mod.data());
            case ADD_TRADE -> schedulePendingTrade((NeoForgeRecipeManager.TradeData) mod.data());
            default -> LOGGER.warn("[LuaTweaker] Unhandled event type {}", mod.type());
        }
    }

    private static Map<ResourceLocation, RecipeHolder<?>> extractByName(Object byNameRaw) {
        var byName = new HashMap<ResourceLocation, RecipeHolder<?>>();
        if (!(byNameRaw instanceof Map<?, ?> rawByName)) {
            return byName;
        }
        for (Map.Entry<?, ?> entry : rawByName.entrySet()) {
            if (entry.getValue() instanceof RecipeHolder<?> holder) {
                ResourceLocation id = entry.getKey() instanceof ResourceLocation rl ? rl : holder.id();
                byName.put(id, holder);
            }
        }
        return byName;
    }

    private static Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> extractByType(Object byTypeRaw) {
        var baseRecipes = new HashMap<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>>();
        if (byTypeRaw instanceof Multimap<?, ?> rawMulti) {
            collectMultiEntries(baseRecipes, rawMulti);
        } else if (byTypeRaw instanceof Map<?, ?> rawMap) {
            collectMapEntries(baseRecipes, rawMap);
        } else {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "Unexpected byType type: " + (byTypeRaw != null ? byTypeRaw.getClass().getName() : "null"));
        }
        return baseRecipes;
    }

    private static void collectMultiEntries(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> baseRecipes,
                                            Multimap<?, ?> rawMulti) {
        for (Map.Entry<?, ?> entry : rawMulti.entries()) {
            RecipeType<?> type = recipeTypeOf(entry.getKey());
            if (type != null && entry.getValue() instanceof RecipeHolder<?> holder) {
                putByType(baseRecipes, type, holder);
            }
        }
    }

    private static void collectMapEntries(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> baseRecipes,
                                          Map<?, ?> rawMap) {
        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            RecipeType<?> type = recipeTypeOf(entry.getKey());
            if (type != null) {
                collectTypeRecipes(baseRecipes, type, entry.getValue());
            }
        }
    }

    @Nullable
    private static RecipeType<?> recipeTypeOf(@Nullable Object key) {
        if (key instanceof RecipeType<?> type) {
            return type;
        }
        if (key instanceof ResourceLocation rl) {
            return BuiltInRegistries.RECIPE_TYPE.get(rl);
        }
        return null;
    }

    private static void collectTypeRecipes(
            Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> baseRecipes,
            RecipeType<?> type, Object rawValue) {
        if (rawValue instanceof Map<?, ?> valueMap) {
            for (Object value : valueMap.values()) {
                addHolder(baseRecipes, type, value);
            }
        } else if (rawValue instanceof Collection<?> valueCollection) {
            for (Object value : valueCollection) {
                addHolder(baseRecipes, type, value);
            }
        }
    }

    private static void addHolder(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> baseRecipes,
                                  RecipeType<?> type, Object value) {
        if (value instanceof RecipeHolder<?> holder) {
            putByType(baseRecipes, type, holder);
        }
    }

    private static void putByType(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> baseRecipes,
                                  RecipeType<?> type, RecipeHolder<?> holder) {
        baseRecipes.computeIfAbsent(type, ignored -> new HashMap<>()).put(holder.id(), holder);
    }

    private static RecipeSnapshot captureSnapshot(RecipeManager recipeManager, Object byTypeRaw, Object byNameRaw,
                                                   Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> baseRecipes,
                                                   Map<ResourceLocation, RecipeHolder<?>> byName) {
        SNAPSHOT_LOCK.lock();
        try {
            RecipeSnapshot snapshot = RECIPE_SNAPSHOTS.get(recipeManager);
            Object[] lastWritten = LAST_WRITTEN_MAPS.get(recipeManager);
            // Identity check on purpose: these are the raw map instances the RecipeManager
            // holds. A reload replaces them, and that replacement (not equal content) is the
            // signal that vanilla recipes must be re-snapshotted. Content equality would also
            // cost O(recipes) and would miss a reload that produced identical content.
            // PMD CompareObjectsWithEquals flags the four != below; that is the intentional
            // identity check described here, not an accidental reference comparison.
            boolean vanillaRestored = snapshot == null
                    || (snapshot.byTypeIdentity() != byTypeRaw
                        && (lastWritten == null || lastWritten[0] != byTypeRaw))
                    || (snapshot.byNameIdentity() != byNameRaw
                        && (lastWritten == null || lastWritten[1] != byNameRaw));
            if (vanillaRestored) {
                snapshot = new RecipeSnapshot(byTypeRaw, byNameRaw, copyByType(baseRecipes), new HashMap<>(byName));
                RECIPE_SNAPSHOTS.put(recipeManager, snapshot);
                com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                        "Captured vanilla recipe snapshot (" + byName.size() + " recipes) for RecipeManager@"
                                + System.identityHashCode(recipeManager));
            }
            return snapshot;
        } finally {
            SNAPSHOT_LOCK.unlock();
        }
    }

    private static void writeBack(RecipeManager recipeManager, Field byTypeField, Field byNameField,
                                  Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> newRecipes,
                                  Map<ResourceLocation, RecipeHolder<?>> newByName) throws IllegalAccessException {
        Object writtenByType;
        if (Multimap.class.isAssignableFrom(byTypeField.getType())) {
            ArrayListMultimap<RecipeType<?>, RecipeHolder<?>> newMulti = ArrayListMultimap.create();
            for (Map.Entry<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> entry : newRecipes.entrySet()) {
                newMulti.putAll(entry.getKey(), entry.getValue().values());
            }
            writtenByType = newMulti;
        } else {
            writtenByType = newRecipes;
        }
        byTypeField.set(recipeManager, writtenByType);
        byNameField.set(recipeManager, newByName);
        // Remember our own writes so the next apply keeps the vanilla snapshot instead of
        // capturing the already-modified state as "vanilla".
        SNAPSHOT_LOCK.lock();
        try {
            LAST_WRITTEN_MAPS.put(recipeManager, new Object[]{writtenByType, newByName});
        } finally {
            SNAPSHOT_LOCK.unlock();
        }
    }

    private static void logStack(ReflectiveOperationException failure) {
        StringWriter writer = new StringWriter();
        PrintWriter printer = new PrintWriter(writer);
        failure.printStackTrace(printer);
        printer.flush();
        String separator = System.lineSeparator();
        String escaped = separator.replace("\\", "\\\\").replace(".", "\\.").replace("|", "\\|");
        for (String line : writer.toString().split(escaped)) {
            if (!line.isBlank()) {
                com.luatweaker.api.log.LuaTweakerLog.get().info(
                        com.luatweaker.api.log.LogStage.RECIPE_APPLY, "  " + line);
            }
        }
    }

    private static Field findRecipesByTypeField(RecipeManager recipeManager) throws NoSuchFieldException {
        // Level 1: Type inspection of live instance - prefer Multimap, then Map-of-Maps.
        Field multimapField = scanForMultimapField(recipeManager);
        if (multimapField != null) {
            return multimapField;
        }
        Field mapOfMapsField = scanForMapOfMapsField(recipeManager);
        if (mapOfMapsField != null) {
            return mapOfMapsField;
        }
        // Level 2: Known field names.
        Field multimapByName = knownField("byType", "recipes", "f_44007_", true);
        if (multimapByName != null) {
            return multimapByName;
        }
        Field mapByName = knownField("byType", "recipes", "f_44007_", false);
        if (mapByName != null) {
            return mapByName;
        }
        // Level 3: Find a Map/Multimap field different from byName.
        return anyOtherMapField(recipeManager);
    }

    @Nullable
    private static Field scanForMultimapField(RecipeManager recipeManager) {
        for (Field field : RecipeManager.class.getDeclaredFields()) {
            field.trySetAccessible();
            try {
                if (field.get(recipeManager) instanceof Multimap) {
                    return field;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                LOGGER.debug(IGNORED_EXCEPTION, e.getMessage());
            }
        }
        return null;
    }

    @Nullable
    private static Field scanForMapOfMapsField(RecipeManager recipeManager) {
        for (Field field : RecipeManager.class.getDeclaredFields()) {
            field.trySetAccessible();
            try {
                if (field.get(recipeManager) instanceof Map<?, ?> map && containsNestedMap(map)) {
                    return field;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                LOGGER.debug(IGNORED_EXCEPTION, e.getMessage());
            }
        }
        return null;
    }

    private static boolean containsNestedMap(Map<?, ?> map) {
        for (Object value : map.values()) {
            if (value instanceof Map<?, ?>) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static Field knownField(String first, String second, String third, boolean requireMultimap) {
        for (String name : new String[]{first, second, third}) {
            try {
                Field field = RecipeManager.class.getDeclaredField(name);
                field.trySetAccessible();
                boolean matches = requireMultimap
                        ? Multimap.class.isAssignableFrom(field.getType())
                        : Map.class.isAssignableFrom(field.getType());
                if (matches) {
                    return field;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                LOGGER.debug(IGNORED_EXCEPTION, e.getMessage());
            }
        }
        return null;
    }

    @Nullable
    private static Field anyOtherMapField(RecipeManager recipeManager) throws NoSuchFieldException {
        Field byNameField = null;
        try {
            byNameField = findByNameMapField(recipeManager);
        } catch (ReflectiveOperationException e) {
            LOGGER.debug(IGNORED_EXCEPTION, e.getMessage());
        }
        for (Field field : RecipeManager.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType()) || Multimap.class.isAssignableFrom(field.getType())) {
                field.trySetAccessible();
                if (byNameField == null || !field.equals(byNameField)) {
                    return field;
                }
            }
        }
        throw new NoSuchFieldException("recipes by-type field in RecipeManager");
    }

    private static Field findByNameMapField(RecipeManager recipeManager) throws NoSuchFieldException {
        Field entriesField = scanForNameEntriesField(recipeManager);
        if (entriesField != null) {
            return entriesField;
        }
        Field knownField = knownMapField("byName", "bySingleName", "f_44008_");
        if (knownField != null) {
            return knownField;
        }
        Field lastMap = lastMapField();
        if (lastMap != null) {
            return lastMap;
        }
        throw new NoSuchFieldException("byName Map field in RecipeManager");
    }

    @Nullable
    private static Field scanForNameEntriesField(RecipeManager recipeManager) {
        for (Field field : RecipeManager.class.getDeclaredFields()) {
            field.trySetAccessible();
            try {
                if (field.get(recipeManager) instanceof Map<?, ?> map && hasRecipeEntries(map)) {
                    return field;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                LOGGER.debug(IGNORED_EXCEPTION, e.getMessage());
            }
        }
        return null;
    }

    private static boolean hasRecipeEntries(Map<?, ?> map) {
        int checked = 0;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof ResourceLocation && entry.getValue() instanceof RecipeHolder<?>) {
                return true;
            }
            checked++;
            if (checked > MAX_FIELDS_TO_SCAN) {
                return false;
            }
        }
        return false;
    }

    @Nullable
    private static Field knownMapField(String first, String second, String third) {
        for (String name : new String[]{first, second, third}) {
            try {
                Field field = RecipeManager.class.getDeclaredField(name);
                field.trySetAccessible();
                if (Map.class.isAssignableFrom(field.getType())) {
                    return field;
                }
            } catch (ReflectiveOperationException | IllegalArgumentException e) {
                LOGGER.debug(IGNORED_EXCEPTION, e.getMessage());
            }
        }
        return null;
    }

    @Nullable
    private static Field lastMapField() {
        Field lastMap = null;
        for (Field field : RecipeManager.class.getDeclaredFields()) {
            if (Map.class.isAssignableFrom(field.getType())) {
                field.trySetAccessible();
                lastMap = field;
            }
        }
        return lastMap;
    }
    private static HolderLookup.Provider findRegistries(RecipeManager recipeManager) {
        for (Field f : RecipeManager.class.getDeclaredFields()) {
            if (HolderLookup.Provider.class.isAssignableFrom(f.getType())) {
                f.trySetAccessible();
                try {
                    Object val = f.get(recipeManager);
                    if (val instanceof HolderLookup.Provider provider) {
                        return provider;
                    }
                } catch (ReflectiveOperationException | IllegalArgumentException e) {
                    LOGGER.debug(IGNORED_EXCEPTION, e.getMessage());
                }
            }
        }
        return null;
    }

    private static ItemStack getResultItemSafely(Recipe<?> recipe, HolderLookup.Provider registries) {
        if (recipe == null) {
            return ItemStack.EMPTY;
        }
        if (registries != null) {
            ItemStack resolved = com.luatweaker.platform.util.CallbackFirewall.supply(
                    () -> recipe.getResultItem(registries), ItemStack.EMPTY,
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY, () -> "Recipe result lookup failed");
            if (!resolved.isEmpty()) {
                return resolved;
            }
        }
        return com.luatweaker.platform.util.CallbackFirewall.supply(
                () -> recipe.getResultItem(null), ItemStack.EMPTY,
                com.luatweaker.api.log.LogStage.RECIPE_APPLY, () -> "Recipe result lookup failed");
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
                if (Arrays.stream(ing.getItems()).anyMatch(stack -> Arrays.stream(target.getItems()).anyMatch(t -> Objects.equals(t.getItem(), stack.getItem())))) {
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
            if (!event.getType().equals(profession)) { continue; }

            var levelMap = event.getTrades();
            if (!levelMap.containsKey(data.level())) {
                LOGGER.warn("[LuaTweaker] Trade level {} out of range for profession {}", data.level(), data.profession());
                continue;
            }

            MerchantOffer offer = offerFor(data);
            levelMap.get(data.level()).add((trader, random) -> offer);
        }
    }

    @NotNull
    private static MerchantOffer offerFor(NeoForgeRecipeManager.TradeData data) {
        ItemStack sellStack = new ItemStack(itemOf(data.sell()), data.sell().count());
        ItemCost buy1Cost = new ItemCost(itemOf(data.buy1()), data.buy1().count());
        ItemCost buy2Cost = data.buy2() != null ? new ItemCost(itemOf(data.buy2()), data.buy2().count()) : null;
        return new MerchantOffer(buy1Cost, Optional.ofNullable(buy2Cost), sellStack,
                data.maxUses(), data.xp(), TRADE_PRICE_MULTIPLIER);
    }

    private static Item itemOf(ItemCount count) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(count.itemId()));
    }

    /**
     * Call this from a @SubscribeEvent RegisterBrewingRecipesEvent handler (on the mod event bus).
     * Uses PotionBrewing.Builder.addMix() — the correct NeoForge 1.21.1 API.
     */
    public static void applyPendingBrewing(RegisterBrewingRecipesEvent event) {
        net.minecraft.world.item.alchemy.PotionBrewing.Builder builder = event.getBuilder();
        for (NeoForgeRecipeManager.BrewingData data : PENDING_BREWING) {
            Boolean registered = com.luatweaker.platform.util.CallbackFirewall.supply(
                    () -> registerBrewing(builder, data), Boolean.FALSE,
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    () -> "Failed to register brewing recipe '" + data.recipeId() + "'");
            if (!Boolean.TRUE.equals(registered)) {
                LOGGER.warn("[LuaTweaker] Brewing recipe '{}' was not registered", data.recipeId());
            }
        }
    }

    private static boolean registerBrewing(net.minecraft.world.item.alchemy.PotionBrewing.Builder builder,
                                           NeoForgeRecipeManager.BrewingData data) {
        ResourceLocation inputLoc = ResourceLocation.parse(data.inputPotion());
        ResourceLocation outputLoc = ResourceLocation.parse(data.outputPotion());
        var inputKey = net.minecraft.resources.ResourceKey.create(Registries.POTION, inputLoc);
        var outputKey = net.minecraft.resources.ResourceKey.create(Registries.POTION, outputLoc);
        var inputHolder = BuiltInRegistries.POTION.getHolder(inputKey).orElse(null);
        var outputHolder = BuiltInRegistries.POTION.getHolder(outputKey).orElse(null);
        if (inputHolder == null || outputHolder == null) {
            LOGGER.warn("[LuaTweaker] Unknown potion in brewing recipe '{}': in={}, out={}",
                    data.recipeId(), data.inputPotion(), data.outputPotion());
            return false;
        }
        // addMix takes (Holder<Potion>, Item, Holder<Potion>) - extract Item from descriptor
        String descriptor = data.ingredient().descriptor();
        String itemId = descriptor.startsWith("#") ? descriptor.substring(1) : descriptor;
        Item catalystItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        builder.addMix(inputHolder, catalystItem, outputHolder);
        com.luatweaker.api.log.LuaTweakerLog.get().info(
                com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                "[Brewing] Registered: " + data.inputPotion() + " + "
                        + data.ingredient().descriptor() + " -> " + data.outputPotion());
        return true;
    }
    /**
     * Call this from a @SubscribeEvent AnvilUpdateEvent handler.
     * If the items in the anvil match a pending anvil recipe, set the output and cost.
     */
    public static void applyPendingAnvil(AnvilUpdateEvent event) {
        for (NeoForgeRecipeManager.AnvilData data : PENDING_ANVIL) {
            Ingredient left = parseIngredient(data.leftInput());
            Ingredient right = parseIngredient(data.rightInput());

            boolean leftMatch = left.test(event.getLeft());
            boolean rightMatch = right.test(event.getRight());

            if (leftMatch && rightMatch) {
                ItemStack outputStack = createItemStack(data.output());

                if (Objects.equals(outputStack.getItem(), event.getLeft().getItem()) && (data.output().customName() == null || data.output().customName().isEmpty())) {
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
        if (text == null || text.isEmpty()) { return net.minecraft.network.chat.Component.empty(); }
        String formatted = text.replace("Â§", "§").replaceAll("&([0-9a-fA-FK-ORk-or])", "§$1");
        
        net.minecraft.network.chat.MutableComponent root = null;
        for (String part : formatted.split("§")) {
            if (!part.isEmpty()) {
                net.minecraft.network.chat.MutableComponent component = styledPart(part);
                if (root == null) {
                    root = component;
                } else {
                    root.append(component);
                }
            }
        }
        return root != null ? root : net.minecraft.network.chat.Component.literal(formatted);
    }

    @Nullable
    private static net.minecraft.network.chat.MutableComponent styledPart(String part) {
        net.minecraft.ChatFormatting style = net.minecraft.ChatFormatting.getByCode(part.charAt(0));
        String content = (style != null && part.length() > 1) ? part.substring(1) : part;
        net.minecraft.network.chat.MutableComponent component = net.minecraft.network.chat.Component.literal(content);
        if (style != null) {
            component.withStyle(style);
        }
        return component;
    }

    public static net.minecraft.core.RegistryAccess getRegistryAccess() {
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
            if (client != null && client.level != null) {
                return client.level.registryAccess();
            }
        }
        return serverRegistryAccess(net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer());
    }

    @Nullable
    private static net.minecraft.core.RegistryAccess serverRegistryAccess(
            @Nullable net.minecraft.server.MinecraftServer server) {
        return server != null ? server.registryAccess() : null;
    }

    private static ItemStack createItemStack(ItemCount data) {
        if (data == null || data.itemId() == null) {
            return ItemStack.EMPTY;
        }
        Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.itemId()));
        if (item == Items.AIR) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "Invalid or unregistered item ID: '" + data.itemId() + "'");
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, Math.max(1, data.count()));
        applyDamage(stack, data);
        applyName(stack, data);
        applyLore(stack, data);
        applyEnchantments(stack, data);
        applyNbt(stack, data);
        return stack;
    }

    private static void applyDamage(ItemStack stack, ItemCount data) {
        if (data.damage() > 0 && stack.isDamageableItem()) {
            stack.setDamageValue(data.damage());
        }
    }

    private static void applyName(ItemStack stack, ItemCount data) {
        if (data.customName() != null && !data.customName().isEmpty()) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, parseColoredText(data.customName()));
        }
    }

    private static void applyLore(ItemStack stack, ItemCount data) {
        if (data.lore() == null || data.lore().isEmpty()) {
            return;
        }
        List<net.minecraft.network.chat.Component> loreComponents = data.lore().stream()
                .map(InterceptionHelper::parseColoredText)
                .toList();
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new net.minecraft.world.item.component.ItemLore(loreComponents));
    }

    private static void applyEnchantments(ItemStack stack, ItemCount data) {
        if (data.enchantments() == null || data.enchantments().isEmpty()) {
            return;
        }
        net.minecraft.core.RegistryAccess registryAccess = getRegistryAccess();
        if (registryAccess == null) {
            return;
        }
        var enchantments = registryAccess.registryOrThrow(Registries.ENCHANTMENT);
        net.minecraft.world.item.enchantment.EnchantmentHelper.updateEnchantments(stack, mutable -> {
            for (var entry : data.enchantments().entrySet()) {
                ResourceLocation location = ResourceLocation.parse(entry.getKey());
                var key = net.minecraft.resources.ResourceKey.create(Registries.ENCHANTMENT, location);
                enchantments.getHolder(key).ifPresent(holder -> mutable.set(holder, entry.getValue()));
            }
        });
    }

    private static void applyNbt(ItemStack stack, ItemCount data) {
        if (data.nbtJson() == null || data.nbtJson().isEmpty()) {
            return;
        }
        try {
            net.minecraft.nbt.CompoundTag compound = net.minecraft.nbt.TagParser.parseTag(data.nbtJson());
            net.minecraft.world.item.component.CustomData.update(
                    net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, tag -> tag.merge(compound));
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(
                    com.luatweaker.api.log.LogStage.RECIPE_APPLY,
                    "Failed to parse NBT JSON for " + data.itemId() + ": " + data.nbtJson()
                            + " (" + e.getMessage() + ")");
        }
    }
    private static void addShapeless(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.ShapelessData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) {
            com.luatweaker.api.log.LuaTweakerLog.get().error(com.luatweaker.api.log.LogStage.RECIPE_APPLY, "Skipping shapeless recipe '" + id + "' because output item is invalid or empty!");
            return;
        }

        var ingredients = NonNullList.<Ingredient>create();
        for (IngredientWrapper wrap : data.ingredients()) {
            ingredients.add(parseIngredient(wrap));
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

        var keyMap = new HashMap<Character, Ingredient>();
        for (Map.Entry<String, IngredientWrapper> entry : data.keys().entrySet()) {
            if (!entry.getKey().isEmpty()) {
                keyMap.put(entry.getKey().charAt(0), parseIngredient(entry.getValue()));
            }
        }

        var ingredients = NonNullList.withSize(width * height, Ingredient.EMPTY);
        for (int r = 0; r < height; r++) {
            String row = data.pattern().get(r);
            for (int c = 0; c < row.length(); c++) {
                char ch = row.charAt(c);
                if (ch != PATTERN_EMPTY_SLOT) {
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
        if (outputStack.isEmpty()) { return; }
        SmeltingRecipe recipe = new SmeltingRecipe("", CookingBookCategory.MISC, parseIngredient(data.input()), outputStack, data.xp(), data.cookTime());
        RecipeHolder<SmeltingRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.SMELTING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addBlasting(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.CookingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) { return; }
        BlastingRecipe recipe = new BlastingRecipe("", CookingBookCategory.MISC, parseIngredient(data.input()), outputStack, data.xp(), data.cookTime());
        RecipeHolder<BlastingRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.BLASTING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addSmoking(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.CookingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) { return; }
        SmokingRecipe recipe = new SmokingRecipe("", CookingBookCategory.MISC, parseIngredient(data.input()), outputStack, data.xp(), data.cookTime());
        RecipeHolder<SmokingRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.SMOKING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addCampfire(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.CookingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) { return; }
        CampfireCookingRecipe recipe = new CampfireCookingRecipe("", CookingBookCategory.MISC, parseIngredient(data.input()), outputStack, data.xp(), data.cookTime());
        RecipeHolder<CampfireCookingRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.CAMPFIRE_COOKING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addStonecutting(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.StonecuttingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack outputStack = createItemStack(data.output());
        if (outputStack.isEmpty()) { return; }
        StonecutterRecipe recipe = new StonecutterRecipe("", parseIngredient(data.input()), outputStack);
        RecipeHolder<StonecutterRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);
        recipes.computeIfAbsent(RecipeType.STONECUTTING, k -> new HashMap<>()).put(id, holder);
    }

    private static void addSmithing(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes, Map<ResourceLocation, RecipeHolder<?>> byName, NeoForgeRecipeManager.SmithingData data) {
        ResourceLocation id = ResourceLocation.parse(data.recipeId());
        ItemStack resultStack = createItemStack(data.result());
        if (resultStack.isEmpty()) { return; }
        SmithingTransformRecipe recipe = new SmithingTransformRecipe(
            parseIngredient(data.template()),
            parseIngredient(data.base()),
            parseIngredient(data.addition()),
            resultStack
        );
        RecipeHolder<SmithingTransformRecipe> holder = new RecipeHolder<>(id, recipe);
        byName.put(id, holder);

        recipes.computeIfAbsent(RecipeType.SMITHING, k -> new HashMap<>()).put(id, holder);
    }

    private static void replaceInput(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes,
                                     Map<ResourceLocation, RecipeHolder<?>> byName,
                                     NeoForgeRecipeManager.ReplacementData data,
                                     HolderLookup.Provider registries) {
        Ingredient target = parseIngredient(data.target());
        Ingredient replacement = parseIngredient(data.replacement());
        for (Map.Entry<ResourceLocation, RecipeHolder<?>> entry : new ArrayList<>(byName.entrySet())) {
            RecipeHolder<?> holder = entry.getValue();
            Recipe<?> newRecipe = createInputReplacedRecipe(holder.value(), target, replacement, registries);
            if (newRecipe != null) {
                putIntercepted(recipes, byName, entry.getKey(), holder, newRecipe);
            }
        }
    }

    private static void replaceOutput(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes,
                                      Map<ResourceLocation, RecipeHolder<?>> byName,
                                      NeoForgeRecipeManager.ReplacementData data,
                                      HolderLookup.Provider registries) {
        ResourceLocation targetId = ResourceLocation.parse(data.target());
        Item replacementItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(data.replacement()));
        for (Map.Entry<ResourceLocation, RecipeHolder<?>> entry : new ArrayList<>(byName.entrySet())) {
            RecipeHolder<?> holder = entry.getValue();
            Recipe<?> rawRecipe = holder.value();
            ItemStack result = getResultItemSafely(rawRecipe, registries);
            if (!result.isEmpty() && BuiltInRegistries.ITEM.getKey(result.getItem()).equals(targetId)) {
                Recipe<?> newRecipe = createOutputReplacedRecipe(rawRecipe, replacementItem, result.getCount());
                if (newRecipe != null) {
                    putIntercepted(recipes, byName, entry.getKey(), holder, newRecipe);
                }
            }
        }
    }

    private static void putIntercepted(Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> recipes,
                                       Map<ResourceLocation, RecipeHolder<?>> byName,
                                       ResourceLocation key, RecipeHolder<?> original, Recipe<?> newRecipe) {
        RecipeHolder<?> intercepted = new RecipeHolder<>(key, newRecipe);
        byName.put(key, intercepted);
        Map<ResourceLocation, RecipeHolder<?>> typeMap = recipes.get(original.value().getType());
        if (typeMap != null) {
            typeMap.put(key, intercepted);
        }
    }

    @Nullable
    private static Recipe<?> createInputReplacedRecipe(Recipe<?> rawRecipe, Ingredient target,
                                                       Ingredient replacement, HolderLookup.Provider registries) {
        boolean hasMatch = false;
        var newIngredients = NonNullList.<Ingredient>create();
        for (Ingredient ingredient : rawRecipe.getIngredients()) {
            if (matchesIngredient(ingredient, target)) {
                newIngredients.add(replacement);
                hasMatch = true;
            } else {
                newIngredients.add(ingredient);
            }
        }
        if (!hasMatch) {
            return null;
        }
        return rebuild(rawRecipe, newIngredients, getResultItemSafely(rawRecipe, registries));
    }

    private static boolean matchesIngredient(Ingredient ingredient, Ingredient target) {
        return Arrays.stream(ingredient.getItems())
                .anyMatch(stack -> Arrays.stream(target.getItems())
                        .anyMatch(t -> Objects.equals(t.getItem(), stack.getItem())));
    }

    @Nullable
    private static Recipe<?> createOutputReplacedRecipe(Recipe<?> rawRecipe, Item replacementItem, int count) {
        return rebuild(rawRecipe, rawRecipe.getIngredients(), new ItemStack(replacementItem, count));
    }

    /** Rebuilds a recipe of the same type with new ingredients/result; null for unsupported types. */
    @Nullable
    private static Recipe<?> rebuild(Recipe<?> rawRecipe, List<Ingredient> ingredients, ItemStack resultStack) {
        if (rawRecipe instanceof ShapelessRecipe shapeless) {
            return new ShapelessRecipe(shapeless.getGroup(), shapeless.category(), resultStack,
                    NonNullList.copyOf(ingredients));
        }
        if (rawRecipe instanceof ShapedRecipe shaped) {
            ShapedRecipePattern pattern = new ShapedRecipePattern(shaped.getWidth(), shaped.getHeight(),
                    NonNullList.copyOf(ingredients), Optional.empty());
            return new ShapedRecipe(shaped.getGroup(), shaped.category(), pattern, resultStack);
        }
        if (rawRecipe instanceof SmeltingRecipe smelting) {
            return new SmeltingRecipe(smelting.getGroup(), smelting.category(), ingredients.get(0), resultStack,
                    smelting.getExperience(), smelting.getCookingTime());
        }
        if (rawRecipe instanceof BlastingRecipe blasting) {
            return new BlastingRecipe(blasting.getGroup(), blasting.category(), ingredients.get(0), resultStack,
                    blasting.getExperience(), blasting.getCookingTime());
        }
        if (rawRecipe instanceof SmokingRecipe smoking) {
            return new SmokingRecipe(smoking.getGroup(), smoking.category(), ingredients.get(0), resultStack,
                    smoking.getExperience(), smoking.getCookingTime());
        }
        if (rawRecipe instanceof CampfireCookingRecipe campfire) {
            return new CampfireCookingRecipe(campfire.getGroup(), campfire.category(), ingredients.get(0),
                    resultStack, campfire.getExperience(), campfire.getCookingTime());
        }
        if (rawRecipe instanceof StonecutterRecipe stonecut) {
            return new StonecutterRecipe(stonecut.getGroup(), ingredients.get(0), resultStack);
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

    /**
     * Builds the Minecraft ingredient for a Lua {@code ingredient(...):or(...)} chain. A single
     * descriptor keeps tag semantics; alternatives are expanded into an explicit item list.
     */
    private static Ingredient parseIngredient(IngredientWrapper wrapper) {
        if (wrapper.alternatives().isEmpty()) {
            return parseIngredient(wrapper.descriptor());
        }
        List<ItemStack> stacks = new ArrayList<>();
        collectIngredientStacks(wrapper, stacks);
        return stacks.isEmpty() ? Ingredient.EMPTY : Ingredient.of(stacks.toArray(new ItemStack[0]));
    }

    private static void collectIngredientStacks(IngredientWrapper wrapper, List<ItemStack> out) {
        String normalized = IngredientWrapper.normalizeDescriptor(wrapper.descriptor());
        if (normalized.startsWith("#")) {
            TagKey<Item> tagKey = TagKey.create(Registries.ITEM, ResourceLocation.parse(normalized.substring(1)));
            for (net.minecraft.core.Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tagKey)) {
                out.add(new ItemStack(holder.value()));
            }
        } else {
            out.add(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(normalized))));
        }
        for (IngredientWrapper alternative : wrapper.alternatives()) {
            collectIngredientStacks(alternative, out);
        }
    }

    private static Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> copyByType(
            Map<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> source) {
        var copy = new HashMap<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>>();
        for (Map.Entry<RecipeType<?>, Map<ResourceLocation, RecipeHolder<?>>> entry : source.entrySet()) {
            copy.put(entry.getKey(), copyType(entry.getValue()));
        }
        return copy;
    }

    private static Map<ResourceLocation, RecipeHolder<?>> copyType(
            Map<ResourceLocation, RecipeHolder<?>> source) {
        return new HashMap<>(source);
    }
}












