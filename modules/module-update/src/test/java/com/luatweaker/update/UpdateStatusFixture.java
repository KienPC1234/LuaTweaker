package com.luatweaker.update;

/**
 * Fixture for the status-table tests: builds statuses for one pinned mod and reads single flags
 * from the Lua table.
 */
class UpdateStatusFixture extends UpdateServiceFixture {

    protected static final String MOD_ID = "gt_addon";
    protected static final String CURRENT = "1.0.2";
    protected static final String LATEST = "1.1.0";
    protected static final String FEED_URL = "https://example.com/update.json";
    protected static final String HTTP_404 = "update_url returned HTTP 404";

    protected static UpdateStatus status(String current, String latestVersion, boolean checking,
                                         String error) {
        return new UpdateStatus(MOD_ID, current, FEED_URL, latestVersion, null, null, null,
                checking, error);
    }

    protected boolean tableFlag(String key) {
        return new UpdateServiceImpl(engine).GetStatus(MOD_ID).asTable().rawget(key).asBoolean();
    }
}
