package com.luatweaker.common.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.common.CommonServerHolder;
import com.luatweaker.dimension.DimensionBenchmark;
import com.luatweaker.dimension.DimensionConfig;
import com.luatweaker.dimension.DimensionServiceImpl;
import com.luatweaker.common.command.MinecraftCommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * /luatweaker dimension list | info &lt;id&gt; | tp &lt;id&gt; [playerName] | bench &lt;id&gt; [radius]
 *
 * <p>Lists the Lua-registered dimensions, prints a dimension's parsed config,
 * teleports a player into a dimension and measures the Lua terrain pipeline
 * (Lua calls per chunk and milliseconds per chunk). Console callers must name
 * an online player as the teleport target.</p>
 */
public class DimensionCommand implements ILuaTweakerCommand {
    private static final int MIN_ARGS_FOR_ID = 2;
    private static final int MIN_ARGS_FOR_VALUE = 3;


    @Override
    public String getName() { return "dimension"; }

    @Override
    public String getDescription() { return "List, inspect, benchmark or teleport to Lua-registered dimensions."; }

    @Override
    public boolean isConsoleAllowed() { return true; }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendError("Usage: /lt dimension <list|info <id>|tp <id> [playerName]|bench <id> [radius]>");
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
            case "bench" -> bench(sender, service, args);
            default -> {
                sender.sendError("Unknown sub-command '" + args[0] + "'. Use list, info, tp or bench.");
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
            sender.sendMessage("§6  " + id + " §7- terrain: " + terrainLabel(service, id)
                    + ", biomes: " + config.biomes().size()
                    + ", spawns: " + config.spawnEntities().size());
        }
        return 1;
    }

    private int info(ICommandSender sender, DimensionServiceImpl service, String... args) {
        if (args.length < MIN_ARGS_FOR_ID) {
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
        sender.sendMessage("§7  terrain: §e" + terrainLabel(service, config.id())
                + " §7biome batch: §e" + (service.hasBiomeBatch(config.id()) ? "yes" : "no"));
        return 1;
    }

    private int bench(ICommandSender sender, DimensionServiceImpl service, String... args) {
        if (args.length < MIN_ARGS_FOR_ID) {
            sender.sendError("Usage: /lt dimension bench <dimensionId> [radius 0-"
                    + DimensionBenchmark.MAX_RADIUS + "]");
            return 0;
        }
        int radius = DimensionBenchmark.DEFAULT_RADIUS;
        if (args.length >= MIN_ARGS_FOR_VALUE) {
            try {
                radius = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendError("Radius must be a whole number, got '" + args[2] + "'.");
                return 0;
            }
        }
        try {
            DimensionBenchmark.Report report = DimensionBenchmark.run(service, args[1], radius);
            sender.sendMessage("§6=== [Terrain benchmark: " + report.dimensionId() + "] ===");
            sender.sendMessage("§7  mode: §e" + report.mode()
                    + " §7chunks: §e" + report.chunks()
                    + " §7radius: §e" + report.chunkRadius());
            sender.sendMessage(String.format("§7  total: §e%.2f ms §7(§e%.3f ms/chunk§7)",
                    report.totalMillis(), report.millisPerChunk()));
            sender.sendMessage(String.format("§7  Lua calls: §e%.2f /chunk §7(total §e%d§7)",
                    report.luaCallsPerChunk(), report.luaCalls()));
            sender.sendMessage("§8  Block placement/lighting are not included; numbers are the Lua pipeline only.");
            return 1;
        } catch (IllegalArgumentException | IllegalStateException e) {
            sender.sendError(e.getMessage());
            return 0;
        }
    }

    private static String terrainLabel(DimensionServiceImpl service, String dimensionId) {
        return switch (service.getTerrainMode(dimensionId)) {
            case CHUNK -> "chunk-batch";
            case SPEC -> "declarative-spec";
            case LEGACY -> service.getTerrainGenerator(dimensionId) != null ? "per-column-lua" : "config-only";
        };
    }

    private int teleport(ICommandSender sender, DimensionServiceImpl service, String... args) {
        if (args.length < MIN_ARGS_FOR_ID) {
            sender.sendError("Usage: /lt dimension tp <dimensionId> [playerName]");
            return 0;
        }
        String dimensionId = args[1];
        if (service.getConfig(dimensionId) == null) {
            sender.sendError("Unknown dimension '" + dimensionId + "'.");
            return 0;
        }
        ServerPlayer player = resolveTarget(sender, args);
        if (player == null) {
            return 0;
        }
        try {
            boolean teleported = com.luatweaker.api.pal.Platform.getDimension()
                    .teleportToDimension(player, dimensionId);
            if (!teleported) {
                sender.sendError("Could not teleport to dimension '" + dimensionId
                        + "': the server refused (see the log for the safe-spawn check).");
                return 0;
            }
            sender.sendSuccess("Teleported to dimension '" + dimensionId + "'.");
            return 1;
        } catch (IllegalStateException e) {
            sender.sendError(e.getMessage());
            return 0;
        }
    }

    private static @Nullable ServerPlayer resolveTarget(ICommandSender sender, String... args) {
        if (args.length >= MIN_ARGS_FOR_VALUE) {
            MinecraftServer server = CommonServerHolder.get();
            if (server == null) {
                sender.sendError("Server is not running.");
                return null;
            }
            ServerPlayer player = findPlayer(server, args[2]);
            if (player == null) {
                sender.sendError("Player '" + args[2] + "' is not online.");
            }
            return player;
        }
        if (sender instanceof MinecraftCommandSender neoforgeSender
                && neoforgeSender.getSource().getEntity() instanceof ServerPlayer executing) {
            return executing;
        }
        sender.sendError("Console needs a player name: /lt dimension tp <dimensionId> <playerName>");
        return null;
    }

    private static @Nullable ServerPlayer findPlayer(MinecraftServer server, String nameOrUuid) {
        try {
            return server.getPlayerList().getPlayer(UUID.fromString(nameOrUuid));
        } catch (IllegalArgumentException notUuid) {
            return server.getPlayerList().getPlayerByName(nameOrUuid);
        }
    }

    private static DimensionServiceImpl currentService() {
        Object service = com.luatweaker.core.service.LuaServiceRegistry.get("DimensionServiceImpl");
        return service instanceof DimensionServiceImpl dim ? dim : null;
    }
}
