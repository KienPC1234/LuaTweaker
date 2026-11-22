package com.luatweaker.common.client.luamods;

import com.luatweaker.core.mod.ModSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mod-list filter must match ids and display names case-insensitively. */
class LuamodClientFilterTest {

    private static List<ModSummary> twoMods() {
        ModSummary economy = new ModSummary(
                "mod_economy", "Economy Plus", "1.0.0", "dev", "universal",
                true, true, null, List.of(), List.of(), List.of(), Map.of());
        ModSummary powers = new ModSummary(
                "lib_powers", "Super Powers", "2.0.0", "dev", "universal",
                true, true, null, List.of(), List.of(), List.of(), Map.of());
        return List.of(economy, powers);
    }

    @Test
    void nullQueryKeepsEveryMod() {
        assertEquals(2, LuamodClientActions.filterSummaries(twoMods(), null).size(),
                "a null query must not filter anything out");
    }

    @Test
    void blankQueryKeepsEveryMod() {
        assertEquals(2, LuamodClientActions.filterSummaries(twoMods(), "  ").size(),
                "a blank query must not filter anything out");
    }

    @Test
    void idPrefixMatches() {
        List<ModSummary> all = twoMods();

        assertEquals(List.of(all.get(0)), LuamodClientActions.filterSummaries(all, "econ"),
                "typing an id prefix must keep that mod");
    }

    @Test
    void nameMatchesCaseInsensitively() {
        List<ModSummary> all = twoMods();

        assertEquals(List.of(all.get(1)), LuamodClientActions.filterSummaries(all, "SUPER"),
                "typing a display-name fragment in caps must still match");
    }

    @Test
    void unknownQueryMatchesNothing() {
        assertTrue(LuamodClientActions.filterSummaries(twoMods(), "ghost").isEmpty(),
                "a query matching nothing must yield an empty list");
    }
}
