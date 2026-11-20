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
public class LuaTweakerCommandRegistryTest {

    private static CommandDefinition def(String modId, String name, String... aliases) {
        return new CommandDefinition(modId, name, "desc", 0, true, "usage",
                List.of(aliases), null, null, null);
    }

    @Test
    void allPathsOf_primaryFirstThenAliases() {
        assertEquals(List.of("shop/buy", "shop", "store"),
                LuaTweakerCommandRegistry.allPathsOf(def("mymod", "shop/buy", "shop", "store")));
    }

    @Test
    void decidePath_freshPathRegisters() {
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REGISTER,
                LuaTweakerCommandRegistry.decidePath("hello", Set.of("luatweaker", "lt"),
                        new HashSet<>(), new HashSet<>()));
    }

    @Test
    void decidePath_previousCyclePathSkipsQuietly() {
        assertEquals(LuaTweakerCommandRegistry.PathDecision.SKIP_ALREADY_LIVE,
                LuaTweakerCommandRegistry.decidePath("hello", Set.of("luatweaker", "lt", "hello"),
                        new HashSet<>(), Set.of("hello")));
    }

    @Test
    void decidePath_sameCycleDuplicateStaysLoud() {
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REJECT_DUPLICATE_THIS_CYCLE,
                LuaTweakerCommandRegistry.decidePath("hello", Set.of("luatweaker", "lt"),
                        Set.of("hello"), new HashSet<>()));
    }

    @Test
    void decidePath_occupiedPrefixRejected() {
        // "shop" taken by vanilla/another mod: nested path must not step into it.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REJECT_OCCUPIED,
                LuaTweakerCommandRegistry.decidePath("shop/buy", Set.of("luatweaker", "shop"),
                        new HashSet<>(), new HashSet<>()));
        // Sibling root is fine.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REGISTER,
                LuaTweakerCommandRegistry.decidePath("store/buy", Set.of("luatweaker", "shop"),
                        new HashSet<>(), new HashSet<>()));
    }

    @Test
    void decidePath_cyclePriorityDuplicateOverLiveOverOccupied() {
        Set<String> live = new HashSet<>(Set.of("hello"));
        Set<String> justAdded = new HashSet<>(Set.of("hello"));
        // Same-cycle duplicate wins even when the path is also live + occupied.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REJECT_DUPLICATE_THIS_CYCLE,
                LuaTweakerCommandRegistry.decidePath("hello", Set.of("hello"), justAdded, live));
        // Live wins over occupied (dispatcher still lists it from the earlier cycle).
        assertEquals(LuaTweakerCommandRegistry.PathDecision.SKIP_ALREADY_LIVE,
                LuaTweakerCommandRegistry.decidePath("hello", Set.of("hello"), new HashSet<>(), live));
        assertTrue(live.contains("hello"));
    }

    @Test
    void decidePath_siblingUnderOwnLiveRootMerges() {
        // /shop/buy went live in cycle 1, /shop/info is brand-new in cycle 2
        // while the live dispatcher still lists the "shop" root.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REGISTER,
                LuaTweakerCommandRegistry.decidePath("shop/info",
                        Set.of("luatweaker", "lt", "shop", "tell"),
                        new HashSet<>(), Set.of("shop/buy")));
        // The already-live sibling still skips quietly.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.SKIP_ALREADY_LIVE,
                LuaTweakerCommandRegistry.decidePath("shop/buy",
                        Set.of("luatweaker", "lt", "shop", "tell"),
                        new HashSet<>(), Set.of("shop/buy")));
        // A foreign/vanilla root still rejects loudly (no own-root merge).
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REJECT_OCCUPIED,
                LuaTweakerCommandRegistry.decidePath("tell/info",
                        Set.of("luatweaker", "lt", "shop", "tell"),
                        new HashSet<>(), Set.of("shop/buy")));
    }

    @Test
    void isOwnLiveRoot_matchesExactAndNestedPaths() {
        assertTrue(LuaTweakerCommandRegistry.isOwnLiveRoot("shop", Set.of("shop/buy", "store")));
        assertTrue(LuaTweakerCommandRegistry.isOwnLiveRoot("store", Set.of("shop/buy", "store")));
        assertTrue(LuaTweakerCommandRegistry.isOwnLiveRoot("shop", Set.of("shop")));
        assertEquals(false, LuaTweakerCommandRegistry.isOwnLiveRoot("tell", Set.of("shop/buy")));
        assertEquals(false, LuaTweakerCommandRegistry.isOwnLiveRoot("shopkeeper", Set.of("shop/buy")));
    }
}
