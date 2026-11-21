package com.luatweaker.spawn;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.spawn.ISpawnRuleService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Full-control spawn handlers, per dimension. The engine only provides the
 * timing: every few server ticks the Lua handler is called with the dimension
 * id and the players inside it, and decides everything itself.
 */
public class SpawnRuleServiceImpl implements ISpawnRuleService {

    private static final String DIMENSION_ID_FIELD = "dimensionId";

    private final ILuaEngine engine;
    private final Map<String, ILuaValue> handlersByDimension = new ConcurrentHashMap<>();
    private final Map<String, List<SpawnRule>> rulesByDimension = new ConcurrentHashMap<>();
    private final AtomicLong rulesVersion = new AtomicLong();
    private final AtomicReference<ILuaValue> globalSpawnCheck = new AtomicReference<>();

    public SpawnRuleServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void registerHandler(@NotNull String dimensionId, @NotNull Object luaFunction) {
        SpawnRuleParser.validateDimensionId(DIMENSION_ID_FIELD, dimensionId);
        if (!(luaFunction instanceof ILuaValue value) || !value.isFunction()) {
            throw new IllegalArgumentException("spawn handler must be a Lua function, got: "
                    + (luaFunction != null ? luaFunction.getClass().getSimpleName() : "nil"));
        }
        handlersByDimension.put(dimensionId, value);
    }

    @Override
    public void clearHandler(@NotNull String dimensionId) {
        SpawnRuleParser.validateDimensionId(DIMENSION_ID_FIELD, dimensionId);
        handlersByDimension.remove(dimensionId);
    }

    @Override
    public void clearAll() {
        handlersByDimension.clear();
        rulesByDimension.clear();
        rulesVersion.incrementAndGet();
        globalSpawnCheck.set(null);
    }

    @Override
    public void addRule(@NotNull String dimensionId, @NotNull ILuaValue rule) {
        SpawnRuleParser.validateDimensionId(DIMENSION_ID_FIELD, dimensionId);
        SpawnRule parsed = SpawnRuleParser.parse(dimensionId, rule);
        rulesByDimension.compute(dimensionId, (key, existing) -> {
            List<SpawnRule> merged = new java.util.ArrayList<>(existing == null ? List.of() : existing);
            merged.add(parsed);
            return List.copyOf(merged);
        });
        rulesVersion.incrementAndGet();
        LuaTweakerLog.get().info(LogStage.SYSTEM,
                "Spawn rule added for '" + dimensionId + "': " + parsed.entity()
                        + " weight " + parsed.weight() + ", group " + parsed.minGroup()
                        + "-" + parsed.maxGroup());
    }

    @Override
    public void clearRules(@NotNull String dimensionId) {
        SpawnRuleParser.validateDimensionId(DIMENSION_ID_FIELD, dimensionId);
        if (rulesByDimension.remove(dimensionId) != null) {
            rulesVersion.incrementAndGet();
        }
    }

    @Override
    @Nullable
    public Object getRules(@NotNull String dimensionId) {
        ILuaTable out = engine.createTable();
        List<SpawnRule> rules = rulesFor(dimensionId);
        for (int i = 0; i < rules.size(); i++) {
            out.rawset(i + 1, SpawnRuleDescriber.describe(engine, rules.get(i)));
        }
        return out;
    }

    /** Declarative rules for a dimension (empty when none). Immutable snapshot. */
    @NotNull
    public List<SpawnRule> rulesFor(@NotNull String dimensionId) {
        return rulesByDimension.getOrDefault(dimensionId, List.of());
    }

    /** Bumped whenever a rule is added or removed; the platform uses it to rebuild its cache. */
    public long getRulesVersion() {
        return rulesVersion.get();
    }

    /** Dimensions that currently have at least one declarative rule. */
    @NotNull
    public java.util.Set<String> getRuleDimensions() {
        return java.util.Collections.unmodifiableSet(rulesByDimension.keySet());
    }

    /**
     * Runs a rule's optional Lua predicate. Only an explicit {@code false} vetoes; {@code nil}
     * (a handler that returns nothing) allows the spawn.
     */
    public boolean runCheck(@NotNull SpawnRule rule, @NotNull ILuaTable context) {
        ILuaValue check = rule.check();
        if (check == null) {
            return true;
        }
        ILuaValue result = engine.callFunction(check, context);
        return result == null || result.isNil() || !result.isBoolean() || result.asBoolean();
    }

    @Override
    public void onSpawnCheck(@NotNull Object luaFunction) {
        if (!(luaFunction instanceof ILuaValue value) || !value.isFunction()) {
            throw new IllegalArgumentException("spawn check hook must be a Lua function, got: "
                    + (luaFunction != null ? luaFunction.getClass().getSimpleName() : "nil"));
        }
        this.globalSpawnCheck.set(value);
    }

    @Override
    public void clearSpawnCheck() {
        this.globalSpawnCheck.set(null);
    }

    @Override
    @NotNull
    public java.util.Set<String> getRegisteredDimensions() {
        return java.util.Collections.unmodifiableSet(handlersByDimension.keySet());
    }

    // -------------------------------------------------------------------------
    // Queries for the platform spawner
    // -------------------------------------------------------------------------

    /** Returns the full-control spawn handler for a dimension, or null. */
    @Nullable
    public ILuaValue getHandler(@NotNull String dimensionId) {
        return handlersByDimension.get(dimensionId);
    }

    /** Returns the global spawn check hook, or null. */
    @Nullable
    public ILuaValue getSpawnCheck() {
        return globalSpawnCheck.get();
    }

    /** Engine owning the handlers (used to invoke them). */
    @NotNull
    public ILuaEngine getEngine() {
        return engine;
    }
}
