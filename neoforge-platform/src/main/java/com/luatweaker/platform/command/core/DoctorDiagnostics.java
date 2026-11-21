package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.network.NetworkServiceImpl;
import com.luatweaker.platform.LuaTweakerMod;
import com.luatweaker.tasks.TaskServiceImpl;
import org.jetbrains.annotations.NotNull;
import org.luava.runtime.LuaException;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diagnostics sub-reporters for {@link DoctorCommand}.
 */
final class DoctorDiagnostics {
    private static final int MAX_CHANNEL_LINES = 10;

    private DoctorDiagnostics() {}

    static void reportTaskQueue(@NotNull ICommandSender sender) {
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

    static void reportNetwork(@NotNull ICommandSender sender) {
        Object networkService = LuaServiceRegistry.get("NetworkServiceImpl");
        if (!(networkService instanceof NetworkServiceImpl network)) {
            return;
        }
        Map<String, String> owners = network.getChannelOwners();
        if (owners.isEmpty()) {
            sender.sendMessage("§7Remote channels: §e0");
            return;
        }
        Set<String> blocked = network.getInboundBlockedNames();
        sender.sendMessage("§7Remote channels: §e" + owners.size());
        List<String> names = new ArrayList<>(owners.keySet());
        names.sort(String.CASE_INSENSITIVE_ORDER);
        int shown = 0;
        for (String name : names) {
            if (shown >= MAX_CHANNEL_LINES) {
                break;
            }
            String state = blocked.contains(name) ? "§cinbound blocked" : "§ainbound open";
            sender.sendMessage("§7  - §f" + name + " §7(owner:§f" + owners.get(name) + "§7) " + state);
            shown++;
        }
        if (owners.size() > shown) {
            sender.sendMessage("§7  ... and " + (owners.size() - shown) + " more.");
        }
    }

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
            LuaTweakerLog.get().debug(LogStage.SYSTEM,
                    "/lt doctor could not read the Lua scheduler queue: " + e.getMessage());
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
}
