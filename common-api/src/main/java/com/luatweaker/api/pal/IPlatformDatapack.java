package com.luatweaker.api.pal;

import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Read-only access to datapacks already loaded by Minecraft (vanilla + other mods).
 *
 * <p>Virtual files registered through {@code IDatapackService} are NOT included here;
 * use {@code IDatapackService#getData} for those. All paths are canonical virtual keys
 * ({@code data/<namespace>/<path>} or {@code assets/<namespace>/<path>}).
 */
public interface IPlatformDatapack {

    /**
     * Reads a loaded datapack file as UTF-8 text.
     *
     * @return content, or null when missing, binary, or unreadable
     */
    @Nullable
    String readLoaded(@NotNull String relPath);

    /** Returns true when a loaded datapack provides the path. */
    boolean loadedExists(@NotNull String relPath);

    /**
     * Lists loaded datapack keys starting with prefix (empty prefix lists all).
     *
     * @return sorted keys
     */
    @NotNull
    List<String> listLoaded(@NotNull String prefix);

    /**
     * Lists namespaces visible to loaded packs for a pack dir.
     *
     * @param packDir {@code data} or {@code assets}
     * @return sorted namespaces
     */
    @NotNull
    List<String> loadedNamespaces(@NotNull String packDir);
}
