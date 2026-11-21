package com.luatweaker.entities;

import com.luatweaker.api.entity.IEntity;
import com.luatweaker.api.pal.IPlatformEntity;
import com.luatweaker.api.pal.Platform;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Verifies that every AI goal method forwards its arguments to the platform layer.
 */
class AIGoalDelegationTest {

    private static final String GOAL_KEY = "goal";
    private static final String GOAL_NAME = "FloatGoal";
    private static final String LIST_GOALS_METHOD = "listGoals";

    private ILuaEngine engine;
    private RecordingPlatform platform;

    @BeforeEach
    void setUp() {
        engine = LuaEngineFactory.create(false);
        platform = new RecordingPlatform();
        Platform.setEntity(platform.proxy());
    }

    @AfterEach
    void tearDown() {
        Platform.setEntity(null);
    }

    @Test
    void newGoalsForwardEveryArgumentToThePlatform() {
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

        Assertions.assertEquals(List.of(
                "addFloatGoal:0",
                "addWanderGoal:5:1.0",
                "addLookAtGoal:6:8.0:player",
                "addLookAroundGoal:7",
                "addPanicGoal:1:1.25",
                "addAvoidGoal:3:creeper:6.0:1.0:1.2",
                "addLeapGoal:4:0.4",
                "addBreakDoorGoal:2:true",
                "addTemptGoal:5:1.0:minecraft:wheat",
                "addMoveToTargetGoal:1:1.0:16.0"), platform.calls(),
                "every goal builder must forward its primitive arguments unchanged");
    }

    @Test
    void listGoalsReturnsThePlatformList() {
        AIGoalServiceImpl service = new AIGoalServiceImpl(engine);

        List<Map<String, Object>> goals = service.listGoals(mockEntity());

        Assertions.assertEquals(
                List.of(Map.of("side", GOAL_KEY, "priority", 1, GOAL_KEY, GOAL_NAME, "running", true)),
                goals, "listGoals must return the platform's goal descriptors unchanged");
    }

    private static IEntity mockEntity() {
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

    /** Records goal calls as "method:primitiveArgs" and fakes {@code listGoals}. */
    private static final class RecordingPlatform implements InvocationHandler {
        private final List<String> recordedCalls = new ArrayList<>();

        IPlatformEntity proxy() {
            return (IPlatformEntity) Proxy.newProxyInstance(
                    Thread.currentThread().getContextClassLoader(), new Class<?>[] {IPlatformEntity.class}, this);
        }

        List<String> calls() {
            return List.copyOf(recordedCalls);
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            recordedCalls.add(describe(method.getName(), args));
            if (LIST_GOALS_METHOD.equals(method.getName())) {
                return List.of(Map.of("side", GOAL_KEY, "priority", 1, GOAL_KEY, GOAL_NAME,
                        "running", true));
            }
            return null;
        }

        private static String describe(String methodName, Object... args) {
            StringBuilder text = new StringBuilder(methodName);
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof Number || arg instanceof CharSequence || arg instanceof Boolean) {
                        text.append(':').append(arg);
                    }
                }
            }
            return text.toString();
        }
    }
}
