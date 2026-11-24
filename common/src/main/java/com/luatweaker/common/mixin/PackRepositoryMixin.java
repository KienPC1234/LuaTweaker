package com.luatweaker.common.mixin;

import com.luatweaker.common.content.VirtualPackSource;
import dev.architectury.hooks.PackRepositoryHooks;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.repository.ServerPacksSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;
import java.util.Set;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Shadow
    @Final
    Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    void injectVirtualPackSource(CallbackInfo ci) {
        Objects.requireNonNull(ci);
        PackRepository self = (PackRepository) (Object) this;
        boolean isServerRepo = this.sources.stream().anyMatch(source -> source instanceof ServerPacksSource);
        if (isServerRepo) {
            PackRepositoryHooks.addSource(self, VirtualPackSource.getInstance());
        } else if (isFabricSafe()) {
            PackRepositoryHooks.addSource(self, VirtualPackSource.clientResources());
        }
    }

    private static boolean isFabricSafe() {
        try {
            return dev.architectury.platform.Platform.isFabric();
        } catch (IllegalStateException | LinkageError | AssertionError e) {
            return false;
        }
    }
}
