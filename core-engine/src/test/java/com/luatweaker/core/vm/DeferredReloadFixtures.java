package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Shared reload-repro fixture: a HUD-style coroutine that prints and re-enters
 * Java through a service callback.
 */
final class DeferredReloadFixtures {

    private DeferredReloadFixtures() {
    }

    static ILuaEngine createEngine() {
        ILuaEngine engine = LuaEngineFactory.create(false);
        ILuaTable globals = engine.getGlobalEnvironment();

        // A Java service whose method calls back into the VM (mimics
        // NetworkServiceImpl.GetOrCreateRemoteEvent -> RemoteEvent.new).
        ILuaTable fakeNetwork = engine.createTable();
        fakeNetwork.rawset("GetOrCreateRemoteEvent", args -> {
            ILuaValue remoteClass = globals.rawget("RemoteEvent");
            ILuaValue newFn = remoteClass.asTable().rawget("new");
            return engine.callFunction(newFn, remoteClass, engine.wrapString(args[1].asString()), engine.createTable());
        });
        globals.rawset("FakeNetwork", fakeNetwork);
        return engine;
    }

    static void runModule(ILuaEngine engine) {
        // Mimics magic_staff.lua: a HUD-style loop that waits, re-enters Java and prints.
        engine.executeString(
                "_hudTicks = 0\n"
                        + "Task.spawn(function()\n"
                        + "    print('[HUD] loop started')\n"
                        + "    while true do\n"
                        + "        Task.wait(0.001)\n"
                        + "        local ok, err = pcall(function()\n"
                        + "            FakeNetwork:GetOrCreateRemoteEvent('StaffManaSync')\n"
                        + "            _hudTicks = _hudTicks + 1\n"
                        + "            print('[HUD] tick')\n"
                        + "        end)\n"
                        + "        if not ok then _hudError = tostring(err) end\n"
                        + "    end\n"
                        + "end)\n"
                        + "print('[Module] loaded')",
                "module_load");
        assertNull(engine.getAndClearLastExecutionError(), "module entrypoint must load cleanly");
    }

    static void tick(ILuaEngine engine) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue tickFn = globals.rawget("task").asTable().rawget("_tick");
        assertDoesNotThrow(() -> engine.callFunction(tickFn), "task._tick must not leak a VM exception");
    }

    /** One full reload round: fresh engine, module load, ticks, and a short settle wait. */
    static ILuaEngine runReloadRound() {
        ILuaEngine engine = createEngine();
        runModule(engine);
        tick(engine);
        tick(engine);
        AwaitSupport.awaitMillis(10);
        tick(engine);
        return engine;
    }

    record ReloadPair(ILuaEngine oldEngine, ILuaEngine newEngine) {
    }

    /** Old engine runs, then a reload constructs a brand-new VM running the same module. */
    static ReloadPair prepareReloadPair() {
        ILuaEngine oldEngine = createEngine();
        runModule(oldEngine);
        tick(oldEngine);
        AwaitSupport.awaitMillis(10);
        tick(oldEngine);
        // Deferred tasks run on luava coroutine virtual threads, so the counter can
        // be published just after the tick returns. Wait briefly before asserting.
        AwaitSupport.awaitMillis(100);

        ILuaEngine newEngine = createEngine();
        runModule(newEngine);
        return new ReloadPair(oldEngine, newEngine);
    }
}
