package com.luatweaker.neoforge.recipe;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.recipe.CommonInterceptionApplier;
import com.luatweaker.common.recipe.CommonRecipeManager;
import com.luatweaker.common.recipe.InterceptionHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

public final class NeoForgeRecipeHandler {

    private NeoForgeRecipeHandler() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(NeoForgeRecipeHandler::onRegisterBrewing);
        NeoForge.EVENT_BUS.addListener(NeoForgeRecipeHandler::onVillagerTrades);
        NeoForge.EVENT_BUS.addListener(NeoForgeRecipeHandler::onAnvilUpdate);
    }

    public static void onVillagerTrades(VillagerTradesEvent event) {
        for (CommonRecipeManager.TradeData data : InterceptionHelper.getPendingTrades()) {
            ResourceLocation profLoc = ResourceLocation.parse(data.profession());
            VillagerProfession profession = BuiltInRegistries.VILLAGER_PROFESSION.get(profLoc);
            if (profession == null || !event.getType().equals(profession)) {
                continue;
            }
            var levelMap = event.getTrades();
            if (levelMap.containsKey(data.level())) {
                levelMap.get(data.level()).add((trader, random) -> CommonInterceptionApplier.createOffer(data));
            }
        }
    }

    public static void onRegisterBrewing(RegisterBrewingRecipesEvent event) {
        CommonInterceptionApplier.applyPendingBrewing(event.getBuilder());
    }

    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        CommonInterceptionApplier.matchAnvil(event.getLeft(), event.getRight()).ifPresent(match -> {
            event.setOutput(match.output());
            event.setCost(match.expCost());
            event.setMaterialCost(match.materialCost());
            LuaTweakerLog.get().info(LogStage.RECIPE_APPLY,
                    "[Anvil] Applied recipe '" + match.recipeId() + "' -> "
                            + match.output().getCount() + "x " + match.output().getItem());
        });
    }
}
