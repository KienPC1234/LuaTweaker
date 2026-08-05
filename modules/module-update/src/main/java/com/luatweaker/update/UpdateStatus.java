package com.luatweaker.update;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable result of one update check for one LuaMod.
 *
 * <p>While the engine is downloading {@code updateUrl} the status is stored
 * with {@code checking = true} and a null {@code latestVersion}. On failure an
 * {@code error} message is recorded so the failure is visible through
 * {@code Mod.GetUpdateStatus()} instead of silently vanishing.</p>
 *
 * @param modId          the manifest id of the checked mod
 * @param currentVersion the installed version from the manifest
 * @param updateUrl      the update feed URL declared in the manifest (https only)
 * @param latestVersion  the newest version reported by the feed, or null while
 *                       checking / on failure
 * @param updateName     optional human-readable feed name (e.g. release title)
 * @param downloadUrl    optional direct download link from the feed
 * @param changelog      optional changelog text from the feed
 * @param checking       true while the fetch is still in flight
 * @param error          failure reason, or null when the check succeeded
 */
public record UpdateStatus(
        @NotNull String modId,
        @NotNull String currentVersion,
        @Nullable String updateUrl,
        @Nullable String latestVersion,
        @Nullable String updateName,
        @Nullable String downloadUrl,
        @Nullable String changelog,
        boolean checking,
        @Nullable String error
) {
    /** @return true when a newer version was confirmed by the feed. */
    public boolean hasUpdate() {
        return !checking && error == null && latestVersion != null
                && UpdateVersion.compare(currentVersion, latestVersion) < 0;
    }
}
