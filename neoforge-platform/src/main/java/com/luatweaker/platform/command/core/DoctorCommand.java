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
            sender.sendMessage("§7  " + status + " §f" + mod.getManifest().id() + " §7v" + mod.getManifest().version());
        }

        int serviceCount = LuaServiceRegistry.size();
        sender.sendMessage("§7Registered services: §e" + serviceCount);
        return 1;
    }
}
