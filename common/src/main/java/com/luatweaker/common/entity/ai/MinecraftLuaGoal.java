package com.luatweaker.common.entity.ai;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.luava.runtime.LuaException;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class MinecraftLuaGoal extends Goal {
    /** Lua arrays are 1-based; index 0 is always nil. */
    private static final int FIRST_LUA_INDEX = 1;
    private static final String FALLBACK_GOAL_NAME = "LuaGoal";

    private final ILuaTable goalTable;
    private final ILuaEngine engine;

    public MinecraftLuaGoal(@NotNull ILuaTable goalTable, @NotNull ILuaEngine engine) {
        super();
        this.goalTable = goalTable;
        this.engine = engine;
        applyFlags(goalTable.rawget("flags"));
    }

    @NotNull
    public ILuaTable getGoalTable() {
        return goalTable;
    }

    /**
     * Reads the optional {@code flags} list ({@code AIGoals:addGoal(ent, 1, { flags = {"MOVE", "LOOK"} })}).
     * Unknown flag names are reported and skipped instead of failing goal creation.
     */
    private void applyFlags(@Nullable ILuaValue flagsValue) {
        if (flagsValue == null || !flagsValue.isTable()) {
            return;
        }
        Set<Flag> flags = parseFlags(flagsValue.asTable());
        if (!flags.isEmpty()) {
            this.setFlags(EnumSet.copyOf(flags));
        }
    }

    private static @NotNull Set<Flag> parseFlags(@NotNull ILuaTable flagsTable) {
        Set<Flag> flags = EnumSet.noneOf(Flag.class);
        for (int index = FIRST_LUA_INDEX; index <= flagsTable.length(); index++) {
            ILuaValue value = flagsTable.rawget(index);
            if (value == null) {
                continue;
            }
            String flagName = value.asString().toUpperCase(Locale.ROOT);
            try {
                flags.add(Flag.valueOf(flagName));
            } catch (IllegalArgumentException e) {
                LuaTweakerLog.get().debug(LogStage.SYSTEM,
                        "Ignoring unknown AI goal flag '" + flagName + "': " + e.getMessage());
            }
        }
        return flags;
    }

    /**
     * Human-readable label for {@code AIGoals:listGoals} debugging. Reads the optional
     * {@code name} field of the goal table ({@code AIGoals:addGoal(ent, 1, { name = "ambush", ... })});
     * unnamed goals fall back to {@code "LuaGoal"} so every custom node is identifiable
     * instead of all showing the Java class name.
     */
    @NotNull
    public String getGoalName() {
        try {
            ILuaValue nameVal = goalTable.rawget("name");
            if (nameVal != null && !nameVal.isNil()) {
                String name = nameVal.asString();
                if (name != null && !name.isBlank()) {
                    return name.strip();
                }
            }
        } catch (LuaException | IllegalStateException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Lua goal name read failed: " + e.getMessage());
        }
        return FALLBACK_GOAL_NAME;
    }

    /**
     * Calls a goal callback, tolerating an engine replaced by {@code /lt reload}. The closures in
     * this goal belong to the old VM, so once that VM is shut down the goal must become inert
     * instead of throwing from {@code canUse}/{@code tick} on the server thread.
     */
    private ILuaValue call(ILuaValue function) {
        try {
            return engine.callFunction(function, goalTable);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    @Override
    public boolean canUse() {
        ILuaValue func = goalTable.rawget("canUse");
        if (func != null && func.isFunction()) {
            ILuaValue res = call(func);
            return res != null && res.asBoolean();
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        ILuaValue func = goalTable.rawget("canContinueToUse");
        if (func != null && func.isFunction()) {
            ILuaValue res = call(func);
            return res != null && res.asBoolean();
        }
        return super.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        ILuaValue func = goalTable.rawget("isInterruptable");
        if (func == null || func.isNil()) {
            func = goalTable.rawget("isInterruptible");
        }
        if (func != null && func.isFunction()) {
            ILuaValue res = call(func);
            return res != null && res.asBoolean();
        }
        return super.isInterruptable();
    }

    @Override
    public void start() {
        ILuaValue func = goalTable.rawget("start");
        if (func != null && func.isFunction()) {
            call(func);
        }
    }

    @Override
    public void stop() {
        ILuaValue func = goalTable.rawget("stop");
        if (func != null && func.isFunction()) {
            call(func);
        }
    }

    @Override
    public void tick() {
        ILuaValue func = goalTable.rawget("tick");
        if (func != null && func.isFunction()) {
            call(func);
        }
    }
}
