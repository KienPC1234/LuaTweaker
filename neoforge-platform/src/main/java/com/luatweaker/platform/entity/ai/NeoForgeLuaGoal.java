package com.luatweaker.platform.entity.ai;

import com.luatweaker.api.vm.*;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.Mob;
import org.jetbrains.annotations.NotNull;
import java.util.EnumSet;

public class NeoForgeLuaGoal extends Goal {
    private final Mob mob;
    private final ILuaTable goalTable;
    private final ILuaEngine engine;

    public NeoForgeLuaGoal(@NotNull Mob mob, @NotNull ILuaTable goalTable, @NotNull ILuaEngine engine) {
        this.mob = mob;
        this.goalTable = goalTable;
        this.engine = engine;
        
        ILuaValue flagsVal = goalTable.rawget("flags");
        if (flagsVal != null && flagsVal.isTable()) {
            EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
            ILuaTable flagsTable = flagsVal.asTable();
            for (int i = 1; i <= flagsTable.length(); i++) {
                String fName = flagsTable.rawget(i).asString().toUpperCase();
                try {
                    flags.add(Flag.valueOf(fName));
                } catch (IllegalArgumentException e) {
                    com.luatweaker.api.log.LuaTweakerLog.get().debug(
                            com.luatweaker.api.log.LogStage.SYSTEM,
                            "Ignoring unknown AI goal flag '" + fName + "': " + e.getMessage());
                }
            }
            this.setFlags(flags);
        }
    }

    @NotNull
    public ILuaTable getGoalTable() {
        return goalTable;
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
        } catch (Exception e) {
            com.luatweaker.api.log.LuaTweakerLog.get().warn(com.luatweaker.api.log.LogStage.SYSTEM,
                    "Lua goal name read failed: " + e.getMessage());
        }
        return "LuaGoal";
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
