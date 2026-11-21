package com.luatweaker.interception;

import com.luatweaker.api.wrapper.IngredientWrapper;
import com.luatweaker.api.wrapper.ItemCount;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Verifies that the three interception APIs convert their arguments exactly once and
 * delegate to the active recipe manager without touching the other recipe operations.
 */
class InterceptionDelegationTest {

    private static final String ANVIL_ID = "test_anvil";
    private static final String BREW_ID = "test_brew";
    private static final String IRON = "minecraft:iron_ingot";
    private static final String GOLD = "minecraft:gold_ingot";
    private static final String APPLE = "minecraft:golden_apple";
    private static final String WATER = "minecraft:water";
    private static final String GLOWSTONE = "minecraft:glowstone_dust";
    private static final String THICK = "minecraft:thick";
    private static final String FARMER = "farmer";
    private static final String EMERALD = "minecraft:emerald";
    private static final String BREAD = "minecraft:bread";

    private RecordingRecipeService delegate;
    private InterceptionServiceImpl service;

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @BeforeEach
    void setUp() {
        delegate = new RecordingRecipeService();
        service = new InterceptionServiceImpl(delegate.asService());
    }

    @Test
    void anvilRecipeIsConvertedAndForwardedOnce() {
        service.addAnvilRecipe(ANVIL_ID, IRON, GOLD, APPLE, 5);

        RecordingRecipeService.Invocation call = delegate.callsOf("addAnvil").get(0);
        Assertions.assertAll("the anvil recipe must reach the delegate exactly as converted",
            () -> Assertions.assertEquals(
                List.of("addAnvil", ANVIL_ID, APPLE, "1", IRON, GOLD, "5"),
                describeAnvil(call), "every converted anvil argument must reach the delegate"),
            () -> Assertions.assertEquals(1, service.getRegistrationCount(),
                "a successful anvil registration must be counted"),
            () -> Assertions.assertEquals(List.of("addAnvil"), delegate.methodsCalled(),
                "adding an anvil recipe must not call the brewing or trade delegate"));
    }

    @Test
    void brewingRecipeIsForwardedAsPlainStrings() {
        service.addBrewingRecipe(BREW_ID, WATER, GLOWSTONE, THICK);

        RecordingRecipeService.Invocation call = delegate.callsOf("addBrewing").get(0);
        Assertions.assertAll("the brewing recipe must reach the delegate exactly as converted",
            () -> Assertions.assertEquals(
                List.of("addBrewing", BREW_ID, THICK, WATER, GLOWSTONE),
                describeBrewing(call), "the brewing strings and catalyst must reach the delegate"),
            () -> Assertions.assertEquals(1, service.getRegistrationCount(),
                "a successful brewing registration must be counted"),
            () -> Assertions.assertEquals(List.of("addBrewing"), delegate.methodsCalled(),
                "adding a brewing recipe must not call the anvil or trade delegate"));
    }

    @Test
    void villagerTradeUsesASingleItemStackAndNoSecondBuySlot() {
        service.addVillagerTrade(FARMER, 2, EMERALD, BREAD, 12, 10);

        RecordingRecipeService.Invocation call = delegate.callsOf("addTrade").get(0);
        Assertions.assertAll("the trade must reach the delegate exactly as converted",
            () -> Assertions.assertEquals(
                List.of("addTrade", FARMER, "2", EMERALD, "1", "no-second-buy", BREAD, "1", "12", "10"),
                describeTrade(call), "every converted trade argument must reach the delegate"),
            () -> Assertions.assertEquals(1, service.getRegistrationCount(),
                "a successful trade registration must be counted"),
            () -> Assertions.assertEquals(List.of("addTrade"), delegate.methodsCalled(),
                "adding a trade must not call the anvil or brewing delegate"));
    }

    private static List<String> describeAnvil(RecordingRecipeService.Invocation call) {
        ItemCount output = (ItemCount) call.args()[1];
        return List.of(
                call.method(),
                (String) call.args()[0],
                output.itemId(),
                String.valueOf(output.count()),
                ((IngredientWrapper) call.args()[2]).descriptor(),
                ((IngredientWrapper) call.args()[3]).descriptor(),
                String.valueOf(call.args()[4]));
    }

    private static List<String> describeBrewing(RecordingRecipeService.Invocation call) {
        return List.of(
                call.method(),
                (String) call.args()[0],
                (String) call.args()[1],
                (String) call.args()[2],
                ((IngredientWrapper) call.args()[3]).descriptor());
    }

    private static List<String> describeTrade(RecordingRecipeService.Invocation call) {
        ItemCount buy1 = (ItemCount) call.args()[2];
        ItemCount buy2 = (ItemCount) call.args()[3];
        ItemCount sell = (ItemCount) call.args()[4];
        List<String> described = new ArrayList<>(List.of(
                call.method(),
                (String) call.args()[0],
                String.valueOf(call.args()[1]),
                buy1.itemId(),
                String.valueOf(buy1.count())));
        described.add(buy2 == null ? "no-second-buy" : buy2.itemId());
        described.add(sell.itemId());
        described.add(String.valueOf(sell.count()));
        described.add(String.valueOf(call.args()[5]));
        described.add(String.valueOf(call.args()[6]));
        return described;
    }
}
