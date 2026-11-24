package com.luatweaker.common.mixin;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.common.recipe.CommonInterceptionApplier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {

    @Shadow
    int repairItemCountCost;

    protected AnvilMenuMixin(@Nullable MenuType<?> type, int containerId,
                             Inventory playerInventory, ContainerLevelAccess access) {
        super(type, containerId, playerInventory, access);
    }

    @Inject(method = "createResult", at = @At("TAIL"))
    void applyLuaAnvilRecipes(CallbackInfo ci) {
        Objects.requireNonNull(ci);
        if (!isFabricSafe()) {
            return;
        }
        CommonInterceptionApplier.matchAnvil(this.inputSlots.getItem(0), this.inputSlots.getItem(1))
                .ifPresent(match -> {
                    AnvilMenu self = (AnvilMenu) (Object) this;
                    this.resultSlots.setItem(0, match.output());
                    self.cost.set(match.expCost());
                    this.repairItemCountCost = match.materialCost();
                    LuaTweakerLog.get().info(LogStage.RECIPE_APPLY,
                            "[Anvil] Applied recipe '" + match.recipeId() + "' -> "
                                    + match.output().getCount() + "x " + match.output().getItem());
                });
    }

    private static boolean isFabricSafe() {
        try {
            return dev.architectury.platform.Platform.isFabric();
        } catch (IllegalStateException | LinkageError | AssertionError e) {
            return false;
        }
    }
}
