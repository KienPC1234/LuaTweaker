package com.luatweaker.common.content;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.NotNull;

/**
 * Static layout checks for {@code luamods/<mod>/data} and {@code assets} trees.
 *
 * <p>Minecraft only serves namespaced paths ({@code data/<namespace>/...},
 * {@code assets/<namespace>/...}) with singular data folders ({@code recipe},
 * {@code loot_table}, {@code advancement}, {@code function},
 * {@code structure}). A misplaced file loads silently as nothing, so the validator
 * reports the classic mistakes (missing namespace, plural folders, NBT outside
 * {@code structure/}) for {@code /lt doctor} instead of failing the load.
 */
public final class DatapackLayoutValidator {

    private static final int MAX_WALK_DEPTH = 8;
    private static final int MAX_WARNINGS = 200;
    private static final int SINGLE_PART = 1;
    private static final int TWO_PARTS = 2;

    private static final Map<String, String> DATA_TYPO_FIX = Map.of(
            "recipes", "recipe",
            "loot_tables", "loot_table",
            "advancements", "advancement",
            "functions", "function",
            "structures", "structure/<path>.nbt"
    );
    private static final Map<String, String> ASSET_TYPO_FIX = Map.of(
            "texture", "textures",
            "model", "models",
            "blockstate", "blockstates",
            "sound", "sounds"
    );
    private static final Set<String> DATA_ROOTS = Set.of(
            "recipe", "recipes", "loot_table", "loot_tables", "advancement", "advancements",
            "function", "functions", "structure", "structures", "tags", "worldgen",
            "dimension", "dimension_type");
    private static final Set<String> ASSET_ROOTS = Set.of(
            "blockstates", "models", "textures", "lang", "sounds", "particles", "font", "shaders");

    private DatapackLayoutValidator() {
    }

    /**
     * Scans every folder mod and {@code .zip} mod under {@code luamodsDir}.
     *
     * @return human-readable warnings, empty when every tree follows the layout
     */
    @NotNull
    public static List<String> validate(@NotNull File luamodsDir) {
        List<String> warnings = new ArrayList<>();
        if (luamodsDir == null || !luamodsDir.isDirectory()) {
            return warnings;
        }
        File[] mods = luamodsDir.listFiles();
        if (mods == null) {
            return warnings;
        }
        Arrays.sort(mods, Comparator.comparing(File::getName));
        for (File mod : mods) {
            if (warnings.size() >= MAX_WARNINGS) {
                break;
            }
            if (mod.isDirectory()) {
                checkFolderMod(mod, warnings);
            } else if (mod.isFile() && mod.getName().toLowerCase(Locale.ROOT).endsWith(".zip")) {
                checkZipMod(mod, warnings);
            }
        }
        return warnings;
    }

    private static void checkFolderMod(File mod, List<String> warnings) {
        checkTree(mod.getName(), new File(mod, "data"), true, warnings);
        checkTree(mod.getName(), new File(mod, "assets"), false, warnings);
    }

    private static void checkTree(String modName, File root, boolean dataTree, List<String> warnings) {
        if (!root.isDirectory()) {
            return;
        }
        Path rootPath = root.toPath();
        try (Stream<Path> paths = Files.walk(rootPath, MAX_WALK_DEPTH)) {
            List<Path> files = paths.filter(Files::isRegularFile).sorted().toList();
            for (Path file : files) {
                if (warnings.size() >= MAX_WARNINGS) {
                    return;
                }
                String rel = rootPath.relativize(file).toString().replace('\\', '/');
                String message = checkRelativePath(modName, root.getName(), rel, dataTree);
                if (message != null) {
                    warnings.add(message);
                }
            }
        } catch (IOException e) {
            warnings.add(modName + "/" + root.getName() + ": unreadable (" + e.getMessage() + ")");
        }
    }

    private static void checkZipMod(File zipFile, List<String> warnings) {
        try (ZipFile zip = new ZipFile(zipFile)) {
            List<? extends ZipEntry> entries = zip.stream().sorted(Comparator.comparing(ZipEntry::getName)).toList();
            for (ZipEntry entry : entries) {
                if (warnings.size() >= MAX_WARNINGS) {
                    return;
                }
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                boolean dataTree;
                String rel;
                if (name.startsWith("data/")) {
                    dataTree = true;
                    rel = name.substring("data/".length());
                } else if (name.startsWith("assets/")) {
                    dataTree = false;
                    rel = name.substring("assets/".length());
                } else {
                    continue;
                }
                String message = checkRelativePath(zipFile.getName(), dataTree ? "data" : "assets", rel, dataTree);
                if (message != null) {
                    warnings.add(message);
                }
            }
        } catch (IOException e) {
            warnings.add(zipFile.getName() + ": unreadable zip (" + e.getMessage() + ")");
        }
    }

    /**
     * Checks one path relative to the {@code data/} or {@code assets/} root.
     *
     * @return a warning, or null when the path follows the layout
     */
    static String checkRelativePath(String modName, String rootName, String rel, boolean dataTree) {
        if (rel == null || rel.isEmpty()) {
            return null;
        }
        String[] parts = rel.split("/");
        if (parts.length == SINGLE_PART) {
            return modName + "/" + rootName + "/" + rel
                    + ": missing <namespace>/ folder (expected " + rootName + "/<namespace>/...)";
        }
        String namespace = parts[0];
        boolean rootMatch = dataTree ? DATA_ROOTS.contains(namespace) : ASSET_ROOTS.contains(namespace);
        if (rootMatch) {
            return modName + "/" + rootName + "/" + rel
                    + ": missing <namespace>/ folder (expected " + rootName + "/<namespace>/...)";
        }
        if (!namespace.matches("[a-z0-9_.-]+")) {
            return modName + "/" + rootName + "/" + rel
                    + ": invalid namespace '" + namespace + "' (use lowercase letters, digits, _, ., -)";
        }
        String category = parts[1];
        return dataTree
                ? checkDataCategory(modName, rel, namespace, category)
                : checkAssetCategory(modName, rel, namespace, category, parts.length);
    }

    private static String checkDataCategory(String modName, String rel, String namespace, String category) {
        String fix = DATA_TYPO_FIX.get(category);
        if (fix != null) {
            return modName + "/data/" + rel + ": did you mean '" + namespace + "/" + fix + "/...'?";
        }
        if (rel.endsWith(".nbt") && !"structure".equals(category)) {
            return modName + "/data/" + rel
                    + ": .nbt templates must live under 'data/" + namespace + "/structure/<path>.nbt'";
        }
        return null;
    }

    private static String checkAssetCategory(String modName, String rel, String namespace, String category, int partsCount) {
        String fix = ASSET_TYPO_FIX.get(category);
        if (fix != null) {
            return modName + "/assets/" + rel + ": did you mean '" + namespace + "/" + fix + "/...'?";
        }
        if ("lang".equals(category) && partsCount == TWO_PARTS && !rel.endsWith(".json")) {
            return modName + "/assets/" + rel + ": language files must be .json";
        }
        return null;
    }
}
