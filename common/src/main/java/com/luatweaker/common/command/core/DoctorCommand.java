package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.command.CommandDefinition;
import com.luatweaker.command.CommandServiceImpl;
import com.luatweaker.core.mod.LuaMod;
import com.luatweaker.core.mod.LuaModManager;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.common.LuaTweakerCommon;
import com.luatweaker.common.content.DatapackLayoutValidator;
import com.luatweaker.update.UpdateServiceImpl;
import com.luatweaker.update.UpdateStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * /luatweaker doctor
 *
 * Health diagnostics for the Lua runtime: loaded mods, per-mod load errors,
 * active engine status and the service registry state.
 */
public class DoctorCommand implements ILuaTweakerCommand {

    private static final String SECTION_HEADER = "§6========== LuaTweaker Doctor ==========";

    private static final int MAX_SHOWN_WARNINGS = 10;

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
        DoctorDiagnostics.reportTaskQueue(sender);
        reportLuaCommands(sender);
        DoctorDiagnostics.reportNetwork(sender);
        reportDatapack(sender);
        return 1;
    }

    private void reportEngine(@NotNull ICommandSender sender) {
        boolean engineOk = LuaTweakerCommon.getActiveEngine() != null;
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

    private void reportDatapack(@NotNull ICommandSender sender) {
        Object datapack = LuaServiceRegistry.get("Datapack");
        if (datapack instanceof com.luatweaker.api.content.IDatapackService datapackService) {
            sender.sendMessage("§7Virtual datapack files: §e" + datapackService.getVirtualFiles().size());
        }
        File gameFolder;
        try {
            gameFolder = dev.architectury.platform.Platform.getGameFolder().toFile();
        } catch (IllegalStateException | LinkageError | AssertionError t) {
            gameFolder = new File(".");
        }
        File luamodsDir = new File(gameFolder, "luamods");
        List<String> warnings = DatapackLayoutValidator.validate(luamodsDir);
        if (warnings.isEmpty()) {
            sender.sendMessage("§a  data/ + assets/ layout clean.");
            return;
        }
        sender.sendMessage("§e  data/ + assets/ layout warnings (" + warnings.size() + "):");
        int shown = 0;
        for (String warning : warnings) {
            if (shown >= MAX_SHOWN_WARNINGS) {
                break;
            }
            sender.sendMessage("§e    - " + warning);
            shown++;
        }
        if (warnings.size() > shown) {
            sender.sendMessage("§e    ... and " + (warnings.size() - shown) + " more.");
        }
    }
}
