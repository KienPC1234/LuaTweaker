package com.luatweaker.client.tween;

import com.luatweaker.api.log.LogStage;
import com.luatweaker.api.log.LuaTweakerLog;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import java.util.HashMap;
import java.util.Map;

public class TweenImpl {

    public enum TweenState {
        PLAYING,
        PAUSED,
        CANCELLED,
        COMPLETED
    }

    private final ILuaTable targetInstance;
    private final ILuaEngine engine;
    private final Map<String, Double> targetProperties = new HashMap<>();
    private final Map<String, Double> initialProperties = new HashMap<>();
    
    private final double duration;
    private final EasingHelper.EasingStyle easingStyle;
    private final EasingHelper.EasingDirection easingDirection;
    
    private TweenState state = TweenState.PAUSED;
    private double timeElapsed = 0.0;
    private ILuaTable boundTable;

    public TweenImpl(ILuaEngine engine, ILuaTable instance, ILuaTable tweenInfo, ILuaTable properties) {
        this.engine = engine;
        this.targetInstance = instance;
        
        // Parse TweenInfo
        double tDuration = 1.0;
        EasingHelper.EasingStyle tStyle = EasingHelper.EasingStyle.LINEAR;
        EasingHelper.EasingDirection tDirection = EasingHelper.EasingDirection.OUT;
        
        if (tweenInfo != null) {
            ILuaValue durVal = tweenInfo.rawget("Duration");
            if (durVal != null && durVal.isNumber()) tDuration = durVal.asDouble();
            
            ILuaValue styleVal = tweenInfo.rawget("EasingStyle");
            if (styleVal != null && styleVal.isString()) {
                try {
                    tStyle = EasingHelper.EasingStyle.valueOf(styleVal.asString().toUpperCase());
                } catch (Exception ignored) {}
            }
            
            ILuaValue dirVal = tweenInfo.rawget("EasingDirection");
            if (dirVal != null && dirVal.isString()) {
                try {
                    tDirection = EasingHelper.EasingDirection.valueOf(dirVal.asString().toUpperCase());
                } catch (Exception ignored) {}
            }
        }
        
        this.duration = Math.max(0.001, tDuration);
        this.easingStyle = tStyle;
        this.easingDirection = tDirection;
        
        // Parse Properties
        if (properties != null) {
            properties.forEach((k, v) -> {
                if (k.isString() && v.isNumber()) {
                    targetProperties.put(k.asString(), v.asDouble());
                }
            });
        }
    }

    public void play() {
        if (state != TweenState.PLAYING) {
            if (state == TweenState.COMPLETED || state == TweenState.CANCELLED) {
                timeElapsed = 0.0;
            }
            // Capture initial properties
            initialProperties.clear();
            for (String key : targetProperties.keySet()) {
                ILuaValue currentVal = targetInstance.rawget(key);
                if (currentVal != null && currentVal.isNumber()) {
                    initialProperties.put(key, currentVal.asDouble());
                } else {
                    initialProperties.put(key, 0.0);
                }
            }
            state = TweenState.PLAYING;
            com.luatweaker.client.TweenServiceImpl.registerActiveTween(this);
        }
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

    // Called every frame
    public void tick(double deltaTime) {
        if (state != TweenState.PLAYING) return;
        
        timeElapsed += deltaTime;
        double progress = timeElapsed / duration;
        boolean finished = false;
        if (progress >= 1.0) {
            progress = 1.0;
            finished = true;
        }
        
        // Update properties
        for (Map.Entry<String, Double> entry : targetProperties.entrySet()) {
            String key = entry.getKey();
            double endVal = entry.getValue();
            double startVal = initialProperties.getOrDefault(key, 0.0);
            
            double currentVal = EasingHelper.interpolate(startVal, endVal, progress, easingStyle, easingDirection);
            targetInstance.rawset(key, engine.wrapNumber(currentVal));
        }
        
        if (finished) {
            state = TweenState.COMPLETED;
            
            // Fire Completed event if any
            if (boundTable != null) {
                ILuaValue onCompleted = boundTable.rawget("Completed");
                if (onCompleted != null && onCompleted.isTable()) {
                    ILuaTable signal = onCompleted.asTable();
                    ILuaValue fireFn = signal.rawget("FireSync");
                    if (fireFn != null && fireFn.isFunction()) {
                        try {
                            engine.callFunction(fireFn, signal);
                        } catch(Exception e) {
                            LuaTweakerLog.get().warn(LogStage.SYSTEM, "Failed to fire Tween.Completed: " + e.getMessage());
                        }
                    }
                }
            }
        }
    }
}
