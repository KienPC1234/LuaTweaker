package com.luatweaker.common.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ClientMobParentRegistryTest {

    @Test
    void standardParentsResolveNonNullFactories() {
        assertAll("Standard parent mob renderers must resolve to valid factories",
                () -> assertNotNull(ClientMobParentRegistry.getFactory("iron_golem"), "iron_golem renderer must resolve"),
                () -> assertNotNull(ClientMobParentRegistry.getFactory("cow"), "cow renderer must resolve"),
                () -> assertNotNull(ClientMobParentRegistry.getFactory("pig"), "pig renderer must resolve"),
                () -> assertNotNull(ClientMobParentRegistry.getFactory("spider"), "spider renderer must resolve"),
                () -> assertNotNull(ClientMobParentRegistry.getFactory("creeper"), "creeper renderer must resolve"),
                () -> assertNotNull(ClientMobParentRegistry.getFactory("skeleton"), "skeleton renderer must resolve")
        );
    }

    @Test
    void golemAliasSharesIronGolemFactory() {
        assertSame(ClientMobParentRegistry.getFactory("iron_golem"), ClientMobParentRegistry.getFactory("golem"),
                "golem alias must point to the iron_golem factory");
    }

    @Test
    void unknownParentSafelyFallsBack() {
        assertAll("Unknown and null parents must safely fall back to the default renderer",
                () -> assertNotNull(ClientMobParentRegistry.getFactory("unknown_creature"), "unknown parent must return fallback"),
                () -> assertNotNull(ClientMobParentRegistry.getFactory(null), "null parent must return fallback")
        );
    }
}
