package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.LuaTweakerMod;

import java.util.Map;

/**
 * /luatweaker doctor
 *
 * Health diagnostics for the Lua runtime: loaded mods, per-mod load errors,
 * active engine status and the service registry state.
 */
public class DoctorCommand implements ILuaTweakerCommand {

    /** Number of deferred + delayed callbacks in the active engine's bootstrap scheduler. */
    private static int readSchedulerQueueSize() {
        com.luatweaker.api.vm.ILuaEngine engine = LuaTweakerMod.getActiveEngine();
        if (engine == null) {
            return 0;
        }
        try {
            com.luatweaker.api.vm.ILuaValue taskValue = engine.getGlobalEnvironment().rawget("task");
            if (taskValue == null || !taskValue.isTable()) {
                return 0;
            }
            com.luatweaker.api.vm.ILuaValue statsFn = taskValue.asTable().rawget("_stats");
            if (statsFn == null || !statsFn.isFunction()) {
                return 0;
            }
            com.luatweaker.api.vm.ILuaValue stats = engine.callFunction(statsFn, taskValue);
            if (stats == null || !stats.isTable()) {
                return 0;
            }
            com.luatweaker.api.vm.ILuaValue queued = stats.asTable().rawget("queued");
            return queued == null || queued.isNil() ? 0 : queued.asInt();
        } catch (RuntimeException e) {
            logQueueReadFailure(e);
            return 0;
        }
    }

    private static void logQueueReadFailure(RuntimeException e) {
        com.luatweaker.api.log.LuaTweakerLog.get().debug(
                com.luatweaker.api.log.LogStage.SYSTEM,
                "/lt doctor could not read the Lua scheduler queue: " + e.getMessage());
    }

    @Override
    public String getName() { return "doctor"; }

    @Override
    public String getDescription() { return "Health diagnostics: mods, engine, services, load errors"; }

    @Override
    public int getPermissionLevel() { return 2; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        sender.sendMessage("§6========== LuaTweaker Doctor ==========");

        boolean engineOk = LuaTweakerMod.getActiveEngine() != null;
        sender.sendMessage("§7Engine: §" + (engineOk ? "aACTIVE" : "cMISSING"));

        Map<String, com.luatweaker.core.mod.LuaMod> mods = LuaModManager.getLoadedMods();
        sender.sendMessage("§7Loaded LuaMods: §e" + mods.size());

        Map<String, String> loadErrors = LuaModManager.getLoadErrors();
        if (loadErrors.isEmpty()) {
            sender.sendMessage("§a  All mods loaded cleanly.");
        } else {
            sender.sendMessage("§c  Load errors (" + loadErrors.size() + "):");
            for (Map.Entry<String, String> entry : loadErrors.entrySet()) {
                sender.sendMessage("§c    - " + entry.getKey() + ": " + entry.getValue());
            }
        }

        for (com.luatweaker.core.mod.LuaMod mod : mods.values()) {
            String status = loadErrors.containsKey(mod.getManifest().id()) ? "§c[ERROR]" : "§a[OK]";
            sender.sendMessage("§7  " + status + " §f" + mod.getManifest().id() + " §7v" + mod.getManifest().version()
                    + (mod.getManifest().updateUrl() == null ? "" : " §8(update feed declared)"));
            if (mod.getManifest().permissions().contains(com.luatweaker.update.WebServiceImpl.PERMISSION)) {
                sender.sendMessage("§c    ! holds 'net.http' permission (internet access)");
            }
        }

        var updateStatuses = com.luatweaker.update.UpdateServiceImpl.getUpdateStatuses();
        if (!updateStatuses.isEmpty()) {
            sender.sendMessage("§7Update checks: §e" + updateStatuses.size()
                    + " declared, §a" + com.luatweaker.update.UpdateServiceImpl.getUpdates().size() + " available");
            for (com.luatweaker.update.UpdateStatus us : com.luatweaker.update.UpdateServiceImpl.getUpdates()) {
                sender.sendMessage("§a  - " + us.modId() + " §fv" + us.currentVersion() + " §7-> §a" + us.latestVersion());
            }
        }

        int serviceCount = LuaServiceRegistry.size();
        sender.sendMessage("§7Registered services: §e" + serviceCount);

        int luaQueued = readSchedulerQueueSize();
        Object taskService = LuaServiceRegistry.get("TaskServiceImpl");
        if (taskService instanceof com.luatweaker.tasks.TaskServiceImpl tasks) {
            sender.sendMessage("§7Lua tasks: §e" + luaQueued + " queued§7 (scheduler), §e"
                    + tasks.getExecutedTaskCount() + " executed§7, §c"
                    + tasks.getDroppedTaskCount() + " dropped, §6"
                    + tasks.getSlowTaskCount() + " slow callbacks");
        } else {
            sender.sendMessage("§7Lua tasks: §e" + luaQueued + " queued§7 (scheduler)");
        }

        Object cmdService = LuaServiceRegistry.get("CommandServiceImpl");
        if (cmdService instanceof com.luatweaker.command.CommandServiceImpl commandService) {
            var luaCommands = commandService.getSnapshot();
            sender.sendMessage("§7Lua-registered commands: §e" + luaCommands.size());
            for (var def : luaCommands) {
                sender.sendMessage("§7  - /§f" + def.name()
                        + " §7(mod:§f" + def.modId()
                        + "§7, op:§f" + def.permissionLevel()
                        + "§7, console:§f" + def.consoleAllowed()
                        + (def.aliases().isEmpty() ? "" : "§7, aliases:§f " + String.join(", ", def.aliases()))
                        + "§7)");
            }
        }
        return 1;
    }
}
