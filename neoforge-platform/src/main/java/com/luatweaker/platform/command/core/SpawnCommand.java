package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.api.command.ILuaTweakerCommand;
import com.luatweaker.core.service.LuaServiceRegistry;
import com.luatweaker.platform.command.NeoForgeCommandSender;
import com.luatweaker.platform.dimension.DimensionMobSpawner;
import com.luatweaker.spawn.SpawnRule;
import com.luatweaker.spawn.SpawnRuleServiceImpl;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * /luatweaker spawn list|clear|test|spawn - inspect and drive the Lua spawn system on a live
 * server.
 *
 * <p>{@code list} prints the declarative rules of a dimension; {@code test} evaluates them at
 * the player's position (the same evaluation the natural spawner does); {@code spawn} forces a
 * validated spawn at the player, so an admin can check a rule or an entity id instantly.</p>
 */
public class SpawnCommand implements ILuaTweakerCommand {
    private static final int MIN_ARGS_FOR_ID = 2;
    private static final int MIN_ARGS_FOR_VALUE = 3;
    private static final int MIN_ARGS_FOR_DIMENSION = 2;
    private static final int MIN_SPAWN_COUNT = 1;
    private static final int MAX_SPAWN_COUNT = 32;

    @Override
    public String getName() { return "spawn"; }

    @Override
    public String getDescription() {
        return "Inspect spawn rules, test them at your position, or force a validated spawn.";
    }

    @Override
    public int execute(ICommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendError("Usage: /lt spawn <list <dim>|clear [dim]|test [dim]|spawn <entity> [count]>");
            return 0;
        }
        SpawnRuleServiceImpl spawnRules = currentSpawnService();
        if (spawnRules == null) {
            sender.sendError("Spawn service is not registered.");
            return 0;
        }
        return switch (args[0]) {
            case "list" -> list(sender, spawnRules, args);
            case "clear" -> clear(sender, spawnRules, args);
            case "test" -> test(sender, spawnRules, args);
            case "spawn" -> spawn(sender, args);
            default -> {
                sender.sendError("Unknown sub-command '" + args[0] + "'. Use list, clear, test or spawn.");
                yield 0;
            }
        };
    }

    private int list(@NotNull ICommandSender sender, @NotNull SpawnRuleServiceImpl spawnRules,
                     @NotNull String... args) {
        String dimensionId = dimensionArg(sender, args, 1);
        if (dimensionId == null) {
            sender.sendError("Usage: /lt spawn list <dimensionId>");
            return 0;
        }
        List<SpawnRule> rules = spawnRules.rulesFor(dimensionId);
        SpawnRuleReporter.reportRules(sender, dimensionId, rules);
        return 1;
    }

    private int clear(@NotNull ICommandSender sender, @NotNull SpawnRuleServiceImpl spawnRules,
                      @NotNull String... args) {
        String dimensionId = dimensionArg(sender, args, 1);
        if (dimensionId == null) {
            sender.sendError("Usage: /lt spawn clear [dimensionId] (defaults to your dimension)");
            return 0;
        }
        spawnRules.clearRules(dimensionId);
        sender.sendSuccess("Spawn rules cleared for '" + dimensionId + "'.");
        return 1;
    }

    private int test(@NotNull ICommandSender sender, @NotNull SpawnRuleServiceImpl spawnRules,
                     @NotNull String... args) {
        ServerPlayer player = playerOf(sender);
        if (player == null) {
            sender.sendError("Only in-game players can use /lt spawn test.");
            return 0;
        }
        String dimensionId = args.length >= MIN_ARGS_FOR_DIMENSION
                ? args[1] : player.serverLevel().dimension().location().toString();
        List<SpawnRule> rules = spawnRules.rulesFor(dimensionId);
        SpawnRuleReporter.reportEvaluation(sender, dimensionId, rules,
                player.serverLevel(), player.blockPosition(), spawnRules);
        return 1;
    }

    private int spawn(@NotNull ICommandSender sender, @NotNull String... args) {
        if (args.length < MIN_ARGS_FOR_ID) {
            sender.sendError("Usage: /lt spawn <entityId> [count 1-32]");
            return 0;
        }
        ServerPlayer player = playerOf(sender);
        if (player == null) {
            sender.sendError("Only in-game players can use /lt spawn.");
            return 0;
        }
        int count = parseCount(sender, args);
        if (count == 0) {
            return 0;
        }
        int spawned = spawnAttempts(player.serverLevel(), args[1], player.blockPosition(), count);
        if (spawned == 0) {
            sender.sendError("Could not spawn '" + args[1] + "' at your position (unknown entity, or the"
                    + " ground/collision/spawn rules rejected it).");
            return 0;
        }
        sender.sendSuccess("Spawned " + spawned + "/" + count + " x " + args[1] + ".");
        return 1;
    }

    /** Runs {@code count} validated spawn attempts and reports how many the engine accepted. */
    private static int spawnAttempts(@NotNull ServerLevel level, @NotNull String entityId,
                                     @NotNull BlockPos pos, int count) {
        String dimensionId = level.dimension().location().toString();
        int spawned = 0;
        for (int attempt = 0; attempt < count; attempt++) {
            if (DimensionMobSpawner.spawnEntity(level, dimensionId, entityId, pos)) {
                spawned++;
            }
        }
        return spawned;
    }

    /** Parses the optional count argument; returns 0 when the sender must be rejected. */
    private int parseCount(@NotNull ICommandSender sender, @NotNull String... args) {
        if (args.length < MIN_ARGS_FOR_VALUE) {
            return MIN_SPAWN_COUNT;
        }
        try {
            return Math.clamp(Integer.parseInt(args[2]), MIN_SPAWN_COUNT, MAX_SPAWN_COUNT);
        } catch (NumberFormatException e) {
            sender.sendError("Count must be a whole number, got '" + args[2] + "'.");
            return 0;
        }
    }

    /** Args[1] when present, else the sender's current dimension, else null. */
    private static @Nullable String dimensionArg(@NotNull ICommandSender sender, @NotNull String[] args,
                                                 int index) {
        if (args.length > index) {
            return args[index];
        }
        ServerPlayer player = playerOf(sender);
        return player != null ? player.serverLevel().dimension().location().toString() : null;
    }

    private static @Nullable ServerPlayer playerOf(@NotNull ICommandSender sender) {
        if (!(sender instanceof NeoForgeCommandSender neoforgeSender)) {
            return null;
        }
        CommandSourceStack source = neoforgeSender.getSource();
        return source.getEntity() instanceof ServerPlayer player ? player : null;
    }

    private static @Nullable SpawnRuleServiceImpl currentSpawnService() {
        Object service = LuaServiceRegistry.get("SpawnRuleServiceImpl");
        return service instanceof SpawnRuleServiceImpl spawn ? spawn : null;
    }
}
