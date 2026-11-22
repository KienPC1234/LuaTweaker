package com.luatweaker.common.content;

import com.luatweaker.api.content.IContentService;
import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.common.util.CallbackFirewall;
import org.jetbrains.annotations.NotNull;

import java.io.File;

/**
 * Orchestrates content-driven virtual-file generation (block tags, item,
 * block, fluid and language assets) into the shared datapack map.
 *
 * <p>The data pack itself is mounted by the loader (mixin-injected
 * {@link VirtualPackSource} for {@code SERVER_DATA}); the client resource pack
 * is mounted per-loader. This class only produces file content, so it stays
 * loader-agnostic. Loader-specific assets (e.g. the NeoForge
 * {@code fluid_container} bucket loader) live in the platform modules.
 */
public final class ContentVirtualFiles {

    private ContentVirtualFiles() {
    }

    /**
     * Regenerates every content-driven virtual file from the registered builders.
     * Safe to call once at startup after content registration; per-domain
     * failures are logged and skipped so one bad domain never kills the rest.
     */
    public static void rebuild(@NotNull File luaDir, @NotNull IContentService contentService,
                               @NotNull IDatapackService datapackService) {
        CallbackFirewall.run(() -> ContentTagFiles.rebuild(contentService, datapackService), LogStage.SYSTEM,
                () -> "[ContentVirtualFiles] Failed block tag generation");
        CallbackFirewall.run(() -> ContentItemAssets.rebuild(luaDir, contentService, datapackService),
                LogStage.SYSTEM,
                () -> "[ContentVirtualFiles] Failed item asset generation");
        CallbackFirewall.run(() -> ContentBlockAssets.rebuild(luaDir, contentService, datapackService),
                LogStage.SYSTEM,
                () -> "[ContentVirtualFiles] Failed block asset generation");
        CallbackFirewall.run(() -> ContentFluidAssets.rebuild(contentService, datapackService), LogStage.SYSTEM,
                () -> "[ContentVirtualFiles] Failed fluid asset generation");
        CallbackFirewall.run(
                () -> new LuaAssetsLangGenerator(contentService, datapackService, ContentAssetPaths.GSON)
                        .injectLanguageFiles(),
                LogStage.SYSTEM,
                () -> "[ContentVirtualFiles] Failed language file generation");
    }
}
