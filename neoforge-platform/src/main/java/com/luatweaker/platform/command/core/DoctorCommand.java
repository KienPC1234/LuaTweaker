package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.command.CommandDefinition;
import com.luatweaker.command.CommandServiceImpl;
import com.luatweaker.core.mod.LuaMod;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.LuaTweakerMod;
import com.luatweaker.tasks.TaskServiceImpl;
import com.luatweaker.update.UpdateServiceImpl;
import com.luatweaker.update.UpdateStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

import java.util.Collection;
import java.util.Map;

/**
 * /luatweaker doctor
 *
 * Health diagnostics for the Lua runtime: loaded mods, per-mod load errors,
 * active engine status and the service registry state.
 */
public class DoctorCommand implements ILuaTweakerCommand {

    private static final String SECTION_HEADER = "§6========== LuaTweaker Doctor ==========";

    @Override
    public String getName() { return "doctor"; }

    @Override
    public String getDescription() { return "Health diagnostics: mods, engine, services, load errors"; }

    @Override
    public int getPermissionLevel() { return 2; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        sender.sendMessage(SECTION_HEADER);
        reportEngine(sender);
        reportMods(sender);
        reportUpdates(sender);
        reportServices(sender);
        reportTaskQueue(sender);
        reportLuaCommands(sender);
        return 1;
    }

    private void reportEngine(@NotNull ICommandSender sender) {
        boolean engineOk = LuaTweakerMod.getActiveEngine() != null;
        sender.sendMessage("§7Engine: §" + (engineOk ? "aACTIVE" : "cMISSING"));
    }

    private void reportMods(@NotNull ICommandSender sender) {
        Map<String, LuaMod> mods = LuaModManager.getLoadedMods();
        sender.sendMessage("§7Loaded LuaMods: §e" + mods.size());

        Map<String, String> loadErrors = LuaModManager.getLoadErrors();
        if (loadErrors.isEmpty()) {
            sender.sendMessage("§a  All mods loaded cleanly.");
            return;
        }
        sender.sendMessage("§c  Load errors (" + loadErrors.size() + "):");
        for (Map.Entry<String, String> entry : loadErrors.entrySet()) {
            sender.sendMessage("§c    - " + entry.getKey() + ": " + entry.getValue());
        }
    }

    private void reportUpdates(@NotNull ICommandSender sender) {
        Map<String, UpdateStatus> updateStatuses = UpdateServiceImpl.getUpdateStatuses();
        if (updateStatuses.isEmpty()) {
            return;
        }
        sender.sendMessage("§7Update checks: §e" + updateStatuses.size()
                + " declared, §a" + UpdateServiceImpl.getUpdates().size() + " available");
        for (UpdateStatus status : UpdateServiceImpl.getUpdates()) {
            sender.sendMessage("§a  - " + status.modId() + " §fv" + status.currentVersion()
                    + " §7-> §a" + status.latestVersion());
        }
    }

    private void reportServices(@NotNull ICommandSender sender) {
        sender.sendMessage("§7Registered services: §e" + LuaServiceRegistry.size());
    }

    private void reportTaskQueue(@NotNull ICommandSender sender) {
        int luaQueued = readSchedulerQueueSize();
        Object taskService = LuaServiceRegistry.get("TaskServiceImpl");
        if (taskService instanceof TaskServiceImpl tasks) {
            sender.sendMessage("§7Lua tasks: §e" + luaQueued + " queued§7 (scheduler), §e"
                    + tasks.getExecutedTaskCount() + " executed§7, §c"
                    + tasks.getDroppedTaskCount() + " dropped, §6"
                    + tasks.getSlowTaskCount() + " slow callbacks");
            return;
        }
        sender.sendMessage("§7Lua tasks: §e" + luaQueued + " queued§7 (scheduler)");
    }

    private void reportLuaCommands(@NotNull ICommandSender sender) {
        Object cmdService = LuaServiceRegistry.get("CommandServiceImpl");
        if (!(cmdService instanceof CommandServiceImpl commandService)) {
            return;
        }
        Collection<CommandDefinition> luaCommands = commandService.getSnapshot();
        sender.sendMessage("§7Lua-registered commands: §e" + luaCommands.size());
        for (CommandDefinition def : luaCommands) {
            sender.sendMessage("§7  - /§f" + def.name()
                    + " §7(mod:§f" + def.modId()
                    + "§7, op:§f" + def.permissionLevel()
                    + "§7, console:§f" + def.consoleAllowed()
                    + (def.aliases().isEmpty() ? "" : "§7, aliases:§f " + String.join(", ", def.aliases()))
                    + "§7)");
        }
    }

    /** Number of deferred + delayed callbacks in the active engine's bootstrap scheduler. */
    private static int readSchedulerQueueSize() {
        ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return 0;
        }
        try {
            ILuaTable stats = readStatsTable(engine);
            if (stats == null) {
                return 0;
            }
            ILuaValue queued = stats.rawget("queued");
            return queued == null || queued.isNil() ? 0 : queued.asInt();
        } catch (LuaException | IllegalStateException e) {
            logQueueReadFailure(e);
            return 0;
        }
    }

    private static @Nullable ILuaTable readStatsTable(@NotNull ILuaEngine engine) {
        ILuaValue taskValue = engine.getGlobalEnvironment().rawget("task");
        if (taskValue == null || !taskValue.isTable()) {
            return null;
        }
        ILuaValue statsFn = taskValue.asTable().rawget("_stats");
        if (statsFn == null || !statsFn.isFunction()) {
            return null;
        }
        ILuaValue stats = engine.callFunction(statsFn, taskValue);
        return stats != null && stats.isTable() ? stats.asTable() : null;
    }

    private static void logQueueReadFailure(@NotNull RuntimeException e) {
        LuaTweakerLog.get().debug(LogStage.SYSTEM,
                "/lt doctor could not read the Lua scheduler queue: " + e.getMessage());
    }
}
