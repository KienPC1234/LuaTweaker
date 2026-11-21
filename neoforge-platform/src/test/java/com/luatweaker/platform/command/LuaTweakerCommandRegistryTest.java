package com.luatweaker.platform.command;

import com.luatweaker.command.CommandDefinition;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reload-diff logic for Lua Brigadier commands. The dispatcher wiring itself
 * needs Minecraft classes, but the "which paths are new" decision is pure and
 * covered here: fresh paths register, previous-cycle paths skip quietly,
 * same-cycle duplicates and vanilla-occupied prefixes stay loud.
 */
class LuaTweakerCommandRegistryTest {

    private static final String HELLO_PATH = "hello";
    private static final String CORE_NAMESPACE = "luatweaker";
    private static final String SHOP_ROOT = "shop";
    private static final String SHOP_BUY_PATH = "shop/buy";

    static CommandDefinition def(String modId, String name, String... aliases) {
        return new CommandDefinition(modId, name, "desc", 0, true, "usage",
                List.of(aliases), null, null, null);
    }

    @Test
    void allPathsOfPrimaryFirstThenAliases() {
        assertEquals(List.of(SHOP_BUY_PATH, SHOP_ROOT, "store"),
                LuaTweakerCommandRegistry.allPathsOf(def("mymod", SHOP_BUY_PATH, SHOP_ROOT, "store")),
                "the primary path must come first, then aliases in order");
    }

    @Test
    void decidePathFreshPathRegisters() {
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REGISTER,
                LuaTweakerCommandRegistry.decidePath(HELLO_PATH, Set.of(CORE_NAMESPACE, "lt"),
                        new HashSet<>(), new HashSet<>()),
                "a brand-new path must register on the dispatcher");
    }

    @Test
    void decidePathPreviousCyclePathSkipsQuietly() {
        assertEquals(LuaTweakerCommandRegistry.PathDecision.SKIP_ALREADY_LIVE,
                LuaTweakerCommandRegistry.decidePath(HELLO_PATH,
                        Set.of(CORE_NAMESPACE, "lt", HELLO_PATH),
                        new HashSet<>(), Set.of(HELLO_PATH)),
                "a path that is already live must skip without noise");
    }

    @Test
    void decidePathSameCycleDuplicateStaysLoud() {
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REJECT_DUPLICATE_THIS_CYCLE,
                LuaTweakerCommandRegistry.decidePath(HELLO_PATH, Set.of(CORE_NAMESPACE, "lt"),
                        Set.of(HELLO_PATH), new HashSet<>()),
                "registering the same path twice in one cycle must stay loud");
    }

    @Test
    void decidePathNestedUnderOccupiedRootIsRejected() {
        // "shop" taken by vanilla/another mod: nested path must not step into it.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REJECT_OCCUPIED,
                LuaTweakerCommandRegistry.decidePath(SHOP_BUY_PATH, Set.of(CORE_NAMESPACE, SHOP_ROOT),
                        new HashSet<>(), new HashSet<>()),
                "a nested path under an occupied root must be rejected");
    }

    @Test
    void decidePathSiblingUnderFreeRootRegisters() {
        // Sibling root is fine.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REGISTER,
                LuaTweakerCommandRegistry.decidePath("store/buy", Set.of(CORE_NAMESPACE, SHOP_ROOT),
                        new HashSet<>(), new HashSet<>()),
                "a sibling path that avoids the occupied root must register");
    }

    @Test
    void decidePathSameCycleDuplicateWinsOverLive() {
        Set<String> live = new HashSet<>(Set.of(HELLO_PATH));
        Set<String> justAdded = new HashSet<>(Set.of(HELLO_PATH));
        // Same-cycle duplicate wins even when the path is also live + occupied.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REJECT_DUPLICATE_THIS_CYCLE,
                LuaTweakerCommandRegistry.decidePath(HELLO_PATH, Set.of(HELLO_PATH), justAdded, live),
                "a same-cycle duplicate must win even when the path is also live");
    }

    @Test
    void decidePathLiveWinsOverOccupied() {
        Set<String> live = new HashSet<>(Set.of(HELLO_PATH));
        // Live wins over occupied (dispatcher still lists it from the earlier cycle).
        assertEquals(LuaTweakerCommandRegistry.PathDecision.SKIP_ALREADY_LIVE,
                LuaTweakerCommandRegistry.decidePath(HELLO_PATH, Set.of(HELLO_PATH),
                        new HashSet<>(), live),
                "an already-live path must skip even when the root looks occupied");
    }

    @Test
    void decidePathKeepsLiveSetIntact() {
        Set<String> live = new HashSet<>(Set.of(HELLO_PATH));
        LuaTweakerCommandRegistry.decidePath(HELLO_PATH, Set.of(HELLO_PATH),
                new HashSet<>(Set.of(HELLO_PATH)), live);

        assertTrue(live.contains(HELLO_PATH),
                "deciding a path must not drain the live set");
    }
}
