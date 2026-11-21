package com.luatweaker.platform.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Finds a TrueType font with Vietnamese coverage for the ImGui atlas.
 *
 * <p>The ImGui default font (ProggyClean) has no Vietnamese glyphs, so any
 * panel with diacritics renders {@code ?} without this. No font is bundled
 * with the mod; the loader probes in order:</p>
 * <ol>
 *   <li>{@code config/luatweaker_font.ttf} — explicit user override.</li>
 *   <li>OS system fonts known to cover Vietnamese
 *       (Segoe UI / Arial on Windows, DejaVu Sans on Linux, Arial on macOS).</li>
 * </ol>
 * <p>Callers combine the bytes with
 * {@code ImFontAtlas.getGlyphRangesVietnamese()} (verified in imgui-java
 * 1.88.0), which covers Latin plus the Vietnamese extensions. Files over
 * 8&nbsp;MiB are skipped as a sanity cap.</p>
 */
public final class ImGuiFontLoader {

    /** Rendered pixel height of the UI font. Fixed; MC-side GUI scale applies on top. */
    public static final float FONT_SIZE_PX = 16.0f;

    private static final long MAX_FONT_BYTES = 8L * 1024 * 1024;
    private static final int TTF_MAGIC_LENGTH = 4;
    private static final int TTF_MAGIC = 0x0001_0000;
    private static final int TRUE_MAGIC = 0x7472_7565;
    private static final int TYP1_MAGIC = 0x7479_7031;
    private static final int OTTO_MAGIC = 0x4F54_544F;

    private ImGuiFontLoader() {}

    /** @return TTF bytes of the first usable candidate, or {@code null} when none is usable. */
    public static @org.jetbrains.annotations.Nullable byte[] loadFontBytes() {
        for (Path candidate : candidates()) {
            try {
                if (!Files.isRegularFile(candidate)) {
                    continue;
                }
                long size = Files.size(candidate);
                if (size <= 0 || size > MAX_FONT_BYTES) {
                    continue;
                }
                byte[] bytes = Files.readAllBytes(candidate);
                if (looksLikeTtf(bytes)) {
                    return bytes;
                }
            } catch (java.io.IOException | SecurityException e) {
                com.luatweaker.api.log.LuaTweakerLog.get().debug(
                        com.luatweaker.api.log.LogStage.SYSTEM,
                        "[ImGui] font candidate unreadable: " + candidate + " (" + e.getMessage() + ")");
            }
        }
        return null;
    }

    static List<Path> candidates() {
        List<Path> paths = new ArrayList<>();
        paths.add(Paths.get("config", "luatweaker_font.ttf"));
        String os = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT);
        String windir = System.getenv("WINDIR");
        if (os.contains("win")) {
            String base = windir != null ? windir : "C:\\Windows";
            paths.add(Paths.get(base, "Fonts", "segoeui.ttf"));
            paths.add(Paths.get(base, "Fonts", "arial.ttf"));
        } else if (os.contains("mac")) {
            paths.add(Paths.get("/System/Library/Fonts/Supplemental/Arial.ttf"));
            paths.add(Paths.get("/Library/Fonts/Arial.ttf"));
        } else {
            paths.add(Paths.get("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
            paths.add(Paths.get("/usr/share/fonts/TTF/DejaVuSans.ttf"));
        }
        return List.copyOf(paths);
    }

    /** TrueType magic ({@code 0x00 0x01 0x00 0x00}) or {@code 'true'}/{@code 'typ1'}/{@code 'OTTO'}. */
    static boolean looksLikeTtf(byte @org.jetbrains.annotations.Nullable [] bytes) {
        if (bytes == null || bytes.length < TTF_MAGIC_LENGTH) {
            return false;
        }
        int magic = readMagic(bytes);
        return magic == TTF_MAGIC || magic == TRUE_MAGIC || magic == TYP1_MAGIC || magic == OTTO_MAGIC;
    }

    /** Big-endian 32-bit tag of the first four bytes, the same value the sfnt table reader uses. */
    private static int readMagic(byte @org.jetbrains.annotations.NotNull [] bytes) {
        return ((bytes[0] & 0xFF) << 24) | ((bytes[1] & 0xFF) << 16)
                | ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
    }
}
