package com.luatweaker.command;

import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuaCommandWrapperSuggestionsTest {

    private static final String HELLO = "hello";
    private static final String SHOP_BUY = "shop/buy";
    private static final String PLAIN = "plain";
    private static final String TYPED_TAIL = "buy ru";

    private static final String STATIC_DEF =
        "{ Suggestions = { 'world', 'minecraft' }, Handler = function() end }";
    private static final String FUNCTION_DEF = """
        { Suggestions = function(sender, args)
            _G._suggestSender = sender.Name
            _G._suggestArgs = #args
            return { 'sword', 'pickaxe' }
          end,
          Handler = function() end
        }
        """;
    private static final String PLAIN_DEF = "{ Handler = function() end }";

    @BeforeEach
    void setUp() {
        CommandServiceImpl.clear();
    }

    @AfterAll
    static void tearDown() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void staticSuggestionsAreReturned() {
        SuggestRun run = CommandWrapperFixture.suggest(HELLO, STATIC_DEF, "hel");

        assertEquals(List.of("world", "minecraft"), run.suggestions(),
            "a static Suggestions table must be returned as the completion list");
    }

    @Test
    void functionSuggestionsAreReturned() {
        SuggestRun run = CommandWrapperFixture.suggest(SHOP_BUY, FUNCTION_DEF, TYPED_TAIL);

        assertEquals(List.of("sword", "pickaxe"), run.suggestions(),
            "a Suggestions function must be called and its table returned");
    }

    @Test
    void functionSuggestionReceivesSenderName() {
        SuggestRun run = CommandWrapperFixture.suggest(SHOP_BUY, FUNCTION_DEF, TYPED_TAIL);

        assertEquals("TestPlayer", CommandWrapperFixture.globalString(run.engine(), "_suggestSender"),
            "the suggestion function must receive the sender table");
    }

    @Test
    void functionSuggestionReceivesTypedTokens() {
        SuggestRun run = CommandWrapperFixture.suggest(SHOP_BUY, FUNCTION_DEF, TYPED_TAIL);

        assertEquals(2, CommandWrapperFixture.globalInt(run.engine(), "_suggestArgs"),
            "the suggestion function must receive the typed tokens as its args table");
    }

    @Test
    void commandWithoutSuggestionsReturnsEmptyList() {
        SuggestRun run = CommandWrapperFixture.suggest(PLAIN, PLAIN_DEF, "x");

        assertTrue(run.suggestions().isEmpty(),
            "a command with no Suggestions provider must complete with an empty list");
    }
}
