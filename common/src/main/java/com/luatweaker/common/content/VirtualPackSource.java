package com.luatweaker.common.content;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.common.LuaTweakerCommon;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Universal repository source injecting LuaTweaker in-memory and disk packs
 * for server data ({@code SERVER_DATA}) and client resources ({@code CLIENT_RESOURCES}).
 */
public final class VirtualPackSource implements RepositorySource {
    private static final VirtualPackSource SERVER_INSTANCE =
            new VirtualPackSource("luatweaker_virtual_pack", "LuaTweaker Virtual Pack", PackType.SERVER_DATA, true);
    private static final VirtualPackSource CLIENT_INSTANCE =
            new VirtualPackSource("luatweaker_user_assets", "LuaTweaker User Assets", PackType.CLIENT_RESOURCES, false);

    private static final AtomicReference<File> CONFIGURED_LUA_DIR = new AtomicReference<>();

    private final String packId;
    private final String title;
    private final PackType packType;
    private final boolean fixedPosition;

    private VirtualPackSource(String packId, String title, PackType packType, boolean fixedPosition) {
        this.packId = packId;
        this.title = title;
        this.packType = packType;
        this.fixedPosition = fixedPosition;
    }

    public static VirtualPackSource getInstance() {
        return SERVER_INSTANCE;
    }

    public static VirtualPackSource clientResources() {
        return CLIENT_INSTANCE;
    }

    public static void setLuaDir(@NotNull File dir) {
        CONFIGURED_LUA_DIR.set(dir);
    }

    private static File resolveLuaDir() {
        File explicit = CONFIGURED_LUA_DIR.get();
        if (explicit != null) {
            return explicit;
        }
        try {
            return dev.architectury.platform.Platform.getGameFolder().resolve("luamods").toFile();
        } catch (IllegalStateException | LinkageError | AssertionError e) {
            return new File("lua");
        }
    }

    @Override
    public void loadPacks(@NotNull Consumer<Pack> onLoad) {
        PackLocationInfo location = new PackLocationInfo(
                packId,
                Component.literal(title),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        PackSelectionConfig selectionConfig = new PackSelectionConfig(true, Pack.Position.TOP, fixedPosition);
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo info) {
                @Nullable IDatapackService service = LuaTweakerCommon.getDatapackService();
                return new LuaTweakerVirtualPackResources(info, packType, resolveLuaDir(), service);
            }

            @Override
            public net.minecraft.server.packs.PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return openPrimary(info);
            }
        };

        Pack pack = Pack.readMetaAndCreate(location, resources, packType, selectionConfig);
        if (pack != null) {
            onLoad.accept(pack);
        }
    }
}
