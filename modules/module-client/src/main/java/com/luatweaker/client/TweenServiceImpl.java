package com.luatweaker.client;

import com.luatweaker.api.client.ITweenService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.api.vm.ILuaTable;
import com.luatweaker.api.vm.ILuaValue;
import com.luatweaker.client.tween.TweenImpl;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TweenServiceImpl implements ITweenService {
    
    private static final List<TweenImpl> activeTweens = new CopyOnWriteArrayList<>();
    private final ILuaEngine engine;

    public TweenServiceImpl(ILuaEngine engine) {
        this.engine = engine;
    }

    @Override
    public ILuaTable create(ILuaTable instance, ILuaTable tweenInfo, ILuaTable properties) {
        if (engine == null) return null;
        
        TweenImpl tween = new TweenImpl(engine, instance, tweenInfo, properties);
        
        ILuaTable tweenTable = engine.createTable();
        tweenTable.rawset("Play", args -> {
            tween.play();
            return null;
        });
        tweenTable.rawset("Pause", args -> {
            tween.pause();
            return null;
        });
        tweenTable.rawset("Cancel", args -> {
            tween.cancel();
            return null;
        });
        
        // Add a stub for Completed signal so user can listen to it
        ILuaValue signalClass = engine.getGlobalEnvironment().rawget("Signal");
        if (signalClass != null && signalClass.isTable()) {
            ILuaValue newSignalFn = signalClass.asTable().rawget("new");
            if (newSignalFn != null && newSignalFn.isFunction()) {
                try {
                    ILuaValue signalInstance = engine.callFunction(newSignalFn, signalClass);
                    tweenTable.rawset("Completed", signalInstance);
                } catch(Exception ignored) {}
            }
        }
        
        tween.setBoundTable(tweenTable);
        return tweenTable;
    }
    
    public static void registerActiveTween(TweenImpl tween) {
        if (!activeTweens.contains(tween)) {
            activeTweens.add(tween);
        }
    }
    
    public static void tickAll(double deltaTime) {
        Iterator<TweenImpl> iterator = activeTweens.iterator();
        while (iterator.hasNext()) {
            TweenImpl tween = iterator.next();
            if (tween.getState() == TweenImpl.TweenState.PLAYING) {
                tween.tick(deltaTime);
            }
            if (tween.getState() == TweenImpl.TweenState.COMPLETED || tween.getState() == TweenImpl.TweenState.CANCELLED) {
                activeTweens.remove(tween);
            }
        }
    }
}
