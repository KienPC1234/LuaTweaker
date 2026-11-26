package com.luatweaker.common.platform;

import com.luatweaker.api.platform.IArchitecturyService;
import dev.architectury.platform.Platform;
import dev.architectury.registry.fuel.FuelRegistry;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Architectury-backed implementation of {@link IArchitecturyService}.
 */
public final class ArchitecturyServiceImpl implements IArchitecturyService {

    @Override
    @NotNull
    public String getLoader() {
        if (Platform.isNeoForge()) {
            return "neoforge";
        }
        if (Platform.isFabric()) {
            return "fabric";
        }
        return "architectury";
    }

    @Override
    @NotNull
    public String getMinecraftVersion() {
        return Platform.getMinecraftVersion();
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return Platform.isDevelopmentEnvironment();
    }

    @Override
    public boolean isModLoaded(@NotNull String modId) {
        return modId != null && !modId.isBlank() && Platform.isModLoaded(modId.trim());
    }

    @Override
    @NotNull
    public List<String> getLoadedMods() {
        return List.copyOf(Platform.getModIds());
    }

    @Override
    @Nullable
    public Map<String, Object> getModInfo(@NotNull String modId) {
        return ArchitecturyPlatformOps.readModInfo(modId);
    }

    @Override
    public boolean registerFuel(@NotNull String itemId, int burnTicks) {
        Item item = ArchitecturyPlatformOps.resolveItem(itemId);
        if (item == null || burnTicks <= 0) {
            return false;
        }
        FuelRegistry.register(burnTicks, item);
        return true;
    }

    @Override
    public int getBurnTime(@NotNull String itemId) {
        Item item = ArchitecturyPlatformOps.resolveItem(itemId);
        return item == null ? 0 : FuelRegistry.get(new ItemStack(item));
    }

    @Override
    public boolean addToolTransform(@NotNull String toolKind, @NotNull String inputBlockId,
                                    @NotNull String resultBlockId) {
        return ArchitecturyRegistryOps.addToolTransform(toolKind, inputBlockId, resultBlockId);
    }

    @Override
    public boolean addWanderingTrade(@NotNull String buyItemId, @NotNull String sellItemId,
                                     @Nullable Map<String, Object> options) {
        return ArchitecturyRegistryOps.addWanderingTrade(buyItemId, sellItemId, options);
    }
}
