package com.luatweaker.common.mixin;

import com.luatweaker.common.entity.EntityDataHolder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(Entity.class)
public abstract class EntityDataMixin implements EntityDataHolder {
    @Unique
    private CompoundTag customData;

    @Override
    public CompoundTag getPersistentData() {
        if (this.customData == null) {
            this.customData = new CompoundTag();
        }
        return this.customData;
    }

    @Inject(method = "saveWithoutId", at = @At("RETURN"))
    private void injectCustomSave(CompoundTag compound, CallbackInfoReturnable<CompoundTag> cir) {
        Objects.requireNonNull(cir);
        if (this.customData != null && !this.customData.isEmpty()) {
            compound.put("LuaTweakerData", this.customData.copy());
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void injectCustomLoad(CompoundTag compound, CallbackInfo ci) {
        Objects.requireNonNull(ci);
        if (compound != null && compound.contains("LuaTweakerData", Tag.TAG_COMPOUND)) {
            this.customData = compound.getCompound("LuaTweakerData").copy();
        }
    }
}
