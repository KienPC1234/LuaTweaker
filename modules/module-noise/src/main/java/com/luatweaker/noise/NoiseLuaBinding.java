package com.luatweaker.noise;

import com.luatweaker.api.noise.INoiseService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

public class NoiseLuaBinding {

    static {
        // double[] returns (e.g. domainWarp) become 1-indexed Lua tables.
        LuaBinder.registerReturnConverter(double[].class, (engine, value) -> {
            double[] array = (double[]) value;
            ILuaTable table = engine.createTable();
            for (int i = 0; i < array.length; i++) {
                table.rawset(i + 1, engine.wrapNumber(array[i]));
            }
            return table;
        });
    }

    public static void registerBindings(@NotNull ILuaEngine engine) {
        NoiseServiceImpl service = new NoiseServiceImpl();
        LuaBinder.bind(engine, "Noise", service, INoiseService.class);
        engine.registerService("NoiseServiceImpl", service);
    }
}
