package com.luatweaker.interaction;

import com.luatweaker.api.particle.IParticleService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

/**
 * Binds the particle service as the {@code Particle}/{@code Particles} Lua surface.
 * {@link LuaBinder} turns the interface into both camelCase and PascalCase entries, so
 * {@code Particle:Line(...)} and {@code Particle:line(...)} are the same call.
 */
public final class ParticleLuaBinding {

    private ParticleLuaBinding() {
    }

    public static void registerBindings(@NotNull ILuaEngine engine,
                                        @NotNull IParticleService service) {
        LuaBinder.bind(engine, "Particle", service, IParticleService.class, "Particles");
    }
}
