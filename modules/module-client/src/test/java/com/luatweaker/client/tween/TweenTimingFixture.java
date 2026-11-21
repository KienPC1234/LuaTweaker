package com.luatweaker.client.tween;

import com.luatweaker.api.client.ITweenService;
import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.client.TweenServiceImpl;
import com.luatweaker.core.bind.LuaBinder;
import com.luatweaker.core.vm.LuaEngineFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * Shared fixture for the tween timing tests: a fresh Lua engine with TweenService bound and a
 * {@code target = { Value = 0 }} table.
 */
class TweenTimingFixture {

    protected ILuaEngine engine;

    @BeforeEach
    void setUp() {
        TweenServiceImpl.clearAll();
        engine = LuaEngineFactory.create(false);
        LuaBinder.bind(engine, "TweenService", new TweenServiceImpl(engine), ITweenService.class);
        engine.executeStringOrThrow("target = { Value = 0 }", "tween_setup");
    }

    @AfterEach
    void tearDown() {
        TweenServiceImpl.clearAll();
        engine.shutdown();
    }

    protected double value() {
        return engine.getGlobalEnvironment().rawget("target").asTable().rawget("Value").asDouble();
    }

    protected void createAndPlay(String info) {
        engine.executeStringOrThrow(
                "tween = TweenService:Create(target, " + info + ", { Value = 10.0 })\n"
                        + "assert(tween ~= nil, 'Create must return a handle')\n"
                        + "tween:Play()",
                "tween_create");
    }
}
