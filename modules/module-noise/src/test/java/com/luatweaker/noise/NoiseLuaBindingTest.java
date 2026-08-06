package com.luatweaker.noise;

import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.CobaltLuaEngine;
import com.luatweaker.api.vm.ILuaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the Noise service works end-to-end through the real Lua VM:
 * global registration, PascalCase aliases, defaults and table returns.
 */
public class NoiseLuaBindingTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void binding_ExposesNoiseGlobalsAndFunctions() {
        ILuaEngine engine = new CobaltLuaEngine();
        NoiseLuaBinding.registerBindings(engine);

        engine.executeString(
            "local v = Noise:simplex(1, 2)\n" +
            "assert(type(v) == 'number' and v >= -1 and v <= 1, 'simplex failed: ' .. tostring(v))\n" +
            "assert(type(Noise.FBm) == 'function' or type(Noise.fBm) == 'function', 'fBm alias missing')\n" +
            "local f = Noise:fBm(1, 2)\n" +
            "assert(type(f) == 'number', 'fBm defaults failed')\n" +
            "local r = Noise:ridged(1, 2)\n" +
            "assert(type(r) == 'number' and r >= 0, 'ridged failed: ' .. tostring(r))\n" +
            "local w = Noise:domainWarp(1, 2)\n" +
            "assert(type(w) == 'table' and w[1] ~= nil and w[2] ~= nil, 'domainWarp must return a 2-element table')\n" +
            "local vn = Noise:voronoi(1, 2)\n" +
            "assert(type(vn) == 'number' and vn >= 0, 'voronoi failed: ' .. tostring(vn))\n" +
            "Noise:SetSeed(999)\n" +
            "local a = Noise:simplex(1, 2)\n" +
            "Noise:SetSeed(999)\n" +
            "local b = Noise:simplex(1, 2)\n" +
            "assert(a == b, 'seed must reproduce output through Lua')\n",
            "noise_binding_test"
        );

        Object registered = com.luatweaker.core.service.LuaServiceRegistry.get("NoiseServiceImpl");
        assertTrue(registered instanceof NoiseServiceImpl, "NoiseServiceImpl must be in the service registry");
    }

    @Test
    void binding_MissingRequiredArgumentsRaiseLuaError() {
        ILuaEngine engine = new CobaltLuaEngine();
        NoiseLuaBinding.registerBindings(engine);

        engine.executeString(
            "local ok, err = pcall(function() Noise:voronoi(1) end)\n" +
            "assert(not ok, 'missing required voronoi argument must raise a Lua error')\n",
            "noise_binding_error_test"
        );
    }

    @Test
    void binding_requireNoiseResolvesToGlobal() {
        ILuaEngine engine = new CobaltLuaEngine();
        NoiseLuaBinding.registerBindings(engine);

        engine.executeString(
            "local Noise = require('LuaTweaker.Noise')\n" +
            "assert(type(Noise) == 'table' and Noise.simplex ~= nil, 'require LuaTweaker.Noise failed')\n",
            "noise_require_test"
        );
    }
}
