package com.luatweaker.content;

import java.io.IOException;

import com.luatweaker.api.content.IItemBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Item use animations and lifecycle callbacks are stored for the platform. */
class ItemAnimationTest extends ContentFixture {

    private static final String STAFF_CHUNK = "staff_chunk";
    private static final int USE_DURATION = 72_000;
    private static final String STAFF_SCRIPT = """
            startup:createItem("magic_staff", function(item)
                item:useAnimation("BOW")
            """ + "                    :useDuration(" + USE_DURATION + ")\n" + """
                    :onUseTick(function(player, stack, ticks) end)
                    :onReleaseUsing(function(player, stack, timeLeft) end)
                    :onFinishUsing(function(player, stack) end)
                    :onHurtEnemy(function(target, attacker, stack) end)
                    :onMineBlock(function(player, stack, x, y, z) end)
                    :onSwing(function(player, stack) end)
            end)
        """;

    private IItemBuilder staff() throws IOException {
        engine.executeScript(scriptFile(STAFF_SCRIPT), STAFF_CHUNK);
        return item("magic_staff");
    }

    @Test
    void staffRegistered() throws IOException {
        staff();

        assertEquals(1, contentService.getRegisteredItems().stream()
                .filter(candidate -> "magic_staff".equals(candidate.getId())).count(),
                "the staff must be registered");
    }

    @Test
    void keepsUseAnimation() throws IOException {
        assertEquals("BOW", staff().getUseAnimation(), "the use animation must be kept");
    }

    @Test
    void keepsUseDuration() throws IOException {
        assertEquals(USE_DURATION, staff().getUseDuration(), "the use duration must be kept");
    }

    @Test
    void keepsUseTickHandler() throws IOException {
        assertNotNull(staff().getOnUseTickHandler(), "the use-tick handler must be stored");
    }

    @Test
    void keepsReleaseUsingHandler() throws IOException {
        assertNotNull(staff().getOnReleaseUsingHandler(), "the release-using handler must be stored");
    }

    @Test
    void keepsFinishUsingHandler() throws IOException {
        assertNotNull(staff().getOnFinishUsingHandler(), "the finish-using handler must be stored");
    }

    @Test
    void keepsHurtEnemyHandler() throws IOException {
        assertNotNull(staff().getOnHurtEnemyHandler(), "the hurt-enemy handler must be stored");
    }

    @Test
    void keepsMineBlockHandler() throws IOException {
        assertNotNull(staff().getOnMineBlockHandler(), "the mine-block handler must be stored");
    }

    @Test
    void keepsSwingHandler() throws IOException {
        assertNotNull(staff().getOnSwingHandler(), "the swing handler must be stored");
    }
}
