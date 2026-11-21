package com.luatweaker.client.tween;

import com.luatweaker.client.TweenServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RepeatCount / Reverses / DelayTime used to be accepted by TweenInfo.new and then silently ignored
 * by the ticker. These tests pin the real behaviour.
 */
class TweenTimingTest extends TweenTimingFixture {

    private static final double HALF_DURATION = 0.25;

    @Test
    void delayTimeWritesNothingDuringTheDelay() {
        createAndPlay("{ Duration = 1.0, DelayTime = 0.5 }");
        TweenServiceImpl.tickAll(HALF_DURATION);
        assertEquals(0.0, value(), 1e-9,
                "nothing must be written while the tween is still inside its delay");
    }

    @Test
    void delayTimePostponesTheInterpolation() {
        createAndPlay("{ Duration = 1.0, DelayTime = 0.5 }");
        TweenServiceImpl.tickAll(HALF_DURATION);
        TweenServiceImpl.tickAll(0.5);
        assertEquals(2.5, value(), 1e-6,
                "0.25s into a 1s tween must be a quarter of the way from 0 to 10");
    }

    @Test
    void repeatCountStartsTheTweenAgain() {
        createAndPlay("{ Duration = 0.5, RepeatCount = 1 }");
        TweenServiceImpl.tickAll(HALF_DURATION);
        assertEquals(5.0, value(), 1e-6,
                "half of the first 0.5s cycle must interpolate to 5.0");
    }

    @Test
    void repeatCountReplaysTheSecondCycle() {
        createAndPlay("{ Duration = 0.5, RepeatCount = 1 }");
        TweenServiceImpl.tickAll(HALF_DURATION);
        TweenServiceImpl.tickAll(0.5);
        assertEquals(5.0, value(), 1e-6,
                "halfway through the repeated cycle (0.75s total) must be 5.0 again");
    }

    @Test
    void repeatCountFinishesOnTheEndValue() {
        createAndPlay("{ Duration = 0.5, RepeatCount = 1 }");
        TweenServiceImpl.tickAll(HALF_DURATION);
        TweenServiceImpl.tickAll(0.5);
        TweenServiceImpl.tickAll(0.5);
        assertEquals(10.0, value(), 1e-6, "the tween must finish on the end value");
    }

    @Test
    void reversesPlaysTheSecondCycleBackwards() {
        createAndPlay("{ Duration = 0.5, RepeatCount = 1, Reverses = true }");
        TweenServiceImpl.tickAll(0.75);
        assertEquals(5.0, value(), 1e-6,
                "the reversed cycle at 50% must interpolate back to 5.0");
    }

    @Test
    void reversesReturnsToTheStartValue() {
        createAndPlay("{ Duration = 0.5, RepeatCount = 1, Reverses = true }");
        TweenServiceImpl.tickAll(0.75);
        TweenServiceImpl.tickAll(0.5);
        assertEquals(0.0, value(), 1e-6,
                "a ping-pong tween must return to the start value");
    }

    @Test
    void infiniteRepeatKeepsInterpolating() {
        createAndPlay("{ Duration = 0.5, RepeatCount = -1 }");
        TweenServiceImpl.tickAll(1.25);
        assertEquals(5.0, value(), 1e-6,
                "an infinite tween must keep interpolating after two and a half cycles");
    }

    @Test
    void infiniteRepeatRestartsEachCycle() {
        createAndPlay("{ Duration = 0.5, RepeatCount = -1 }");
        TweenServiceImpl.tickAll(1.25);
        TweenServiceImpl.tickAll(HALF_DURATION);
        assertEquals(0.0, value(), 1e-6,
                "exactly three elapsed cycles must put an infinite tween back at the start value");
    }
}
