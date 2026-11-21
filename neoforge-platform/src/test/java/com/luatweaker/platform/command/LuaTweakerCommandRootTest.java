package com.luatweaker.platform.command;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Own-root merging: a second nested command under a root this mod already owns
 * merges into the live tree, while foreign roots still reject loudly.
 */
class LuaTweakerCommandRootTest {

    private static final String SHOP_INFO = "shop/info";
    private static final String SHOP_BUY = "shop/buy";
    private static final String SHOP_ROOT = "shop";
    private static final Set<String> LIVE_TREE = Set.of("luatweaker", "lt", SHOP_ROOT, "tell");

    @Test
    void decidePathSiblingUnderOwnLiveRootMerges() {
        // /shop/buy went live in cycle 1, /shop/info is brand-new in cycle 2
        // while the live dispatcher still lists the "shop" root.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REGISTER,
                LuaTweakerCommandRegistry.decidePath(SHOP_INFO,
                        LIVE_TREE,
                        new HashSet<>(), Set.of(SHOP_BUY)),
                "a new sibling under this mod's own live root must register");
    }

    @Test
    void decidePathAlreadyLiveSiblingSkipsQuietly() {
        // The already-live sibling still skips quietly.
        assertEquals(LuaTweakerCommandRegistry.PathDecision.SKIP_ALREADY_LIVE,
                LuaTweakerCommandRegistry.decidePath(SHOP_BUY,
                        LIVE_TREE,
                        new HashSet<>(), Set.of(SHOP_BUY)),
                "the already-live sibling must skip without noise");
    }

    @Test
    void decidePathForeignRootStillRejectsLoudly() {
        // A foreign/vanilla root still rejects loudly (no own-root merge).
        assertEquals(LuaTweakerCommandRegistry.PathDecision.REJECT_OCCUPIED,
                LuaTweakerCommandRegistry.decidePath("tell/info",
                        LIVE_TREE,
                        new HashSet<>(), Set.of(SHOP_BUY)),
                "a nested path under a foreign root must be rejected");
    }

    @Test
    void isOwnLiveRootMatchesExactRoot() {
        assertTrue(LuaTweakerCommandRegistry.isOwnLiveRoot(SHOP_ROOT, Set.of(SHOP_BUY, "store")),
                "an exact live root must count as this mod's own root");
    }

    @Test
    void isOwnLiveRootMatchesSecondExactRoot() {
        assertTrue(LuaTweakerCommandRegistry.isOwnLiveRoot("store", Set.of(SHOP_BUY, "store")),
                "an exact live root must count as this mod's own root");
    }

    @Test
    void isOwnLiveRootMatchesSingleRoot() {
        assertTrue(LuaTweakerCommandRegistry.isOwnLiveRoot(SHOP_ROOT, Set.of(SHOP_ROOT)),
                "a lone live root must count as this mod's own root");
    }

    @Test
    void isOwnLiveRootRejectsForeignRoot() {
        assertFalse(LuaTweakerCommandRegistry.isOwnLiveRoot("tell", Set.of(SHOP_BUY)),
                "a foreign root must never count as this mod's own root");
    }

    @Test
    void isOwnLiveRootRejectsPrefixCoincidence() {
        assertFalse(LuaTweakerCommandRegistry.isOwnLiveRoot("shopkeeper", Set.of(SHOP_BUY)),
                "a name that merely starts like the root must not merge");
    }
}
