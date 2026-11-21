package com.luatweaker.update;

import com.luatweaker.api.vm.ILuaTable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pins {@code UpdateService:GetUpdates()}: only available updates are listed, failed or up-to-date
 * checks are omitted, and each entry carries the feed's latest version.
 */
class UpdateListTest extends UpdateServiceFixture {

    private static final String CURRENT = "1.0.0";
    private static final String FEED_URL = "https://example.com/u";

    /** Seeds one update, one up-to-date mod and one failed check, then reads GetUpdates(). */
    private ILuaTable seedAndReadUpdates() {
        UpdateServiceImpl.recordResult(new UpdateStatus("z_mod", CURRENT, FEED_URL, "1.1.0",
                null, null, null, false, null));
        UpdateServiceImpl.recordResult(new UpdateStatus("a_mod", CURRENT, FEED_URL, CURRENT,
                null, null, null, false, null));
        UpdateServiceImpl.recordResult(new UpdateStatus("b_mod", CURRENT, FEED_URL, null, null,
                null, null, false, "boom"));
        return new UpdateServiceImpl(engine).GetUpdates().asTable();
    }

    private List<String> listedIds(ILuaTable updates) {
        List<String> ids = new ArrayList<>();
        updates.forEach((key, value) -> ids.add(key.asString()));
        return ids;
    }

    @Test
    void onlyAvailableUpdatesAreListed() {
        ILuaTable updates = seedAndReadUpdates();
        assertEquals(List.of("z_mod"), listedIds(updates),
                "GetUpdates must list the mod with a newer feed version and omit up-to-date "
                        + "or failed checks");
    }

    @Test
    void listedUpdateCarriesTheLatestVersion() {
        ILuaTable updates = seedAndReadUpdates();
        assertEquals("1.1.0", updates.rawget("z_mod").asTable().rawget("LatestVersion").asString(),
                "each listed update must expose the feed version under LatestVersion");
    }
}
