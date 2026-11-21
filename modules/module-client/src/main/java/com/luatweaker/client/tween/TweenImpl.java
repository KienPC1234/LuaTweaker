package com.luatweaker.client.tween;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.client.TweenServiceImpl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TweenImpl {

    public enum TweenState {
        PLAYING,
        PAUSED,
        CANCELLED,
        COMPLETED
    }

    /** Roblox uses -1 for "repeat forever". */
    private static final int INFINITE_REPEAT = -1;
    private static final double ZERO = 0.0;

    private final ILuaTable targetInstance;
    private final ILuaEngine engine;
    private final Map<String, Double> targetProperties = new ConcurrentHashMap<>();
    private final Map<String, Double> initialProperties = new ConcurrentHashMap<>();

    private final double duration;
    private final double delayTime;
    private final int repeatCount;
    private final boolean reverses;
    private final EasingHelper.EasingStyle easingStyle;
    private final EasingHelper.EasingDirection easingDirection;

    private TweenState state = TweenState.PAUSED;
    private double timeElapsed;
    private boolean started;
    private ILuaTable boundTable;

    public TweenImpl(ILuaEngine engine, ILuaTable instance, ILuaTable tweenInfo, ILuaTable properties) {
        this.engine = engine;
        this.targetInstance = instance;

        TweenConfig config = TweenConfig.parse(tweenInfo);
        this.delayTime = config.delayTime();
        this.repeatCount = config.repeatCount();
        this.reverses = config.reverses();
        this.duration = Math.max(0.001, config.duration());
        this.easingStyle = config.style();
        this.easingDirection = config.direction();

        if (properties != null) {
            properties.forEach((k, v) -> {
                if (k.isString() && v.isNumber()) {
                    targetProperties.put(k.asString(), v.asDouble());
                }
            });
        }
    }

    public void play() {
        if (state == TweenState.PLAYING) {
            return;
        }
        // Resume keeps the captured start values; a fresh/repeated play restarts from
        // wherever the target currently is.
        if (!started || state == TweenState.COMPLETED || state == TweenState.CANCELLED) {
            captureInitialProperties();
            timeElapsed = 0.0;
            started = true;
        }
        state = TweenState.PLAYING;
        TweenServiceImpl.registerActiveTween(this);
    }

    public void pause() {
        if (state == TweenState.PLAYING) {
            state = TweenState.PAUSED;
        }
    }

    public void cancel() {
        state = TweenState.CANCELLED;
        timeElapsed = 0.0;
    }

    public TweenState getState() {
        return state;
    }

    public void setBoundTable(ILuaTable table) {
        this.boundTable = table;
    }

    /**
     * Called every frame. Writes into Lua tables, so it must run under the engine lock; the
     * render thread calls this instead of {@link #tick(double)} directly.
     */
    public void tickGuarded(double deltaTime) {
        engine.runExclusive(() -> tick(deltaTime));
    }

    // Called every frame
    public void tick(double deltaTime) {
        if (state != TweenState.PLAYING) {
            return;
        }
        timeElapsed += Math.max(0.0, deltaTime);
        double activeTime = timeElapsed - delayTime;
        if (activeTime < ZERO) {
            return;
        }

        boolean infinite = repeatCount == INFINITE_REPEAT;
        int totalCycles = infinite ? Integer.MAX_VALUE : repeatCount + 1;
        boolean finished = !infinite && activeTime >= duration * totalCycles;

        int cycle = finished
                ? totalCycles - 1
                : (int) Math.min(Integer.MAX_VALUE - 1.0, Math.floor(activeTime / duration));
        double progressInCycle = finished ? 1.0 : activeTime % duration / duration;
        boolean reversedCycle = reverses && (cycle % 2 == 1);
        double progress = reversedCycle ? 1.0 - progressInCycle : progressInCycle;

        applyProperties(progress);

        if (finished) {
            state = TweenState.COMPLETED;
            fireCompleted();
        }
    }

    private void captureInitialProperties() {
        initialProperties.clear();
        for (String key : targetProperties.keySet()) {
            ILuaValue currentVal = targetInstance.rawget(key);
            initialProperties.put(key, currentVal != null && currentVal.isNumber() ? currentVal.asDouble() : 0.0);
        }
    }

    private void applyProperties(double progress) {
        for (Map.Entry<String, Double> entry : targetProperties.entrySet()) {
            String key = entry.getKey();
            double startVal = initialProperties.getOrDefault(key, 0.0);
            double value = EasingHelper.interpolate(startVal, entry.getValue(), progress,
                    easingStyle, easingDirection);
            targetInstance.rawset(key, engine.wrapNumber(value));
        }
    }

    private void fireCompleted() {
        if (boundTable == null) {
            return;
        }
        ILuaValue onCompleted = boundTable.rawget("Completed");
        if (onCompleted == null || !onCompleted.isTable()) {
            return;
        }
        ILuaTable signal = onCompleted.asTable();
        ILuaValue fireFn = signal.rawget("FireSync");
        if (fireFn == null || !fireFn.isFunction()) {
            return;
        }
        try {
            engine.callFunction(fireFn, signal);
        } catch (IllegalStateException e) {
            LuaTweakerLog.get().warn(LogStage.SYSTEM,
                    "Failed to fire Tween.Completed: " + e.getMessage());
        }
    }
}
