package com.luatweaker.command;

import com.luatweaker.api.command.ICommandService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.bind.LuaBinder;
import org.jetbrains.annotations.NotNull;

/**
 * Registers the {@code Commands} service into a Lua engine.
 *
 * <p>The impl instance is per-engine (it resolves the owning mod id from the
 * engine's {@code mod} global), but all instances share the static command map
 * in {@link CommandServiceImpl} so the platform command tree can always reach
 * the definitions of the current load cycle.</p>
 */
public class CommandLuaBinding {

    private CommandLuaBinding() {}

    public static void registerBindings(@NotNull ILuaEngine engine) {
        ICommandService service = new CommandServiceImpl(engine);
        LuaBinder.bind(engine, "Commands", service, ICommandService.class, "commands");

        // Java-side consumers (LuaTweakerCommandRegistry, HelpCommand) look up
        // the service impl, not the Lua table, exactly like NetworkServiceImpl.
        engine.registerService("CommandServiceImpl", service);
    }
}
