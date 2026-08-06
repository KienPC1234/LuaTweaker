package com.luatweaker.platform.content;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
 *       the filesystem (lazy read).</li>
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

    public LuaTweakerVirtualPackResources(PackLocationInfo location,
                                          PackType packType,
                                          File luaDir,
                                          IDatapackService datapackService) {
        super(location);
        this.packType = packType;
        this.luaDir = luaDir;
        this.datapackService = datapackService;
    }

    // -------------------------------------------------------------------------
    // Root resource (pack.mcmeta, pack.png)
    // -------------------------------------------------------------------------

    @Override
    public IoSupplier<InputStream> getRootResource(String... paths) {
        if (paths.length == 1 && "pack.mcmeta".equals(paths[0])) {
            File metaFile = new File(luaDir, "pack.mcmeta");
            if (metaFile.exists()) {
                return () -> new FileInputStream(metaFile);
            }
        // Serve dynamic in-memory mcmeta with the correct pack format for the
        // served pack type (48 for data packs, 34 for resource packs).
        int format = packType == PackType.SERVER_DATA ? DATA_PACK_FORMAT : RESOURCE_PACK_FORMAT;
        String meta = String.format(PACK_MCMETA, format);
        return () -> new ByteArrayInputStream(meta.getBytes(StandardCharsets.UTF_8));
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Named resource (the main entry point Minecraft calls to load data)
    // -------------------------------------------------------------------------

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
        if (type != packType) return null;

        // Priority 1: Virtual files from Lua scripts (in-memory, highest priority)
        String virtualKey = type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath();
        if (datapackService != null) {
            String content = datapackService.getVirtualFiles().get(virtualKey);
            if (content != null) {
                LuaTweakerLog.get().info(LogStage.SYSTEM,
                        "[VirtualPack] Serving in-memory: " + virtualKey);
                return () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
            }
        }

        // Priority 2: Static files on disk under lua/data/ or lua/assets/
        File physicalFile = resolvePhysicalFile(type, location);
        if (physicalFile != null && physicalFile.exists()) {
            return () -> new FileInputStream(physicalFile);
        }

        return null;
    }

    // -------------------------------------------------------------------------
    // Resource listing — Minecraft calls this to discover what exists
    // -------------------------------------------------------------------------

    @Override
    public void listResources(PackType type, String namespace, String prefix,
                              ResourceOutput resourceOutput) {
        if (type != packType) return;

        // Collect from virtual (RAM) files
        if (datapackService != null) {
            String dirPrefix = type.getDirectory() + "/" + namespace + "/";
            for (Map.Entry<String, String> entry : datapackService.getVirtualFiles().entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(dirPrefix)) {
                    String relativePath = key.substring(dirPrefix.length()); // e.g. "loot_table/blocks/ruby_ore.json"
                    // Directory-segment prefix match: "dimension" must NOT match
                    // "dimension_type/..." (RegistryDataLoader would decode the
                    // dimension type file with the LevelStem codec and crash).
                    if (matchesPrefix(relativePath, prefix)) {
                        ResourceLocation rl = safeResourceLocation(namespace, relativePath);
                        if (rl != null) {
                            String content = entry.getValue();
                            resourceOutput.accept(rl,
                                    () -> new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
                        }
                    }
                }
            }
        }

        // From physical directories under lua/<packTypeDir>/<namespace>/<prefix>/
        File physRoot = new File(luaDir, type.getDirectory() + "/" + namespace);
        File prefixDir = prefix.isEmpty() ? physRoot : new File(physRoot, prefix);
        if (prefixDir.exists() && prefixDir.isDirectory()) {
            collectPhysicalResources(prefixDir, physRoot.toPath(), namespace, resourceOutput, datapackService, type);
        }

        // From luamods/<mod_id>/<packTypeDir>/<namespace>/<prefix>/
        File luamodsDir = getLuamodsDir();
        if (luamodsDir.exists() && luamodsDir.isDirectory()) {
            File[] mods = luamodsDir.listFiles(File::isDirectory);
            if (mods != null) {
                for (File modFolder : mods) {
                    File modNsRoot = new File(modFolder, type.getDirectory() + "/" + namespace);
                    File modPrefixDir = prefix.isEmpty() ? modNsRoot : new File(modNsRoot, prefix);
                    if (modPrefixDir.exists() && modPrefixDir.isDirectory()) {
                        collectPhysicalResources(modPrefixDir, modNsRoot.toPath(), namespace, resourceOutput, datapackService, type);
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Namespace discovery — Minecraft needs this to know what namespaces exist
    // -------------------------------------------------------------------------

    @Override
    public Set<String> getNamespaces(PackType type) {
        if (type != packType) return Set.of();

        Set<String> namespaces = new HashSet<>();

        // From virtual files
        if (datapackService != null) {
            String prefix = type.getDirectory() + "/";
            for (String key : datapackService.getVirtualFiles().keySet()) {
                if (key.startsWith(prefix)) {
                    String rest = key.substring(prefix.length()); // "luatweaker/loot_table/..."
                    int slash = rest.indexOf('/');
                    if (slash > 0) {
                        namespaces.add(rest.substring(0, slash));
                    }
                }
            }
        }

        // From physical directories
        File physRoot = new File(luaDir, type.getDirectory());
        if (physRoot.exists() && physRoot.isDirectory()) {
            File[] dirs = physRoot.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File dir : dirs) {
                    namespaces.add(dir.getName());
                }
            }
        }

        // From luamods directories
        File luamodsDir = getLuamodsDir();
        if (luamodsDir.exists() && luamodsDir.isDirectory()) {
            File[] mods = luamodsDir.listFiles(File::isDirectory);
            if (mods != null) {
                for (File modFolder : mods) {
                    File packDir = new File(modFolder, type.getDirectory());
                    if (packDir.exists() && packDir.isDirectory()) {
                        File[] nsDirs = packDir.listFiles(File::isDirectory);
                        if (nsDirs != null) {
                            for (File nsDir : nsDirs) {
                                namespaces.add(nsDir.getName());
                            }
                        }
                    }
                }
            }
        }

        return namespaces;
    }

    @Override
    public void close() {
        // No resources to close — everything is in-memory or read on demand
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private File getLuamodsDir() {
        if ("luamods".equalsIgnoreCase(luaDir.getName())) return luaDir;
        File sub = new File(luaDir, "luamods");
        return sub.exists() ? sub : luaDir;
    }

    private File resolvePhysicalFile(PackType type, ResourceLocation location) {
        String relativePath = type.getDirectory()
                + "/" + location.getNamespace()
                + "/" + location.getPath();
        File direct = new File(luaDir, relativePath);
        if (direct.exists()) return direct;

        File luamodsDir = getLuamodsDir();
        if (luamodsDir.exists() && luamodsDir.isDirectory()) {
            File[] mods = luamodsDir.listFiles(File::isDirectory);
            if (mods != null) {
                for (File modFolder : mods) {
                    File modFile = new File(modFolder, relativePath);
                    if (modFile.exists()) return modFile;
                }
            }
        }

        return direct;
    }

    /**
     * Directory-segment prefix match: {@code relativePath} is under the
     * {@code prefix} directory (or {@code prefix} is empty). "dimension" must
     * NOT match "dimension_type/...".
     */
    static boolean matchesPrefix(String relativePath, String prefix) {
        return prefix == null || prefix.isEmpty()
                || relativePath.equals(prefix)
                || relativePath.startsWith(prefix + "/");
    }

    // -------------------------------------------------------------------------
    // Resource listing helper
    // -------------------------------------------------------------------------

    private static void collectPhysicalResources(File dir, Path nsRoot, String namespace,
                                                  ResourceOutput output,
                                                  IDatapackService datapackService,
                                                  PackType type) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectPhysicalResources(child, nsRoot, namespace, output, datapackService, type);
            } else if (child.isFile()) {
                String relToNs = nsRoot.relativize(child.toPath()).toString().replace('\\', '/');
                // Skip if already served from virtual (RAM wins)
                String virtualKey = type.getDirectory() + "/" + namespace + "/" + relToNs;
                if (datapackService != null && datapackService.getVirtualFiles().containsKey(virtualKey)) {
                    continue; // virtual already registered above
                }
                ResourceLocation rl = safeResourceLocation(namespace, relToNs);
                if (rl != null) {
                    output.accept(rl, () -> new FileInputStream(child));
                }
            }
        }
    }

    private static ResourceLocation safeResourceLocation(String namespace, String path) {
        try {
            return ResourceLocation.fromNamespaceAndPath(namespace, path);
        } catch (Exception e) {
            return null;
        }
    }
}
