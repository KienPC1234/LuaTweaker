package com.luatweaker.common.content;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A 100% in-memory {@link net.minecraft.server.packs.PackResources} implementation.
 *
 * <p>Mimics KubeJS's Virtual DataPack approach:
 * <ol>
 *   <li>Virtual files registered via Lua scripts ({@link IDatapackService}) are served from RAM,
 *       taking highest priority.</li>
 *   <li>Static JSON files under {@code lua/data/} or {@code lua/assets/} are served as-is from
 *       the filesystem (lazy read, see {@link VirtualPackDirectoryScanner}).</li>
 * </ol>
 *
 * <p>No physical files are ever written for virtual data — they live entirely in memory.
 */
public class LuaTweakerVirtualPackResources extends AbstractPackResources {

    private static final String PACK_MCMETA = """
{
"pack": {
"pack_format": %d,
"description": "LuaTweaker Virtual DataPack (KubeJS-style)"
}
}
""";
    /** 1.21.1 data pack format. */
    private static final int DATA_PACK_FORMAT = 48;
    /** 1.21.1 resource pack format. */
    private static final int RESOURCE_PACK_FORMAT = 34;

    private final PackType packType;
    private final File luaDir;
    private final IDatapackService datapackService;
    private final VirtualPackDirectoryScanner directoryScanner;

    public LuaTweakerVirtualPackResources(PackLocationInfo location,
                                          PackType packType,
                                          File luaDir,
                                          IDatapackService datapackService) {
        super(location);
        this.packType = packType;
        this.luaDir = luaDir;
        this.datapackService = datapackService;
        this.directoryScanner = new VirtualPackDirectoryScanner(luaDir);
    }

    // -------------------------------------------------------------------------
    // Root resource (pack.mcmeta, pack.png)
    // -------------------------------------------------------------------------

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        if (paths.length != 1 || !"pack.mcmeta".equals(paths[0])) {
            return null;
        }
        File metaFile = new File(luaDir, "pack.mcmeta");
        if (metaFile.exists()) {
            return () -> Files.newInputStream(metaFile.toPath());
        }
        // Serve dynamic in-memory mcmeta with the correct pack format for the
        // served pack type (48 for data packs, 34 for resource packs).
        int format = packType == PackType.SERVER_DATA ? DATA_PACK_FORMAT : RESOURCE_PACK_FORMAT;
        return utf8Supplier(String.format(PACK_MCMETA, format));
    }

    // -------------------------------------------------------------------------
    // Named resource (the main entry point Minecraft calls to load data)
    // -------------------------------------------------------------------------

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != packType) { return null; }

        // Priority 1: Virtual files from Lua scripts (in-memory, highest priority)
        String virtualKey = type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath();
        if (datapackService != null) {
            String content = datapackService.getVirtualFiles().get(virtualKey);
            if (content != null) {
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[VirtualPack] Serving in-memory: " + virtualKey);
                return utf8Supplier(content);
            }
        }

        // Priority 2: Static files on disk under lua/data/ or lua/assets/, including
        // zip-packaged LuaMods (lazy read, same layout as folders).
        IoSupplier<InputStream> physical = directoryScanner.resolveResourceStream(type, location);
        if (physical != null) {
            return physical;
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Resource listing — Minecraft calls this to discover what exists
    // -------------------------------------------------------------------------

    @Override
    public void listResources(PackType type, String namespace, String prefix,
                              ResourceOutput resourceOutput) {
        if (type != packType) { return; }
        collectVirtualResources(type, namespace, prefix, resourceOutput);
        directoryScanner.collectResources(type, namespace, prefix, resourceOutput::accept, datapackService);
    }

    // -------------------------------------------------------------------------
    // Namespace discovery — Minecraft needs this to know what namespaces exist
    // -------------------------------------------------------------------------

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != packType) { return Set.of(); }
        Set<String> namespaces = new HashSet<>();
        collectVirtualNamespaces(type, namespaces);
        namespaces.addAll(directoryScanner.listNamespaces(type));
        return namespaces;
    }

    @Override
    public void close() {
        // No resources to close — everything is in-memory or read on demand
    }

    // -------------------------------------------------------------------------
    // Virtual (in-memory) listing helpers
    // -------------------------------------------------------------------------

    private void collectVirtualResources(PackType type, String namespace, String prefix,
                                         ResourceOutput output) {
        if (datapackService == null) { return; }
        String dirPrefix = type.getDirectory() + "/" + namespace + "/";
        for (Map.Entry<String, String> entry : datapackService.getVirtualFiles().entrySet()) {
            acceptVirtualEntry(entry, dirPrefix, prefix, namespace, output);
        }
    }

    private static void acceptVirtualEntry(Map.Entry<String, String> entry, String dirPrefix,
                                           String prefix, String namespace, ResourceOutput output) {
        String key = entry.getKey();
        if (!key.startsWith(dirPrefix)) { return; }
        String relativePath = key.substring(dirPrefix.length());
        // Directory-segment prefix match: "dimension" must NOT match
        // "dimension_type/..." (RegistryDataLoader would decode the
        // dimension type file with the LevelStem codec and crash).
        if (!VirtualPackDirectoryScanner.matchesPrefix(relativePath, prefix)) { return; }
        ResourceLocation rl = VirtualPackDirectoryScanner.safeResourceLocation(namespace, relativePath);
        if (rl == null) { return; }
        output.accept(rl, utf8Supplier(entry.getValue()));
    }

    private void collectVirtualNamespaces(PackType type, Set<String> namespaces) {
        if (datapackService == null) { return; }
        String prefix = type.getDirectory() + "/";
        for (String key : datapackService.getVirtualFiles().keySet()) {
            String namespace = virtualNamespace(key, prefix);
            if (namespace != null) { namespaces.add(namespace); }
        }
    }

    /** Namespace segment of a virtual key ({@code luatweaker/loot_table/...}), if any. */
    @Nullable
    private static String virtualNamespace(String key, String prefix) {
        if (!key.startsWith(prefix)) { return null; }
        String rest = key.substring(prefix.length());
        int slash = rest.indexOf('/');
        if (slash <= 0) { return null; }
        return rest.substring(0, slash);
    }

    private static IoSupplier<InputStream> utf8Supplier(String content) {
        return () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
