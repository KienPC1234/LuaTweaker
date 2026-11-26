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

    private record GoalCallbacks(
            @Nullable ILuaValue canUse,
            @Nullable ILuaValue canContinue,
            @Nullable ILuaValue interruptible,
            @Nullable ILuaValue start,
            @Nullable ILuaValue stop,
            @Nullable ILuaValue tick) {

        static GoalCallbacks resolve(@NotNull ILuaTable table) {
            return new GoalCallbacks(
                    findFunction(table, "canUse"),
                    findFunction(table, "canContinueToUse"),
                    findFunction(table, "isInterruptable", "isInterruptible"),
                    findFunction(table, "start"),
                    findFunction(table, "stop"),
                    findFunction(table, "tick"));
        }

        private static @Nullable ILuaValue findFunction(@NotNull ILuaTable table, String... keys) {
            for (String key : keys) {
                ILuaValue val = table.rawget(key);
                if (val != null && val.isFunction()) {
                    return val;
                }
            }
            return null;
        }
    }

    private final ILuaTable goalTable;
    private final ILuaEngine engine;
    private final GoalCallbacks callbacks;
    private boolean engineClosed;

    public MinecraftLuaGoal(@NotNull ILuaTable goalTable, @NotNull ILuaEngine engine) {
        super();
        this.goalTable = goalTable;
        this.engine = engine;
        this.callbacks = GoalCallbacks.resolve(goalTable);
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
    private @Nullable ILuaValue call(@Nullable ILuaValue function) {
        if (function == null || engineClosed) {
            return null;
        }
        try {
            return engine.callFunction(function, goalTable);
        } catch (IllegalStateException e) {
            engineClosed = true;
            return null;
        }
    }

    @Override
    public boolean canUse() {
        ILuaValue res = call(callbacks.canUse());
        return res != null && res.asBoolean();
    }

    @Override
    public boolean canContinueToUse() {
        if (callbacks.canContinue() != null) {
            ILuaValue res = call(callbacks.canContinue());
            return res != null && res.asBoolean();
        }
        return super.canContinueToUse();
    }

    @Override
    public boolean isInterruptable() {
        if (callbacks.interruptible() != null) {
            ILuaValue res = call(callbacks.interruptible());
            return res != null && res.asBoolean();
        }
        return super.isInterruptable();
    }

    @Override
    public void start() {
        call(callbacks.start());
    }

    @Override
    public void stop() {
        call(callbacks.stop());
    }

    @Override
    public void tick() {
        call(callbacks.tick());
    }
}
