package com.luatweaker.common.datapack;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.pal.IPlatformDatapack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import com.luatweaker.common.CommonServerHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Read-only view of datapacks already loaded by the running server (vanilla + other mods).
 *
 * <p>Covers {@code SERVER_DATA} only: the server {@link ResourceManager} never sees client
 * assets, so every {@code assets/...} query honestly returns empty. Virtual files registered
 * through Lua are NOT included here; those are served from RAM by
 * {@code IDatapackService#getData}.
 *
 * <p>All queries are null-safe outside a running server (unit tests, early startup):
 * they return {@code null}, {@code false}, or an empty list instead of crashing.
 */
public final class MinecraftDatapackPlatform implements IPlatformDatapack {

    private static final String DATA_PREFIX = "data/";
    private static final String ASSETS_PREFIX = "assets/";
    private static final int MAX_READ_BYTES = 8 * 1024 * 1024;

    @Override
    @Nullable
    public String readLoaded(@NotNull String relPath) {
        Parsed parsed = parseDataKey(relPath);
        if (parsed == null) {
            return null;
        }
        ResourceLocation id;
        try {
            id = ResourceLocation.fromNamespaceAndPath(parsed.namespace, parsed.path);
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Datapack] Invalid resource location '" + relPath + "': " + e.getMessage());
            return null;
        }
        Optional<Resource> resource = resourceOf(id);
        if (resource.isEmpty()) {
            return null;
        }
        try (InputStream in = resource.get().open()) {
            byte[] bytes = in.readAllBytes();
            if (bytes.length > MAX_READ_BYTES) {
                LuaTweakerLog.get().warn(LogStage.SYSTEM,
                        "[Datapack] Refusing to read oversized loaded file '" + relPath + "' (" + bytes.length + " bytes)");
                return null;
            }
            return strictUtf8(bytes);
        } catch (java.io.IOException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Datapack] Failed to read loaded file '" + relPath + "': " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean loadedExists(@NotNull String relPath) {
        Parsed parsed = parseDataKey(relPath);
        if (parsed == null) {
            return false;
        }
        ResourceLocation id;
        try {
            id = ResourceLocation.fromNamespaceAndPath(parsed.namespace, parsed.path);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return resourceOf(id).isPresent();
    }

    @Override
    @NotNull
    public List<String> listLoaded(@NotNull String prefix) {
        String normalized = normalize(prefix);
        if (normalized.startsWith(ASSETS_PREFIX)) {
            return List.of();
        }
        MinecraftServer server = CommonServerHolder.get();
        if (server == null) {
            return List.of();
        }
        String rest = normalized.startsWith(DATA_PREFIX)
                ? normalized.substring(DATA_PREFIX.length())
                : normalized;
        int slash = rest.indexOf('/');
        String namespaceFilter;
        String folder;
        if (slash >= 0) {
            namespaceFilter = rest.substring(0, slash);
            folder = rest.substring(slash + 1);
        } else if (rest.isEmpty()) {
            namespaceFilter = "";
            folder = "";
        } else {
            namespaceFilter = rest;
            folder = "";
        }
        String wantedPrefix = normalized.startsWith(DATA_PREFIX) ? normalized : DATA_PREFIX + normalized;
        return queryServerResources(server.getResourceManager(), folder, namespaceFilter, wantedPrefix, prefix);
    }

    private static List<String> queryServerResources(ResourceManager manager, String folder,
                                                     String wantedNamespace, String wantedPrefix, String prefix) {
        try {
            Map<ResourceLocation, Resource> found = manager.listResources(folder, rl -> wantedNamespace.isEmpty()
                    || rl.getNamespace().equals(wantedNamespace));
            Set<String> sorted = new TreeSet<>();
            for (ResourceLocation rl : found.keySet()) {
                String key = DATA_PREFIX + rl.getNamespace() + "/" + rl.getPath();
                if (wantedPrefix.isEmpty() || DATA_PREFIX.equals(wantedPrefix) || key.startsWith(wantedPrefix)) {
                    sorted.add(key);
                }
            }
            return new ArrayList<>(sorted);
        } catch (IllegalArgumentException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "[Datapack] Cannot list loaded files under '" + prefix + "': " + e.getMessage());
            return List.of();
        }
    }

    @Override
    @NotNull
    public List<String> loadedNamespaces(@NotNull String packDir) {
        String dir = normalize(packDir);
        if (!"data".equals(dir)) {
            return List.of();
        }
        MinecraftServer server = CommonServerHolder.get();
        if (server == null) {
            return List.of();
        }
        return new ArrayList<>(new TreeSet<>(server.getResourceManager().getNamespaces()));
    }

    /** Splits a canonical {@code data/<namespace>/<path>} key; null when not a data key. */
    @Nullable
    private static Parsed parseDataKey(@NotNull String relPath) {
        String normalized = normalize(relPath);
        if (normalized.startsWith(ASSETS_PREFIX)) {
            return null;
        }
        String rest = normalized.startsWith(DATA_PREFIX)
                ? normalized.substring(DATA_PREFIX.length())
                : normalized;
        int slash = rest.indexOf('/');
        if (slash <= 0 || slash == rest.length() - 1) {
            return null;
        }
        return new Parsed(rest.substring(0, slash), rest.substring(slash + 1));
    }

    private static String normalize(@NotNull String raw) {
        String normalized = raw == null ? "" : raw.strip().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private static Optional<Resource> resourceOf(ResourceLocation id) {
        MinecraftServer server = CommonServerHolder.get();
        if (server == null) {
            return Optional.empty();
        }
        return server.getResourceManager().getResource(id);
    }

    @Nullable
    private static String strictUtf8(byte[] bytes) throws CharacterCodingException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        CharBuffer decoded = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(buffer);
        return decoded.toString();
    }

    private record Parsed(String namespace, String path) {
    }
}
