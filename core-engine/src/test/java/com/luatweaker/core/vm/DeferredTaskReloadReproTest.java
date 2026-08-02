package com.luatweaker.core.vm;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.logger.AsyncFileLogger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reproduces the production ClassCastException "CobaltLuaEngine$1 cannot be cast to
 * LuaInterpretedFunction" that occurs when a deferred task (running via task._run_deferred)
 * calls print() and Java callbacks that re-enter the VM, after a reload.
 */
public class DeferredTaskReloadReproTest {

    @AfterAll
    public static void shutdownLogger() {
        AsyncFileLogger.get().shutdown();
    }

    private ILuaEngine createEngine() {
        ILuaEngine engine = new CobaltLuaEngine();
        ILuaTable globals = engine.getGlobalEnvironment();

        // Register a Java service whose method calls engine.callFunction of a Lua function
        // (mimics NetworkServiceImpl.GetOrCreateRemoteEvent -> RemoteEvent.new)
        ILuaTable fakeNetwork = engine.createTable();
        fakeNetwork.rawset("GetOrCreateRemoteEvent", args -> {
            ILuaValue remoteClass = globals.rawget("RemoteEvent");
            ILuaValue newFn = remoteClass.asTable().rawget("new");
            return engine.callFunction(newFn, remoteClass, engine.wrapString(args[1].asString()), engine.createTable());
        });
        globals.rawset("FakeNetwork", fakeNetwork);
        return engine;
    }

    private void runModule(ILuaEngine engine) {
        // Mimics magic_staff.lua: spawns a HUD-style loop (prints + Task.wait + Java callbacks)
        engine.executeString(
            "Task.spawn(function()\n" +
            "    print('[HUD] loop started')\n" +
            "    while true do\n" +
            "        Task.wait(0.001)\n" +
            "        local ok, err = pcall(function()\n" +
            "            FakeNetwork:GetOrCreateRemoteEvent('StaffManaSync')\n" +
            "            print('[HUD] tick')\n" +
            "        end)\n" +
            "        if not ok then print('[HUD] body error: ' .. tostring(err)) end\n" +
            "    end\n" +
            "end)\n" +
            "print('[Module] loaded')",
            "module_load"
        );
    }

    private void tick(ILuaEngine engine) {
        ILuaTable globals = engine.getGlobalEnvironment();
        ILuaValue tickFn = globals.rawget("task").asTable().rawget("_tick");
        engine.callFunction(tickFn);
    }

    @Test
    public void deferredLoopWithJavaReentrySurvivesAcrossEngines() throws Exception {
        for (int round = 1; round <= 3; round++) {
            ILuaEngine engine = createEngine();
            runModule(engine);
            tick(engine);
            tick(engine);
            Thread.sleep(10);
            tick(engine);
            System.out.println("Round " + round + " completed without ClassCastException");
        }
    }
}
