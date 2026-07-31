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
                } catch (Exception e) {
                    // Ignore unknown flags
                }
            }
            this.setFlags(flags);
        }
    }

    @NotNull
    public ILuaTable getGoalTable() {
        return goalTable;
    }

    @Override
    public boolean canUse() {
        ILuaValue func = goalTable.rawget("canUse");
        if (func != null && func.isFunction()) {
            ILuaValue res = engine.callFunction(func, goalTable);
            return res != null && res.asBoolean();
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        ILuaValue func = goalTable.rawget("canContinueToUse");
        if (func != null && func.isFunction()) {
            ILuaValue res = engine.callFunction(func, goalTable);
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
            ILuaValue res = engine.callFunction(func, goalTable);
            return res != null && res.asBoolean();
        }
        return super.isInterruptable();
    }

    @Override
    public void start() {
        ILuaValue func = goalTable.rawget("start");
        if (func != null && func.isFunction()) {
            engine.callFunction(func, goalTable);
        }
    }

    @Override
    public void stop() {
        ILuaValue func = goalTable.rawget("stop");
        if (func != null && func.isFunction()) {
            engine.callFunction(func, goalTable);
        }
    }

    @Override
    public void tick() {
        ILuaValue func = goalTable.rawget("tick");
        if (func != null && func.isFunction()) {
            engine.callFunction(func, goalTable);
        }
    }
}
