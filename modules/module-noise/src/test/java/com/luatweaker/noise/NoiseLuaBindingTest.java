package com.luatweaker.noise;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Verifies the Noise service works end-to-end through the real Lua VM:
 * global registration, PascalCase aliases, defaults and table returns.
 */
class NoiseLuaBindingTest {

    private static final String NOISE_FUNCTIONS_SCRIPT =
            "local v = Noise:simplex(1, 2)\n"
            + "assert(type(v) == 'number' and v >= -1 and v <= 1, 'simplex failed: ' .. tostring(v))\n"
            + "assert(type(Noise.FBm) == 'function' or type(Noise.fBm) == 'function', 'fBm alias missing')\n"
            + "local f = Noise:fBm(1, 2)\n"
            + "assert(type(f) == 'number', 'fBm defaults failed')\n"
            + "local r = Noise:ridged(1, 2)\n"
            + "assert(type(r) == 'number' and r >= 0, 'ridged failed: ' .. tostring(r))\n"
            + "local w = Noise:domainWarp(1, 2)\n"
            + "assert(type(w) == 'table' and w[1] ~= nil and w[2] ~= nil, "
            + "'domainWarp must return a 2-element table')\n"
            + "local vn = Noise:voronoi(1, 2)\n"
            + "assert(type(vn) == 'number' and vn >= 0, 'voronoi failed: ' .. tostring(vn))\n"
            + "Noise:SetSeed(999)\n"
            + "local a = Noise:simplex(1, 2)\n"
            + "Noise:SetSeed(999)\n"
            + "local b = Noise:simplex(1, 2)\n"
            + "assert(a == b, 'seed must reproduce output through Lua')\n";

    private static final String BATCH_SCRIPT =
            "local spec = { kind = 'fbm', octaves = 4 }\n"
            + "local field = Noise:Fill2D(10, 20, 4, 3, spec)\n"
            + "assert(#field == 12, 'expected 12 samples, got ' .. #field)\n"
            + "assert(field[1] == Noise:fBm(10, 20, 4, 2.0, 0.5), 'index 1 mismatch')\n"
            + "assert(field[2] == Noise:fBm(11, 20, 4, 2.0, 0.5), 'x must be the fastest axis')\n"
            + "assert(field[5] == Noise:fBm(10, 21, 4, 2.0, 0.5), 'row stride mismatch')\n"
            + "local volume = Noise:Fill3D(0, 0, 0, 2, 2, 2, { kind = 'fbm3', octaves = 3 })\n"
            + "assert(#volume == 8, 'expected 8 3D samples, got ' .. #volume)\n"
            + "assert(volume[1] == Noise:fbm3(0, 0, 0, 3, 2.0, 0.5), '3D index 1 mismatch')\n"
            + "assert(volume[5] == Noise:fbm3(0, 1, 0, 3, 2.0, 0.5), '3D y stride mismatch')\n"
            + "assert(not pcall(function() Noise:Fill2D(0, 0, 0, 1, {}) end), 'zero size must fail')\n"
            + "assert(not pcall(function() Noise:Fill2D(0, 0, 1, 1, { kind = 'nope' }) end), "
            + "'unknown kind must fail')\n"
            + "assert(not pcall(function() Noise:Fill2D(0, 0, 4096, 4096, {}) end), "
            + "'oversized request must fail')\n";

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void exposesNoiseGlobalsAndFunctions() {
        ILuaEngine engine = newEngine();

        engine.executeString(NOISE_FUNCTIONS_SCRIPT, "noise_binding_test");

        Assertions.assertInstanceOf(NoiseServiceImpl.class, LuaServiceRegistry.get("NoiseServiceImpl"),
                "NoiseServiceImpl must be in the service registry");
    }

    @Test
    void missingRequiredArgumentsRaiseALuaError() {
        ILuaEngine engine = newEngine();

        engine.executeString(
            "local ok, err = pcall(function() Noise:voronoi(1) end)\n"
            + "assert(not ok, 'missing required voronoi argument must raise a Lua error')\n",
            "noise_binding_error_test");

        Assertions.assertNull(engine.getAndClearLastExecutionError(),
                "the Lua pcall check for a missing voronoi argument must pass");
    }

    @Test
    void requireNoiseResolvesToTheGlobalTable() {
        ILuaEngine engine = newEngine();

        engine.executeString(
            "local Noise = require('LuaTweaker.Noise')\n"
            + "assert(type(Noise) == 'table' and Noise.simplex ~= nil, 'require LuaTweaker.Noise failed')\n",
            "noise_require_test");

        Assertions.assertNull(engine.getAndClearLastExecutionError(),
                "require('LuaTweaker.Noise') must resolve to the bound noise table");
    }

    @Test
    void batchFillSamplesAWholeGridInOneCall() {
        ILuaEngine engine = newEngine();

        engine.executeString(BATCH_SCRIPT, "noise_batch_test");

        Assertions.assertNull(engine.getAndClearLastExecutionError(),
                "Fill2D/Fill3D must produce the expected grids and reject invalid specs");
    }

    private static ILuaEngine newEngine() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        NoiseLuaBinding.registerBindings(engine);
        return engine;
    }
}
