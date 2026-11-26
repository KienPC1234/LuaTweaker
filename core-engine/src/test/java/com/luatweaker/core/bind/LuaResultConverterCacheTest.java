package com.luatweaker.core.bind;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import com.luatweaker.core.vm.LuavaEngine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies BFS converter cache invalidation in {@link LuaResultConverter}. */
class LuaResultConverterCacheTest {

    interface BaseMarker {
        String label();

        String category();
    }

    interface SubMarker extends BaseMarker {
        int priority();
    }

    @AfterAll
    static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    @Test
    void converterCacheInvalidatesWhenMoreSpecificConverterIsRegistered() {
        ILuaEngine engine = new LuavaEngine(false);
        try {
            SubMarker impl = new SubMarker() {
                @Override
                public String label() {
                    return "item";
                }

                @Override
                public String category() {
                    return "marker";
                }

                @Override
                public int priority() {
                    return 1;
                }
            };
            LuaResultConverter.registerReturnConverter(BaseMarker.class, (eng, v) -> {
                ILuaTable table = eng.createTable();
                table.rawset("kind", "base");
                return table;
            });
            LuaResultConverter.toLua(engine, SubMarker.class, impl);

            LuaResultConverter.registerReturnConverter(SubMarker.class, (eng, v) -> {
                ILuaTable table = eng.createTable();
                table.rawset("kind", "sub");
                return table;
            });
            ILuaValue converted = LuaResultConverter.toLua(engine, SubMarker.class, impl);
            assertEquals("sub", converted.asTable().rawget("kind").asString(),
                    "registering a more specific converter must invalidate cached BFS resolutions");
        } finally {
            engine.shutdown();
        }
    }
}
