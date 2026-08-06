package com.luatweaker.spawn;

import com.luatweaker.api.spawn.ISpawnRuleService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Full-control spawn handlers, per dimension. The engine only provides the
 * timing: every few server ticks the Lua handler is called with the dimension
 * id and the players inside it, and decides everything itself.
 */
public class SpawnRuleServiceImpl implements ISpawnRuleService {

    /** Minecraft resource-location charset: [a-z0-9_.-] for path, optional [a-z0-9_.-] namespace. */
    private static final java.util.regex.Pattern RESOURCE_LOCATION =
            java.util.regex.Pattern.compile("^[a-z0-9_.-]+(:[a-z0-9_./-]+)?$");

    private final ILuaEngine engine;
    private final Map<String, ILuaValue> handlersByDimension = new ConcurrentHashMap<>();

    public SpawnRuleServiceImpl(@NotNull ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public void registerHandler(@NotNull String dimensionId, @NotNull Object luaFunction) {
        validateId("dimensionId", dimensionId);
        if (!(luaFunction instanceof ILuaValue value) || !value.isFunction()) {
            throw new IllegalArgumentException("spawn handler must be a Lua function, got: "
                    + (luaFunction != null ? luaFunction.getClass().getSimpleName() : "nil"));
        }
        handlersByDimension.put(dimensionId, value);
    }

    @Override
    public void clearHandler(@NotNull String dimensionId) {
        validateId("dimensionId", dimensionId);
        handlersByDimension.remove(dimensionId);
    }

    @Override
    public void clearAll() {
        handlersByDimension.clear();
    }

    // -------------------------------------------------------------------------
    // Queries for the platform spawner
    // -------------------------------------------------------------------------

    /** Returns the full-control spawn handler for a dimension, or null. */
    @Nullable
    public ILuaValue getHandler(@NotNull String dimensionId) {
        return handlersByDimension.get(dimensionId);
    }

    /** Engine owning the handlers (used to invoke them). */
    @NotNull
    public ILuaEngine getEngine() {
        return engine;
    }

    private static void validateId(String name, String id) {
        if (id == null || !RESOURCE_LOCATION.matcher(id).matches()) {
            throw new IllegalArgumentException(name + " must be a valid resource location (e.g. 'mymod:id'), got: " + id);
        }
    }
}
