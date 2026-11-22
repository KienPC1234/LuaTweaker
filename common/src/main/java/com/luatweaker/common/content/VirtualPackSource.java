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

import java.io.File;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Universal repository source injecting LuaTweaker in-memory and disk packs.
 */
public final class VirtualPackSource implements RepositorySource {
    private static final VirtualPackSource INSTANCE = new VirtualPackSource();
    private static final String PACK_ID = "luatweaker_virtual_pack";

    private File luaDir = new File("lua");

    private VirtualPackSource() {
    }

    public static VirtualPackSource getInstance() {
        return INSTANCE;
    }

    /**
     * Points the physical-file fallback at the real {@code <gameDir>/luamods}
     * directory. Must be called once during common init; before that the
     * harmless {@code lua} default keeps unit-test construction working.
     */
    public static void setLuaDir(@NotNull File dir) {
        INSTANCE.luaDir = dir;
    }

    @Override
    public void loadPacks(@NotNull Consumer<Pack> onLoad) {
        IDatapackService service = LuaTweakerCommon.getDatapackService();
        PackLocationInfo location = new PackLocationInfo(
                PACK_ID,
                Component.literal("LuaTweaker Virtual Pack"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        PackSelectionConfig selectionConfig = new PackSelectionConfig(true, Pack.Position.TOP, true);
        Pack.ResourcesSupplier resources = new Pack.ResourcesSupplier() {
            @Override
            public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo info) {
                return new LuaTweakerVirtualPackResources(info, PackType.SERVER_DATA, luaDir, service);
            }

            @Override
            public net.minecraft.server.packs.PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                return openPrimary(info);
            }
        };

        Pack pack = Pack.readMetaAndCreate(location, resources, PackType.SERVER_DATA, selectionConfig);
        if (pack != null) {
            onLoad.accept(pack);
        }
    }
}
