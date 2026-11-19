package com.luatweaker.client.tween;

import com.luatweaker.api.vm.ILuaEngine;
import com.luatweaker.core.vm.LuaEngineFactory;
import com.luatweaker.client.TweenServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RepeatCount / Reverses / DelayTime used to be accepted by TweenInfo.new and then
 * silently ignored by the ticker. These tests pin the real behaviour.
 */
public class TweenTimingTest {

    private ILuaEngine engine;

    @BeforeEach
    public void setUp() {
        TweenServiceImpl.clearAll();
        engine = LuaEngineFactory.create(false);
        com.luatweaker.core.bind.LuaBinder.bind(engine, "TweenService",
                new TweenServiceImpl(engine), com.luatweaker.api.client.ITweenService.class);
        engine.executeStringOrThrow("target = { Value = 0 }", "tween_setup");
    }

    private double value() {
        return engine.getGlobalEnvironment().rawget("target").asTable().rawget("Value").asDouble();
    }

    private void createAndPlay(String info) {
        engine.executeStringOrThrow(
                "tween = TweenService:Create(target, " + info + ", { Value = 10.0 })\n" +
                "assert(tween ~= nil, 'Create must return a handle')\n" +
                "tween:Play()",
                "tween_create");
    }

    @Test
    public void delayTimePostponesTheInterpolation() {
        createAndPlay("{ Duration = 1.0, DelayTime = 0.5 }");

        TweenServiceImpl.tickAll(0.25);
        assertEquals(0.0, value(), 1e-9, "nothing must be written during the delay");

        TweenServiceImpl.tickAll(0.5); // 0.25s into the 1s tween
        assertEquals(2.5, value(), 1e-6);
    }

    @Test
    public void repeatCountRunsTheTweenAgain() {
        createAndPlay("{ Duration = 0.5, RepeatCount = 1 }");

        TweenServiceImpl.tickAll(0.25);
        assertEquals(5.0, value(), 1e-6);

        TweenServiceImpl.tickAll(0.5); // second cycle, halfway (0.75s total)
        assertEquals(5.0, value(), 1e-6);

        TweenServiceImpl.tickAll(0.5); // 1.25s >= 2 cycles of 0.5s
        assertEquals(10.0, value(), 1e-6, "the tween must finish on the end value");
    }

    @Test
    public void reversesPlaysTheSecondCycleBackwards() {
        createAndPlay("{ Duration = 0.5, RepeatCount = 1, Reverses = true }");

        TweenServiceImpl.tickAll(0.75); // cycle 1 (reversed) at 50%
        assertEquals(5.0, value(), 1e-6);

        TweenServiceImpl.tickAll(0.5); // finished: cycle 1 fully reversed
        assertEquals(0.0, value(), 1e-6, "a ping-pong tween must return to the start value");
    }

    @Test
    public void infiniteRepeatNeverCompletes() {
        createAndPlay("{ Duration = 0.5, RepeatCount = -1 }");

        TweenServiceImpl.tickAll(1.25); // 2.5 cycles
        assertEquals(5.0, value(), 1e-6, "an infinite tween keeps interpolating");

        TweenServiceImpl.tickAll(0.25); // exactly 3 cycles -> start of cycle 3
        assertEquals(0.0, value(), 1e-6);
    }
}
