package com.luatweaker.common.platform;

import dev.architectury.platform.Mod;
import dev.architectury.platform.Platform;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mod metadata and registry resolution helpers backing {@link ArchitecturyServiceImpl}.
 */
final class ArchitecturyPlatformOps {

    private ArchitecturyPlatformOps() {
    }

    @Nullable
    static Map<String, Object> readModInfo(@Nullable String modId) {
        if (modId == null || modId.isBlank()) {
            return null;
        }
        Optional<Mod> modOpt = Platform.getOptionalMod(modId.trim());
        if (modOpt.isEmpty()) {
            return null;
        }
        Mod mod = modOpt.get();
        var info = new LinkedHashMap<String, Object>();
        info.put("Id", mod.getModId());
        info.put("Name", mod.getName());
        info.put("Version", mod.getVersion());
        info.put("Description", mod.getDescription());
        info.put("Authors", List.copyOf(mod.getAuthors()));
        info.put("License", mod.getLicense() != null ? List.copyOf(mod.getLicense()) : List.of());
        mod.getHomepage().ifPresent(url -> info.put("Homepage", url));
        mod.getSources().ifPresent(url -> info.put("Sources", url));
        mod.getIssueTracker().ifPresent(url -> info.put("IssueTracker", url));
        return info;
    }

    @Nullable
    static Item resolveItem(@Nullable String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(itemId.trim());
        if (rl == null || !BuiltInRegistries.ITEM.containsKey(rl)) {
            return null;
        }
        Item item = BuiltInRegistries.ITEM.get(rl);
        return item == Items.AIR ? null : item;
    }

    @Nullable
    static Block resolveBlock(@Nullable String blockId) {
        if (blockId == null || blockId.isBlank()) {
            return null;
        }
        ResourceLocation rl = ResourceLocation.tryParse(blockId.trim());
        if (rl == null || !BuiltInRegistries.BLOCK.containsKey(rl)) {
            return null;
        }
        Block block = BuiltInRegistries.BLOCK.get(rl);
        return block == Blocks.AIR ? null : block;
    }
}
