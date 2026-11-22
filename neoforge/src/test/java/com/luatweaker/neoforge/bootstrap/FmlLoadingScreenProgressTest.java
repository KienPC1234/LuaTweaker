package com.luatweaker.neoforge.bootstrap;

import net.neoforged.fml.loading.progress.StartupNotificationManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loading-screen adapter must push readable lines through the FML startup message queue.
 * Rendering itself happens on the early-display thread and needs a game window, so this covers
 * the contract the queue receives.
 */
class FmlLoadingScreenProgressTest {

    private static List<String> startupMessages() {
        return StartupNotificationManager.getMessages().stream()
                .map(entry -> entry.message().getText())
                .toList();
    }

    private static List<String> messagesAfterFullLoad(String modId, int totalMods) {
        FmlLoadingScreenProgress progress = new FmlLoadingScreenProgress();

        progress.onModStart(1, totalMods, modId);
        progress.onLoadComplete(totalMods, totalMods);

        return startupMessages();
    }

    @Test
    void reportsModStartLineOnTheFmlLoadingScreen() {
        List<String> messages = messagesAfterFullLoad("alpha_mod", 2);

        assertTrue(messages.stream().anyMatch(message -> message.contains("alpha_mod (1/2)")),
                "the loading screen must show which mod started loading: " + messages);
    }

    @Test
    void reportsReadyLineOnTheFmlLoadingScreen() {
        List<String> messages = messagesAfterFullLoad("alpha_mod", 2);

        assertTrue(messages.stream().anyMatch(message -> message.contains("LuaMods ready: 2/2")),
                "the loading screen must show the final ready line: " + messages);
    }

    @Test
    void nonAsciiModIdsAreSanitizedByFmlInsteadOfThrowing() {
        FmlLoadingScreenProgress progress = new FmlLoadingScreenProgress();

        progress.onModStart(1, 1, "mô_đun_việt");

        List<String> messages = startupMessages();
        assertTrue(messages.stream().anyMatch(message -> message.contains("m_un_vit (1/1)")),
                "non-ASCII mod ids must be sanitized instead of breaking the queue: " + messages);
    }
}
