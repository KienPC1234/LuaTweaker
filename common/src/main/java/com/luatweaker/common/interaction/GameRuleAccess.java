package com.luatweaker.common.interaction;

import com.luatweaker.common.CommonServerHolder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Dynamic reader and writer for vanilla and modded {@link GameRules} by rule id.
 */
final class GameRuleAccess {

    private GameRuleAccess() {
    }

    @Nullable
    static Object get(@Nullable String ruleName) {
        MinecraftServer server = CommonServerHolder.get();
        if (server == null || ruleName == null || ruleName.isBlank()) {
            return null;
        }
        GameRules rules = server.getGameRules();
        String target = ruleName.trim();
        AtomicReference<Object> found = new AtomicReference<>();
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(@NotNull GameRules.Key<GameRules.BooleanValue> key,
                                     @NotNull GameRules.Type<GameRules.BooleanValue> type) {
                if (key.getId().equalsIgnoreCase(target) && type != null) {
                    found.set(rules.getBoolean(key));
                }
            }

            @Override
            public void visitInteger(@NotNull GameRules.Key<GameRules.IntegerValue> key,
                                     @NotNull GameRules.Type<GameRules.IntegerValue> type) {
                if (key.getId().equalsIgnoreCase(target) && type != null) {
                    found.set(rules.getInt(key));
                }
            }
        });
        return found.get();
    }

    static boolean set(@Nullable String ruleName, @Nullable Object value) {
        MinecraftServer server = CommonServerHolder.get();
        if (server == null || ruleName == null || ruleName.isBlank() || value == null) {
            return false;
        }
        GameRules rules = server.getGameRules();
        String target = ruleName.trim();
        AtomicBoolean applied = new AtomicBoolean(false);
        GameRules.visitGameRuleTypes(new GameRules.GameRuleTypeVisitor() {
            @Override
            public void visitBoolean(@NotNull GameRules.Key<GameRules.BooleanValue> key,
                                     @NotNull GameRules.Type<GameRules.BooleanValue> type) {
                if (type != null && key.getId().equalsIgnoreCase(target) && value instanceof Boolean flag) {
                    rules.getRule(key).set(flag, server);
                    applied.set(true);
                }
            }

            @Override
            public void visitInteger(@NotNull GameRules.Key<GameRules.IntegerValue> key,
                                     @NotNull GameRules.Type<GameRules.IntegerValue> type) {
                if (type != null && key.getId().equalsIgnoreCase(target) && value instanceof Number number) {
                    rules.getRule(key).set(number.intValue(), server);
                    applied.set(true);
                }
            }
        });
        return applied.get();
    }
}
