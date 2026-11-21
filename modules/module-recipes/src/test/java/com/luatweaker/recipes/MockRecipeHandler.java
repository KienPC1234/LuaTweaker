package com.luatweaker.recipes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.luatweaker.api.recipe.IRecipeManagerService;
import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;

/**
 * Recording fake for the recipe manager service. It implements the whole
 * service interface with a small reflective dispatcher instead of twenty
 * hand-written methods, while recording every call the tests assert on.
 * Furnace-style variants (smelting, blasting, ...) are intentionally
 * unrecorded: no test observes them.
 */
final class MockRecipeHandler implements InvocationHandler {

    static final String ARROW = " -> ";

    private static final Set<String> CUSTOM_METHODS = Set.of("registerCustom");
    private static final Set<String> SHAPELESS_METHODS = Set.of("addShapeless");
    private static final Set<String> SHAPED_METHODS = Set.of("addShaped");

    final List<String> removedOutputs = new ArrayList<>();
    final List<String> removedInputs = new ArrayList<>();
    final List<String> removedIds = new ArrayList<>();
    final List<String> removedTags = new ArrayList<>();
    final List<String> addedShapeless = new ArrayList<>();
    final List<String> addedShaped = new ArrayList<>();
    final List<IngredientWrapper> capturedIngredients = new ArrayList<>();
    final List<String> replacedInputs = new ArrayList<>();
    final List<String> replacedOutputs = new ArrayList<>();
    final List<String> customRecipes = new ArrayList<>();
    final Map<String, List<String>> removalSinks = new ConcurrentHashMap<>();
    final Map<String, List<String>> replacementSinks = new ConcurrentHashMap<>();

    MockRecipeHandler() {
        removalSinks.put("removeByOutput", removedOutputs);
        removalSinks.put("removeByInput", removedInputs);
        removalSinks.put("removeById", removedIds);
        removalSinks.put("removeByTag", removedTags);
        replacementSinks.put("replaceInput", replacedInputs);
        replacementSinks.put("replaceOutput", replacedOutputs);
    }

    static IRecipeManagerService newMockService(MockRecipeHandler handler) {
        return (IRecipeManagerService) Proxy.newProxyInstance(
                ClassLoader.getSystemClassLoader(),
                new Class<?>[] {IRecipeManagerService.class},
                handler);
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        String name = method.getName();
        Object[] safeArgs = args == null ? new Object[0] : args;
        List<String> removalSink = removalSinks.get(name);
        if (removalSink != null) {
            removalSink.add((String) safeArgs[0]);
            return null;
        }
        List<String> replacementSink = replacementSinks.get(name);
        if (replacementSink != null) {
            replacementSink.add(safeArgs[0] + ARROW + safeArgs[1]);
            return null;
        }
        if (SHAPELESS_METHODS.contains(name)) {
            recordShapeless(safeArgs);
            return null;
        }
        if (SHAPED_METHODS.contains(name)) {
            recordShaped(safeArgs);
            return null;
        }
        if (CUSTOM_METHODS.contains(name)) {
            customRecipes.add(safeArgs[0] + "|" + safeArgs[1] + "|" + safeArgs[2]);
            return null;
        }
        return null;
    }

    private void recordShapeless(Object... args) {
        ItemCount output = (ItemCount) args[1];
        for (Object ingredient : (Iterable<?>) args[2]) {
            capturedIngredients.add((IngredientWrapper) ingredient);
        }
        addedShapeless.add(args[0] + ARROW + output.itemId() + "x" + output.count());
    }

    private void recordShaped(Object... args) {
        ItemCount output = (ItemCount) args[1];
        List<String> pattern = new ArrayList<>();
        for (Object row : (Iterable<?>) args[2]) {
            pattern.add((String) row);
        }
        addedShaped.add(args[0] + ARROW + output.itemId() + "x" + output.count()
                + " pattern:" + String.join(",", pattern));
    }
}
