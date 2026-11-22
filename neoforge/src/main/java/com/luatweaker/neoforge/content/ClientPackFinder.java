package com.luatweaker.neoforge.content;

import com.luatweaker.api.content.IDatapackService;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.content.LuaTweakerVirtualPackResources;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Optional;

/**
 * Mounts the in-memory client resource pack ({@code CLIENT_RESOURCES}) that
 * serves every virtual asset generated from Lua content (blockstates, models,
 * language files) without writing anything to disk.
 *
 * <p>Server data is covered loader-agnostically by the mixin-injected
 * {@code VirtualPackSource}; client resources have no Architectury
 * abstraction, so this listener stays NeoForge-specific (mirrors the old
 * {@code LuaAssetsPackFinder} client branch).
 */
public final class ClientPackFinder {

    private final File luaDir;
    private final IDatapackService datapackService;

    public ClientPackFinder(@NotNull File luaDir, @NotNull IDatapackService datapackService) {
        this.luaDir = luaDir;
        this.datapackService = datapackService;
    }

    public void register(@NotNull IEventBus modBus) {
        modBus.addListener(this::onAddPackFinders);
    }

    private void onAddPackFinders(@NotNull AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        PackLocationInfo locationInfo = new PackLocationInfo(
                "luatweaker_user_assets",
                Component.literal("LuaTweaker User Assets"),
                PackSource.BUILT_IN,
                Optional.empty());

        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public net.minecraft.server.packs.PackResources openPrimary(PackLocationInfo id) {
                return new LuaTweakerVirtualPackResources(id, PackType.CLIENT_RESOURCES, luaDir, datapackService);
            }

            @Override
            public net.minecraft.server.packs.PackResources openFull(PackLocationInfo id, Pack.Metadata meta) {
                return new LuaTweakerVirtualPackResources(id, PackType.CLIENT_RESOURCES, luaDir, datapackService);
            }
        };

        Pack pack = Pack.readMetaAndCreate(
                locationInfo,
                supplier,
                PackType.CLIENT_RESOURCES,
                new PackSelectionConfig(true, Pack.Position.TOP, false));

        if (pack != null) {
            event.addRepositorySource(consumer -> consumer.accept(pack));
            LuaTweakerLog.get().info(LogStage.SYSTEM,
                    "[LuaTweaker] Mounted Zero-Write-To-Disk Virtual ResourcePack for client assets (100% In-Memory RAM)");
        }
    }
}
