package com.luatweaker.structures;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Optional;

/**
 * Locates NBT template files under plain data roots using only {@link File}.
 *
 * <p>Mirrors the lookup order the virtual pack serves at runtime (verified:
 * {@code LuaTweakerVirtualPackResources.resolvePhysicalFile}): each data root is
 * searched for {@code <root>/<namespace>/structure/<path>.nbt}. Data roots are
 * the per-mod {@code data/} directories ({@code luamods/<mod_id>/data}) plus the
 * shared {@code lua/data} directory, supplied by the platform caller.
 *
 * <p>No Minecraft/NeoForge symbols are used: existence and non-emptiness are the
 * only checks performed here. Deep NBT validity is decided by vanilla when the
 * datapack loads, which fails loudly in the game log on malformed templates.
 */
public final class NbtStructureLoader {

    private NbtStructureLoader() {}

    /**
     * Finds {@code <root>/<namespace>/structure/<path>.nbt} under the first data
     * root that contains it. Accepts {@code "ns:path"} with or without a trailing
     * {@code ".nbt"} (the roadmap example uses {@code "mymod:structures/temple.nbt"}).
     *
     * @return the existing non-empty file, or empty when no root provides it
     * @throws IllegalArgumentException on blank ids, missing namespaces, or path traversal
     */
    @NotNull
    public static Optional<File> findTemplateFile(@NotNull List<File> dataRoots, @NotNull String templateId) {
        String[] parts = splitTemplateId(templateId);
        String relative = parts[0] + File.separator + "structure" + File.separator
                + parts[1].replace("/", File.separator) + ".nbt";
        for (File root : dataRoots) {
            Optional<File> found = findUnderRoot(root, relative);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    private static Optional<File> findUnderRoot(File root, @NotNull String relative) {
        if (root == null) {
            return Optional.empty();
        }
        File candidate = new File(root, relative);
        if (candidate.isFile() && candidate.length() > 0) {
            return Optional.of(candidate);
        }
        return Optional.empty();
    }

    /**
     * Normalizes a template reference to the {@code "namespace:path"} location form
     * used by template-pool elements (verified: vanilla pool {@code location} fields
     * carry no {@code .nbt} suffix; the suffix lives on the file under
     * {@code data/<ns>/structure/}).
     */
    @NotNull
    public static String templateLocation(@NotNull String templateId) {
        String[] parts = splitTemplateId(templateId);
        return parts[0] + ":" + parts[1];
    }

    /** Splits and validates {@code "namespace:path"} (optional trailing {@code .nbt}). */
    @NotNull
    static String[] splitTemplateId(@NotNull String templateId) {
        String normalized = stripNbtSuffix(requireNonBlank(templateId));
        int colon = normalized.indexOf(':');
        if (colon <= 0 || colon == normalized.length() - 1) {
            throw new IllegalArgumentException("template must be 'namespace:path' (e.g. 'mymod:structures/temple'),"
                    + " got: " + templateId);
        }
        String namespace = normalized.substring(0, colon);
        String path = normalized.substring(colon + 1);
        rejectTraversal(templateId, namespace, path);
        return new String[]{namespace, path};
    }

    @NotNull
    private static String requireNonBlank(@NotNull String templateId) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalArgumentException("template must not be blank (e.g. 'mymod:structures/temple')");
        }
        return templateId;
    }

    @NotNull
    private static String stripNbtSuffix(@NotNull String templateId) {
        String normalized = templateId.strip();
        if (normalized.endsWith(".nbt")) {
            return normalized.substring(0, normalized.length() - ".nbt".length());
        }
        return normalized;
    }

    private static void rejectTraversal(@NotNull String templateId, @NotNull String namespace,
                                        @NotNull String path) {
        if (path.contains("..") || path.contains("\\") || path.startsWith("/") || namespace.contains("/")) {
            throw new IllegalArgumentException("template must not contain path traversal, got: " + templateId);
        }
    }
}
