package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.platform.command.NeoForgeCommandSender;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * /luatweaker dimension list | info &lt;id&gt; | tp &lt;id&gt;
 *
 * <p>Lists the Lua-registered dimensions, prints a dimension's parsed config
 * and teleports the executing player into a dimension.</p>
 */
public class DimensionCommand implements ILuaTweakerCommand {

    @Override
    public String getName() { return "dimension"; }

    @Override
    public String getDescription() { return "List, inspect or teleport to Lua-registered dimensions."; }

    @Override
    public boolean isConsoleAllowed() { return true; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendError("Usage: /lt dimension <list|info <id>|tp <id>>");
            return 0;
        }
        DimensionServiceImpl service = currentService();
        if (service == null) {
            sender.sendError("Dimension service is not registered.");
            return 0;
        }
        return switch (args[0]) {
            case "list" -> list(sender, service);
            case "info" -> info(sender, service, args);
            case "tp" -> teleport(sender, service, args);
            default -> {
                sender.sendError("Unknown sub-command '" + args[0] + "'. Use list, info or tp.");
                yield 0;
            }
        };
    }

    private int list(ICommandSender sender, DimensionServiceImpl service) {
        List<String> ids = service.getDimensionIds();
        if (ids.isEmpty()) {
            sender.sendMessage("§7No dimensions registered.");
            return 1;
        }
        sender.sendMessage("§6=== [LuaTweaker Dimensions] ===");
        for (String id : ids) {
            DimensionConfig config = service.getConfig(id);
            sender.sendMessage("§6  " + id + " §7- terrain: " + (service.getTerrainGenerator(id) != null ? "lua" : "config")
                    + ", picker: " + (service.getBlockPicker(id) != null ? "lua" : "none")
                    + ", biomes: " + config.biomes().size()
                    + ", spawns: " + config.spawnEntities().size());
        }
        return 1;
    }

    private int info(ICommandSender sender, DimensionServiceImpl service, String[] args) {
        if (args.length < 2) {
            sender.sendError("Usage: /lt dimension info <dimensionId>");
            return 0;
        }
        DimensionConfig config = service.getConfig(args[1]);
        if (config == null) {
            sender.sendError("Unknown dimension '" + args[1] + "'.");
            return 0;
        }
        sender.sendMessage("§6=== [Dimension: " + config.id() + "] ===");
        sender.sendMessage("§7  minY: §e" + config.minHeight() + " §7maxY: §e" + config.maxHeight()
                + " §7seaLevel: §e" + config.seaLevel());
        sender.sendMessage("§7  surface: §e" + config.surfaceBlock()
                + " §7subsurface: §e" + config.subsurfaceBlock()
                + " §7filler: §e" + config.fillerBlock());
        sender.sendMessage("§7  skyLight: §e" + config.hasSkyLight()
                + " §7natural: §e" + config.natural()
                + " §7skyColor: §e#" + Integer.toHexString(config.skyColor()));
        sender.sendMessage("§7  generator: §e" + (service.getTerrainGenerator(config.id()) != null ? "lua" : "config-only"));
        return 1;
    }

    private int teleport(ICommandSender sender, DimensionServiceImpl service, String[] args) {
        if (args.length < 2) {
            sender.sendError("Usage: /lt dimension tp <dimensionId>");
            return 0;
        }
        if (!(sender instanceof NeoForgeCommandSender neoforgeSender)) {
            sender.sendError("Only in-game players can use /lt dimension tp.");
            return 0;
        }
        CommandSourceStack source = neoforgeSender.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            sender.sendError("Only in-game players can use /lt dimension tp.");
            return 0;
        }
        if (service.getConfig(args[1]) == null) {
            sender.sendError("Unknown dimension '" + args[1] + "'.");
            return 0;
        }
        try {
            com.luatweaker.api.pal.Platform.getDimension().teleportToDimension(player, args[1]);
            sender.sendSuccess("Teleported to dimension '" + args[1] + "'.");
            return 1;
        } catch (IllegalStateException e) {
            sender.sendError(e.getMessage());
            return 0;
        }
    }

    private static DimensionServiceImpl currentService() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        return service instanceof DimensionServiceImpl dim ? dim : null;
    }
}
