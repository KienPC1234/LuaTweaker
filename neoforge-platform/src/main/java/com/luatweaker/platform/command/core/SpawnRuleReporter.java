package com.luatweaker.platform.command.core;

import com.luatweaker.api.command.ICommandSender;
import com.luatweaker.platform.spawn.SpawnRuleEvaluator;
import com.luatweaker.spawn.SpawnRule;
import com.luatweaker.spawn.SpawnRuleServiceImpl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Rendering half of {@code /lt spawn}: turns declarative rules and evaluation results into the
 * chat report an admin reads. Kept out of {@link SpawnCommand} so command dispatch stays small.
 */
final class SpawnRuleReporter {

    private static final String CONDITION_SEPARATOR = "§7, ";

    private SpawnRuleReporter() {
    }

    static void reportRules(@NotNull ICommandSender sender, @NotNull String dimensionId,
                            @NotNull List<SpawnRule> rules) {
        sender.sendMessage("§6=== [Spawn rules: " + dimensionId + "] ===");
        if (rules.isEmpty()) {
            sender.sendMessage("§7  No declarative rules (the dimension uses spawnEntities or a handler).");
            return;
        }
        for (SpawnRule rule : rules) {
            sender.sendMessage(describeRule(rule));
            sender.sendMessage("§8      " + describeConditions(rule));
        }
    }

    static void reportEvaluation(@NotNull ICommandSender sender, @NotNull String dimensionId,
                                 @NotNull List<SpawnRule> rules, @NotNull ServerLevel level,
                                 @NotNull BlockPos pos, @NotNull SpawnRuleServiceImpl spawnRules) {
        sender.sendMessage("§6=== [Spawn rule test at " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + ": " + dimensionId + "] ===");
        if (rules.isEmpty()) {
            sender.sendMessage("§7  No declarative rules for this dimension.");
            return;
        }
        List<SpawnRuleEvaluator.Resolved> resolved = SpawnRuleEvaluator.resolve(dimensionId, rules);
        SpawnRuleEvaluator.Attempt attempt = SpawnRuleEvaluator.attemptAt(level, pos);
        int allowed = 0;
        for (SpawnRuleEvaluator.Resolved rule : resolved) {
            boolean matches = SpawnRuleEvaluator.matches(rule, rule.type().getCategory(), level,
                    level.getBiome(pos), pos, dimensionId, spawnRules, attempt);
            if (matches) {
                allowed++;
            }
            sender.sendMessage((matches ? "§a  ALLOW " : "§c  BLOCK ") + "§e" + rule.rule().entity()
                    + " §7(" + rule.type().getCategory().getSerializedName() + ")");
        }
        sender.sendMessage("§7  " + allowed + "/" + resolved.size() + " rule(s) allow a spawn here.");
    }

    private static @NotNull String describeRule(@NotNull SpawnRule rule) {
        return "§e  " + rule.entity()
                + " §7category: §f" + (rule.category() == null ? "auto" : rule.category())
                + " §7weight: §f" + rule.weight()
                + " §7group: §f" + rule.minGroup() + "-" + rule.maxGroup();
    }

    private static @NotNull String describeConditions(@NotNull SpawnRule rule) {
        StringBuilder conditions = new StringBuilder();
        appendRanges(conditions, rule);
        appendEnvironment(conditions, rule);
        appendFilters(conditions, rule);
        return conditions.length() == 0 ? "no conditions" : conditions.toString();
    }

    private static void appendRanges(@NotNull StringBuilder conditions, @NotNull SpawnRule rule) {
        append(conditions, "y", rule.minY() + ".." + rule.maxY());
        append(conditions, "light", rule.minBlockLight() + ".." + rule.maxBlockLight());
        append(conditions, "sky", rule.minSkyLight() + ".." + rule.maxSkyLight());
    }

    private static void appendEnvironment(@NotNull StringBuilder conditions, @NotNull SpawnRule rule) {
        if (rule.requireDay() != null) {
            append(conditions, "time", rule.requireDay() ? "day" : "night");
        }
        if (Boolean.TRUE.equals(rule.requireThundering())) {
            append(conditions, "weather", "thunder");
        } else if (rule.requireRaining() != null) {
            append(conditions, "weather", rule.requireRaining() ? "rain" : "clear");
        }
    }

    private static void appendFilters(@NotNull StringBuilder conditions, @NotNull SpawnRule rule) {
        if (!rule.biomes().isEmpty()) {
            append(conditions, "biomes", String.join(",", rule.biomes()));
        }
        if (!rule.blocksBelow().isEmpty()) {
            append(conditions, "below", String.join(",", rule.blocksBelow()));
        }
        if (rule.requireSolidGround()) {
            append(conditions, "solidGround", "true");
        }
        if (rule.check() != null) {
            append(conditions, "check", "lua");
        }
    }

    private static void append(@NotNull StringBuilder builder, @NotNull String key, @NotNull String value) {
        if (builder.length() > 0) {
            builder.append(CONDITION_SEPARATOR);
        }
        builder.append("§7").append(key).append("=§f").append(value);
    }
}
