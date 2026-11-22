package com.luatweaker.common.mixin;

import com.luatweaker.common.content.VirtualPackSource;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@Mixin(PackRepository.class)
public class PackRepositoryMixin {
    @Shadow
    @Final
    @Mutable
    private Set<RepositorySource> sources;

    @Inject(method = "<init>", at = @At("RETURN"))
    void injectVirtualPackSource(CallbackInfo ci) {
        Objects.requireNonNull(ci);
        Set<RepositorySource> mutableSources = new LinkedHashSet<>(this.sources);
        mutableSources.add(VirtualPackSource.getInstance());
        this.sources = mutableSources;
    }
}
