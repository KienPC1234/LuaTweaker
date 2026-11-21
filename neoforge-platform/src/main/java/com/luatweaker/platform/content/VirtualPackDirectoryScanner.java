package com.luatweaker.platform.content;

import com.luatweaker.api.content.IDatapackService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Filesystem half of {@link LuaTweakerVirtualPackResources}: discovers the static JSON shipped
 * under {@code lua/&lt;packType&gt;/&lt;namespace&gt;/...} and
 * {@code luamods/&lt;mod_id&gt;/&lt;packType&gt;/&lt;namespace&gt;/...}.
 *
 * <p>Files a Lua script already registered in memory are skipped, because virtual content
 * always wins over files on disk. Nothing is ever written; the files are read lazily when
 * Minecraft asks for their bytes.</p>
 */
final class VirtualPackDirectoryScanner {

    private static final String LUAMODS_DIR_NAME = "luamods";
    private static final File[] NO_FOLDERS = new File[0];

    private final File luaDir;

    VirtualPackDirectoryScanner(File luaDir) {
        this.luaDir = luaDir;
    }

    /** Namespaces that own at least one directory under the pack-type root. */
    Set<String> listNamespaces(PackType type) {
        Set<String> namespaces = new HashSet<>();
        collectNamespaceDirs(subDir(luaDir, type.getDirectory()), namespaces);
        for (File modFolder : modFolders()) {
            collectNamespaceDirs(subDir(modFolder, type.getDirectory()), namespaces);
        }
        return namespaces;
    }

    /** Accepts every physical file under {@code <namespace>/<prefix>} that is not virtual. */
    void collectResources(PackType type, String namespace, String prefix,
                          BiConsumer<ResourceLocation, IoSupplier<InputStream>> output,
                          IDatapackService datapackService) {
        collectFromNamespaceRoot(subDir(luaDir, type.getDirectory() + "/" + namespace),
                namespace, prefix, output, datapackService, type);
        for (File modFolder : modFolders()) {
            collectFromNamespaceRoot(subDir(modFolder, type.getDirectory() + "/" + namespace),
                    namespace, prefix, output, datapackService, type);
        }
    }

    /**
     * The file backing a resource, or {@code null} when it only exists as a virtual
     * in-memory file (or not at all).
     */
    @Nullable
    File resolveResourceFile(PackType type, ResourceLocation location) {
        String relativePath = type.getDirectory() + "/" + location.getNamespace() + "/" + location.getPath();
        File direct = new File(luaDir, relativePath);
        if (direct.exists()) {
            return direct;
        }
        for (File modFolder : modFolders()) {
            Path candidate = modFolder.toPath().resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate.toFile();
            }
        }
        return direct;
    }

    /**
     * Directory-segment prefix match: {@code relativePath} is under the {@code prefix}
     * directory (or {@code prefix} is empty). "dimension" must NOT match "dimension_type/...".
     */
    static boolean matchesPrefix(String relativePath, String prefix) {
        return prefix == null || prefix.isEmpty()
                || relativePath.equals(prefix)
                || relativePath.startsWith(prefix + "/");
    }

    @Nullable
    static ResourceLocation safeResourceLocation(String namespace, String path) {
        try {
            return ResourceLocation.fromNamespaceAndPath(namespace, path);
        } catch (net.minecraft.ResourceLocationException e) {
            return null;
        }
    }

    private void collectFromNamespaceRoot(File namespaceRoot, String namespace, String prefix,
                                          BiConsumer<ResourceLocation, IoSupplier<InputStream>> output,
                                          IDatapackService datapackService, PackType type) {
        File prefixDir = prefix.isEmpty() ? namespaceRoot : new File(namespaceRoot, prefix);
        if (!prefixDir.isDirectory()) {
            return;
        }
        collectDirectory(prefixDir, namespaceRoot.toPath(), namespace, output, datapackService, type);
    }

    private static void collectDirectory(File dir, Path nsRoot, String namespace,
                                         BiConsumer<ResourceLocation, IoSupplier<InputStream>> output,
                                         IDatapackService datapackService, PackType type) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectDirectory(child, nsRoot, namespace, output, datapackService, type);
            } else if (child.isFile()) {
                String relToNs = nsRoot.relativize(child.toPath()).toString().replace('\\', '/');
                String virtualKey = type.getDirectory() + "/" + namespace + "/" + relToNs;
                // Virtual (RAM) content already registered by Lua wins over the file on disk.
                if (datapackService != null && datapackService.getVirtualFiles().containsKey(virtualKey)) {
                    continue;
                }
                ResourceLocation rl = safeResourceLocation(namespace, relToNs);
                if (rl != null) {
                    output.accept(rl, () -> Files.newInputStream(child.toPath()));
                }
            }
        }
    }

    private static void collectNamespaceDirs(File root, Set<String> namespaces) {
        if (!root.isDirectory()) {
            return;
        }
        File[] dirs = root.listFiles(File::isDirectory);
        if (dirs == null) {
            return;
        }
        for (File dir : dirs) {
            namespaces.add(dir.getName());
        }
    }

    /**
     * Mod folders to scan. A {@code luaDir} that is itself named "luamods" is scanned
     * directly; otherwise {@code luaDir/luamods} is used, falling back to {@code luaDir}
     * so a flat {@code lua/<packType>/...} layout keeps working.
     */
    private File[] modFolders() {
        File luamodsDir = LUAMODS_DIR_NAME.equalsIgnoreCase(luaDir.getName())
                ? luaDir
                : new File(luaDir, LUAMODS_DIR_NAME);
        if (!luamodsDir.exists()) {
            luamodsDir = luaDir;
        }
        if (!luamodsDir.isDirectory()) {
            return NO_FOLDERS;
        }
        File[] mods = luamodsDir.listFiles(File::isDirectory);
        return mods != null ? mods : NO_FOLDERS;
    }

    private static File subDir(File parent, String relative) {
        return new File(parent, relative);
    }
}
