package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.pal.IPlatformEntity;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class AIGoalDelegationTest {

    static class RecordingPlatform implements IPlatformEntity {
        final List<String> calls = new ArrayList<>();

        @Override public void addCustomGoal(@NotNull IEntity e, int p, @NotNull ILuaTable t, @NotNull ILuaEngine en, boolean ts) { calls.add("custom:" + p + ":" + ts); }
        @Override public void removeCustomGoal(@NotNull IEntity e, @NotNull ILuaTable t) { calls.add("remove"); }
        @Override public void clearCustomGoals(@NotNull IEntity e) { calls.add("clear"); }
        @Override public void addMeleeAttackGoal(@NotNull IEntity e, int p, double s, boolean pause) { calls.add("melee:" + p + ":" + s + ":" + pause); }
        @Override public void addHurtByTargetGoal(@NotNull IEntity e, int p) { calls.add("hurtBy:" + p); }
        @Override public void addNearestAttackableTargetGoal(@NotNull IEntity e, int p, @NotNull String t) { calls.add("nearest:" + p + ":" + t); }
        @Override public void addFloatGoal(@NotNull IEntity e, int p) { calls.add("float:" + p); }
        @Override public void addWanderGoal(@NotNull IEntity e, int p, double s) { calls.add("wander:" + p + ":" + s); }
        @Override public void addLookAtGoal(@NotNull IEntity e, int p, double d, @NotNull String t) { calls.add("lookAt:" + p + ":" + d + ":" + t); }
        @Override public void addLookAroundGoal(@NotNull IEntity e, int p) { calls.add("lookAround:" + p); }
        @Override public void addPanicGoal(@NotNull IEntity e, int p, double s) { calls.add("panic:" + p + ":" + s); }
        @Override public void addAvoidGoal(@NotNull IEntity e, int p, @NotNull String t, double d, double w, double s) { calls.add("avoid:" + p + ":" + t + ":" + d); }
        @Override public void addLeapGoal(@NotNull IEntity e, int p, double s) { calls.add("leap:" + p + ":" + s); }
        @Override public void addBreakDoorGoal(@NotNull IEntity e, int p, boolean h) { calls.add("breakDoor:" + p + ":" + h); }
        @Override public void addTemptGoal(@NotNull IEntity e, int p, double s, @NotNull String item) { calls.add("tempt:" + p + ":" + s + ":" + item); }
        @Override public void addMoveToTargetGoal(@NotNull IEntity e, int p, double s, double m) { calls.add("moveTo:" + p + ":" + s + ":" + m); }
        @Override public java.util.@NotNull List<Map<String, Object>> listGoals(@NotNull IEntity e) {
            return List.of(Map.of("side", "goal", "priority", 1, "goal", "FloatGoal", "running", true));
        }
        @Override public @Nullable com.luatweaker.api.entity.IPlayer getPlayer(@NotNull String uuid) { return null; }
        @Override public java.util.List<com.luatweaker.api.entity.IPlayer> getAllPlayers() { return List.of(); }
        @Override public @Nullable Object spawnEntity(@NotNull String id, double x, double y, double z) { return null; }
    }

    static IEntity mockEntity() {
        return new IEntity() {
            @Override public String getType() { return "test:mob"; }
            @Override public String getName() { return "mob"; }
            @Override public float getHealth() { return 10.0f; }
            @Override public void setHealth(float health) { }
            @Override public float getMaxHealth() { return 10.0f; }
            @Override public boolean isAlive() { return true; }
            @Override public void remove() { }
            @Override public Object getRawEntity() { return null; }
        };
    }

    @AfterEach
    void resetPlatform() {
        Platform.setEntity(null);
    }

    private ILuaEngine engine;

    @BeforeEach
    void createEngine() {
        engine = LuaEngineFactory.create(false);
    }

    @Test
    void newGoals_DelegateToPlatformWithArguments() {
        RecordingPlatform platform = new RecordingPlatform();
        Platform.setEntity(platform);
        AIGoalServiceImpl service = new AIGoalServiceImpl(engine);
        IEntity entity = mockEntity();

        service.addFloatGoal(entity, 0);
        service.addWanderGoal(entity, 5, 1.0);
        service.addLookAtGoal(entity, 6, 8.0, "player");
        service.addLookAroundGoal(entity, 7);
        service.addPanicGoal(entity, 1, 1.25);
        service.addAvoidGoal(entity, 3, "creeper", 6.0, 1.0, 1.2);
        service.addLeapGoal(entity, 4, 0.4);
        service.addBreakDoorGoal(entity, 2, true);
        service.addTemptGoal(entity, 5, 1.0, "minecraft:wheat");
        service.addMoveToTargetGoal(entity, 1, 1.0, 16.0);

        assertEquals(List.of(
                "float:0",
                "wander:5:1.0",
                "lookAt:6:8.0:player",
                "lookAround:7",
                "panic:1:1.25",
                "avoid:3:creeper:6.0",
                "leap:4:0.4",
                "breakDoor:2:true",
                "tempt:5:1.0:minecraft:wheat",
                "moveTo:1:1.0:16.0"), platform.calls);
    }

    @Test
    void listGoals_DelegatesToPlatform() {
        RecordingPlatform platform = new RecordingPlatform();
        Platform.setEntity(platform);
        AIGoalServiceImpl service = new AIGoalServiceImpl(engine);

        List<Map<String, Object>> goals = service.listGoals(mockEntity());
        assertEquals(1, goals.size());
        assertEquals("FloatGoal", goals.get(0).get("goal"));
        assertEquals("goal", goals.get(0).get("side"));
    }
}
