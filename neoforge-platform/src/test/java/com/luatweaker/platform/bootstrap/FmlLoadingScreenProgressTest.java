package com.luatweaker.platform.bootstrap;

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

    @Test
    void reportsEveryStepOnTheFmlLoadingScreen() {
        FmlLoadingScreenProgress progress = new FmlLoadingScreenProgress();

        progress.onModStart(1, 2, "alpha_mod");
        progress.onLoadComplete(2, 2);

        List<String> messages = startupMessages();
        assertTrue(messages.stream().anyMatch(m -> m.contains("alpha_mod (1/2)")), messages.toString());
        assertTrue(messages.stream().anyMatch(m -> m.contains("LuaMods ready: 2/2")), messages.toString());
    }

    @Test
    void nonAsciiModIdsAreSanitizedByFmlInsteadOfThrowing() {
        FmlLoadingScreenProgress progress = new FmlLoadingScreenProgress();

        progress.onModStart(1, 1, "mô_đun_việt");

        List<String> messages = startupMessages();
        assertTrue(messages.stream().anyMatch(m -> m.contains("m_un_vit (1/1)")), messages.toString());
    }
}
